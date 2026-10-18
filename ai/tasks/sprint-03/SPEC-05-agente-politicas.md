# SPEC-05 — Agente de políticas (ADR-35, opção A)

> **Status:** ADR-35 ACCEPTED com a **opção A** (18/10/2026). Nada de Spring AI, pgvector ou embeddings.

Depende de: SPEC-01 (gate) e SPEC-03 (`AICompletionService` com *tools*).
Módulo: `agentpolicies`. Allowlist: `sharedkernel`, `iam.api`, `department.api`, `aigateway.api`.

## 1. Migration V019 (`V019__agentpolicies_knowledge.sql`)

```sql
CREATE TABLE agentpolicies_documents (
    id          UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    title       VARCHAR(200) NOT NULL,
    tenant_wide BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_ap_documents PRIMARY KEY (id),
    CONSTRAINT uq_ap_documents_tenant_title UNIQUE (tenant_id, title)
);
CREATE TABLE agentpolicies_document_scopes (
    document_id   UUID NOT NULL,
    tenant_id     UUID NOT NULL,
    department_id UUID NOT NULL,                 -- ID do department, sem FK cross-module
    CONSTRAINT pk_ap_document_scopes PRIMARY KEY (document_id, department_id),
    CONSTRAINT fk_ap_scopes_document FOREIGN KEY (document_id) REFERENCES agentpolicies_documents (id)
);
CREATE TABLE agentpolicies_document_versions (
    id             UUID        NOT NULL,
    tenant_id      UUID        NOT NULL,
    document_id    UUID        NOT NULL,
    version_number INTEGER     NOT NULL,
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ap_versions PRIMARY KEY (id),
    CONSTRAINT uq_ap_versions_number UNIQUE (document_id, version_number),
    CONSTRAINT fk_ap_versions_document FOREIGN KEY (document_id) REFERENCES agentpolicies_documents (id),
    CONSTRAINT ck_ap_versions_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','ACTIVE','SUPERSEDED'))
);
CREATE UNIQUE INDEX uq_ap_versions_one_active ON agentpolicies_document_versions (document_id) WHERE status = 'ACTIVE';
CREATE TABLE agentpolicies_chunks (
    id            UUID    NOT NULL,
    tenant_id     UUID    NOT NULL,
    version_id    UUID    NOT NULL,
    ordinal       INTEGER NOT NULL,
    text          TEXT    NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('portuguese', text)) STORED,
    CONSTRAINT pk_ap_chunks PRIMARY KEY (id),
    CONSTRAINT fk_ap_chunks_version FOREIGN KEY (version_id) REFERENCES agentpolicies_document_versions (id)
);
CREATE INDEX idx_ap_chunks_search ON agentpolicies_chunks USING GIN (search_vector);
CREATE INDEX idx_ap_chunks_tenant ON agentpolicies_chunks (tenant_id);
CREATE TABLE agentpolicies_executions (
    id            UUID        NOT NULL,
    tenant_id     UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    request_id    UUID,
    status        VARCHAR(20) NOT NULL,           -- RUNNING | COMPLETED | FAILED (ADR-25)
    document_ids  UUID[]      NOT NULL DEFAULT '{}',
    tools_called  VARCHAR(60)[] NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ,
    CONSTRAINT pk_ap_executions PRIMARY KEY (id),
    CONSTRAINT ck_ap_exec_status CHECK (status IN ('RUNNING','COMPLETED','FAILED'))
);
CREATE INDEX idx_ap_exec_tenant_created ON agentpolicies_executions (tenant_id, created_at DESC);
```

Sem extensão nenhuma. `to_tsvector('portuguese', …)` é nativo.

## 2. Recuperação segura (`KnowledgeAccessService`, ADR-14/15)

Query nativa, com o filtro **antes** do ranking e `tenant_id` explícito:

```sql
SELECT c.id, c.text, d.id AS document_id, d.title, v.version_number,
       ts_rank(c.search_vector, q) AS rank
  FROM agentpolicies_chunks c
  JOIN agentpolicies_document_versions v ON v.id = c.version_id AND v.status = 'ACTIVE'
  JOIN agentpolicies_documents d ON d.id = v.document_id
 CROSS JOIN websearch_to_tsquery('portuguese', :consulta) q
 WHERE c.tenant_id = :tenantId AND v.tenant_id = :tenantId AND d.tenant_id = :tenantId
   AND (d.tenant_wide
        OR EXISTS (SELECT 1 FROM agentpolicies_document_scopes s
                    WHERE s.document_id = d.id AND s.tenant_id = :tenantId
                      AND s.department_id = :userDepartmentId))
   AND c.search_vector @@ q
 ORDER BY rank DESC
 LIMIT 5
```

- `:userDepartmentId` é a lotação atual do usuário, via `IdentityQuery`, nunca do cliente.
- Usuário sem lotação → só documentos `tenant_wide`.
- Nenhuma correspondência → o agente responde que não encontrou política aplicável e **não** chama o
  LLM para inventar (`status COMPLETED`, sem `request_id`).

## 3. Orquestração (`AskPolicyAgent`)

1. Gate `KNOWLEDGE_DOCUMENT:READ` (BT). A recuperação já é o filtro fino.
2. Registra a execução `RUNNING`.
3. Recupera os trechos com a pergunta como consulta.
4. Chama `AICompletionService` com `purpose=POLICY_AGENT`, levando:
   - o prompt de sistema fixo (responder só com base nos trechos e citar `[n]`);
   - os trechos numerados;
   - a pergunta;
   - as *tools*.
5. Se vier `toolCalls`: executa só ferramentas da allowlist, sempre com o contexto do usuário, e
   devolve o resultado como mensagem `TOOL`. No máximo 2 rodadas. Nome desconhecido → ignora e
   registra.
   - `buscar_politicas({consulta})` → mesma recuperação da §2;
   - `modelo_permitido({})` → modelo resolvido para o usuário (via `aigateway.api`).
6. Registra `COMPLETED` (ou `FAILED`) com `request_id`, `document_ids` e `tools_called`. Nunca
   grava o texto da pergunta ou da resposta.

## 4. Endpoint

`POST /api/v1/agente-politicas/perguntas` — `KNOWLEDGE_DOCUMENT:READ`, `BY_TARGET`.

Request:

```json
{ "pergunta":"Posso usar o ChatGPT com dados de cliente?", "requestedModelId":null }
```

Resposta:

```json
{ "resposta":"…[1]…", "fontes":[{"documentId":"…","titulo":"Política de Uso de IA","versao":1,"trecho":"…"}],
  "ferramentasUsadas":["buscar_politicas"], "recibo":{ /* igual ao do chat */ } }
```

- Pergunta: 1 a 2000 caracteres.
- Erros da completion: como na SPEC-03.

## 5. Seeder de demonstração (SPI-129)

- `AgentPoliciesDemoSeeder` (`@ConditionalOnProperty app.bootstrap.demo-data=true`).
- Roda depois do seeder do IAM e é idempotente por título.
- Cria de 4 a 5 documentos **originais**, em pt-BR, escritos pelo implementador (nada copiado de
  terceiros):
  - "Política de Uso de IA Generativa" (`tenant_wide`);
  - "Dados Pessoais e LGPD no uso de IA" (`tenant_wide`);
  - "Diretrizes de IA para Marketing" (escopo: depto Marketing do seed);
  - "Diretrizes de IA para Engenharia" (escopo: depto Engenharia do seed).
- Cada documento tem a versão 1 `ACTIVE`, dividida em trechos por parágrafo (até cerca de 800
  caracteres).
- Se os departamentos não existirem no seed, o seeder os cria via `department.api` ou usa
  `tenant_wide`. Registrar a escolha.

## 6. Testes (SPI-135)

- **IT de isolamento de tenant:** documento do tenant B nunca é retornado para o usuário de A, mesmo
  com a mesma consulta.
- **IT de isolamento departamental:** usuário lotado em A não recebe trecho de documento com escopo
  só em B. Com o `StubProviderClient`, verificar que a `ProviderCall` capturada **não contém** o
  texto de B.
- **IT sem correspondência:** resposta "não encontrado"; nenhuma tentativa criada.
- **IT de ferramentas (`stub-tool`):** a ferramenta é executada com o contexto do usuário, o
  resultado volta ao LLM, `tools_called` é registrado e a ferramenta desconhecida é ignorada.
- **Unit:** divisão em trechos e montagem do prompt (os trechos numerados aparecem na ordem do
  ranking).
- **Schema:** `agentpolicies_executions` sem coluna de texto livre de pergunta ou resposta.
