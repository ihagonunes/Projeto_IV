# Agentes — implementação do catálogo, editor e pipeline

**Status:** plano de implementação; Jira **SPI-180**; ADR-44 aceito para o
módulo `agents`; ADR-45 em `PROPOSED` para execução.
**Data:** 21/11/2026.
**Escopo de produto:** pipelines determinísticos de agentes em desktop.
**Fora de escopo:** aplicação mobile, agentes autônomos sem DAG, auto-delegação
irrestrita, broker/fila distribuída, multiagentes como enxame e benchmarking
entre tenants.

## 0. Readequação após os commits f71ec3c e eeb4200 (17–18/11/2026)

Os commits `f71ec3c` (`feat(SPI-136): criar praça visual de agentes`) e
`eeb4200` (`agents park`) alteraram somente o frontend. Não criaram módulo,
tabela, endpoint, RBAC ou execução no backend. Eles ampliaram o protótipo que o
backend precisa suportar:

- catálogo hardcoded com **18 cartões** (17 agentes + o pseudo-nó `merge`);
- 20 PNGs de agente e quatro frames animados de fundo;
- busca, categoria, recolher/expandir a biblioteca;
- arrastar do catálogo, mover nós, ramos paralelos e consolidação;
- tarefa e resultado únicos no grafo;
- conexão por clique origem → destino, remoção por duplo clique/teclado;
- posições normalizadas (`x`,`y` em `[0,1]`);
- IDs locais `task`/`step-N`, sem `schemaVersion` e sem persistência;
- validação local da esteira (`validateGraph`) e consolidação automática
  (`consolidate`) antes da confirmação;
- callback local `onConfirm`, que apenas fecha o diálogo; não salva, publica nem
  executa;
- testes puros de `createGraph`/`addNode`/`connect`/`removeNode`,
  `validateGraph` e `consolidate`.

Impacto no plano:

1. O catálogo do backend deve expor **17 `AgentDefinition`**, não 18. `merge`
   continua sendo `kind` de nó, sem `agentKey`.
2. O frontend precisa deixar de usar o catálogo hardcoded e receber
   `{ catalog, graph, ... }`. O `merge` deve ser sintetizado pela UI como nó, não
   cadastrado como agente.
3. O grafo salvo deve incluir `schemaVersion`; o backend não deve persistir
   `nextId` nem confiar em `step-N` como identidade. A UI deve gerar IDs opacos
   (UUID) ou receber o mapeamento do servidor.
4. `validateGraph` já exige todos os agentes alcançáveis a partir da tarefa,
   pelo menos um agente, ramos consolidados por `merge`, resultado alcançável e
   nenhuma ponta solta. O backend deve aplicar a mesma semântica, além de ciclos,
   duplicatas, limites, `agentKey` ativo/visível e payload.
5. `consolidate` insere `merge` automaticamente para ramos abertos. O backend
   deve aceitar o grafo já normalizado, persistir os consolidadores gerados e
   revalidar a versão publicada; não pode depender do cliente para essa correção.
6. `onConfirm` é apenas local: fecha o diálogo e marca a esteira como confirmada
   na sessão. A implementação real deve trocar isso por save/publicação com
   resposta do servidor; não pode confirmar sucesso sem API.
7. Os commits não resolvem foco destruído no re-render, save/load, autosave,
   conflito de versão, publicação nem execução. A Fase 0/1 continua necessária.
8. A decisão de catálogo por tenant continua em aberto. Antes da migration, é
   preciso escolher como as 17 definições serão criadas para cada tenant:
   provisionamento no onboarding, comando administrativo de seed ou catálogo
   global com override. O ADR-44 atual não fecha essa estratégia.
9. A decisão de catálogo visível × executável também precisa ser fechada:
   recomenda-se expor as 17 definições (por exemplo, `DRAFT`/`ACTIVE`) e permitir
   executar somente as 3 aprovadas no MVP (ADR-45), com status explícito na API.
10. `PUT` de fluxo pode aceitar rascunho inválido com `validation_status=INVALID`;
   `publicar` e `executar` exigem `VALID`. Essa semântica precisa ser confirmada
   no packet/ADR-44.

### Numeração após o pull da main

O merge da `main` trouxe o `LOCAL-BUDGET-001` e ocupou a numeração:

- `ADR-43` = orçamento individual (já aceito);
- `V025` e `V026` = livro financeiro individual e snapshot departamental;
- os ADRs de agentes foram renumerados para `ADR-44` (módulo/persistência) e
  `ADR-45` (execução, ainda `PROPOSED`);
- o módulo `agents` reserva `V027` (schema), `V028` (RBAC) e `V029` (seed global);
  execução fica em `V030+`.

Não usar `V025`/`V026` para agentes nem reutilizar `ADR-43`.

### Ordem revisada

```text
Fase 0  Frontend: foco, IDs, schemaVersion, catálogo injetável, testes puros
Fase 1  Módulo agents + RBAC AGENT + V027/V028 + seed global V029
Fase 2  Persistência de fluxo/versão + save/load/publicação + validação
Fase 3  Frontend conectado à API + conflito/autosave
Fase 4  Execução somente após ADR-45 ACCEPTED (V030+ e purpose AGENT_FLOW)
```

## 1. Ponto de partida

Os PRs #28/#29 entregaram a praça visual no frontend:

- `agent-plaza.js` monta o grafo localmente;
- o catálogo de 18 cartões é hardcoded, com imagens e cenário animado;
- arrastar adiciona/move cartões;
- conectar é clique na origem e depois no destino;
- `validateGraph` e `consolidate` rodam no cliente;
- confirmar a esteira apenas fecha o diálogo e marca a sessão;
- o rascunho vive em memória;
- fechar/reabrir preserva; recarregar apaga;
- não há persistência, execução ou alteração no envio do chat;
- o backend não possui catálogo, fluxo, versão ou motor de execução de agentes.

O doc do protótipo (`docs/development/PRACA_DOS_AGENTES.md`) está correto ao
declarar essas limitações. Este documento descreve o que falta para o recurso
ser utilizável de verdade.

## 2. Definição de “utilizável”

Há dois marcos distintos:

### Marco A — Editor persistente

O usuário consegue:

1. abrir o catálogo real de agentes;
2. montar o fluxo por arrastar e soltar;
3. salvar;
4. fechar e reabrir sem perder;
5. validar o grafo;
6. publicar uma versão.

Ainda não executa agentes.

### Marco B — Pipeline executável

Além do Marco A:

1. escolher um fluxo publicado;
2. informar a entrada;
3. executar;
4. acompanhar etapas;
5. receber resultado, status, custo e recibos;
6. repetir com idempotência e limites.

O Marco B depende do ADR-45 e de um motor de execução. O Marco A já entrega valor
real e deve vir primeiro.

## 3. Arquitetura-alvo

### 3.1 Novo módulo `agents`

Package:

```text
br.com.qsoft.governance.agents
├── api/
├── domain/
├── application/
└── infrastructure/
```

Ownership:

- `AgentDefinition` — catálogo estável;
- `AgentFlow` — metadados do fluxo;
- `AgentFlowVersion` — grafo imutável versionado;
- `AgentFlowExecution` — execução de uma versão publicada;
- `AgentFlowStep` — estado/metadados de cada nó.

Não é dono de:

- `Conversation`/`Message` — chat;
- `KnowledgeDocument`/`AgentExecution` de políticas — `agentpolicies`;
- `Provider`/`Model`/`Attempt` — `aigateway`.

### 3.2 Dependências

Allowlist:

```text
agents -> sharedkernel, iam.api, aigateway.api, agentpolicies.api
```

- `agentpolicies.api` será usado apenas para o nó de políticas, quando essa porta
  existir.
- `chat -> agents.api` **não** entra no MVP; só com ADR-45 aceito e allowlist
  atualizada.
- Sem FK cross-module; IDs + portas.
- Sem broker, Redis, provider SDK ou dependência nova no MVP.

### 3.3 Decisões já registradas

- **SPI-180** é a chave Jira deste incremento.
- ADR-44: novo módulo `agents`; catálogo global por Flyway + override do tenant;
  persistência/versionamento; RBAC.
- ADR-45: execução; **proposto**, não implementar antes do aceite.
- Primeiro incremento: **Marco A** (editor persistente), sem motor de execução.
- 17 definições globais visíveis; baseline executável: `policies`, `writing`,
  `data`. O override do tenant pode alterar prompt, ferramentas, conhecimento,
  política e status.
- RBAC: colaborador lê/executa fluxos próprios; líder/gestor administram o
  departamento; executivo lê/executa o tenant; admin administra o tenant.
- Rascunho inválido pode ser salvo como `INVALID`; publicar/executar exige `VALID`.
- O frontend será integrado por este implementador, com revisão da responsável.

## 4. Modelo de dados

Migrations a partir de `V027` (V024–V026 já usadas). Prefixo `agents_`.

### 4.1 Catálogo global e overrides

```sql
CREATE TABLE agents_catalog (
    id              UUID PRIMARY KEY,
    agent_key       VARCHAR(80) NOT NULL,
    version         INTEGER NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    category        VARCHAR(60) NOT NULL,
    prompt_template TEXT NOT NULL,
    tools_json      JSONB NOT NULL DEFAULT '[]',
    knowledge_scope JSONB NOT NULL DEFAULT '{}',
    model_policy    JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL, -- DRAFT | ACTIVE | ARCHIVED
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_agents_catalog UNIQUE (agent_key, version),
    CONSTRAINT ck_agents_catalog_status
        CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'))
);
CREATE INDEX idx_agents_catalog_status ON agents_catalog (status);

CREATE TABLE agents_catalog_overrides (
    tenant_id       UUID NOT NULL,
    agent_key       VARCHAR(80) NOT NULL,
    version         INTEGER NOT NULL,
    prompt_template TEXT,
    tools_json      JSONB,
    knowledge_scope JSONB,
    model_policy    JSONB,
    status          VARCHAR(20), -- NULL = herda o global
    updated_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, agent_key, version),
    CONSTRAINT ck_agents_override_status
        CHECK (status IS NULL OR status IN ('DRAFT','ACTIVE','ARCHIVED'))
);
```

O `agent_key` é a identidade usada pelo grafo. Nome/descrição nunca são chave.
As 17 definições globais entram por Flyway (`V029`); o admin do tenant só cria
overrides. A resolução efetiva é `global` + `override` do tenant.

A praça tem 18 cartões, mas apenas 17 são `AgentDefinition`; `merge` é nó de
consolidação, não agente. O seed global (V029) cria as 17 definições: `policies`,
`writing` e `data` como `ACTIVE`/executáveis no baseline; as demais como
`DRAFT`/não executáveis. O override do tenant pode alterar prompt, ferramentas,
conhecimento, política de modelo e status; habilitar outros agentes é uma decisão
do admin do tenant e deve ser validada na publicação/execução.

### 4.2 Fluxos e versões

```sql
CREATE TABLE agents_flows (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    owner_user_id     UUID NOT NULL,
    department_id     UUID,
    name              VARCHAR(120) NOT NULL,
    status            VARCHAR(20) NOT NULL, -- DRAFT | PUBLISHED | ARCHIVED
    current_version_id UUID,
    row_version       BIGINT NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT ck_agents_flow_status
        CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);
CREATE INDEX idx_agents_flow_owner
    ON agents_flows (tenant_id, owner_user_id, updated_at DESC, id DESC);

CREATE TABLE agents_flow_versions (
    id                UUID PRIMARY KEY,
    flow_id           UUID NOT NULL REFERENCES agents_flows (id),
    version_number    INTEGER NOT NULL,
    schema_version    INTEGER NOT NULL,
    graph_json        JSONB NOT NULL,
    validation_status VARCHAR(20) NOT NULL, -- VALID | INVALID
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_agents_flow_version UNIQUE (flow_id, version_number)
);
```

- Versão publicada é imutável.
- A execução pina `flow_version_id`.
- `row_version`/`If-Match` evitam lost update.
- `deleted_at` é arquivamento lógico; `DELETED` não aparece em leitura.

### 4.3 Execução (futuro, após ADR-45)

Estas tabelas **não entram no Marco A**. Só devem ser criadas em `V030+` depois
do aceite do ADR-45.

```sql
CREATE TABLE agents_flow_executions (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    flow_version_id   UUID NOT NULL REFERENCES agents_flow_versions (id),
    owner_user_id     UUID NOT NULL,
    status            VARCHAR(20) NOT NULL,
    idempotency_key   VARCHAR(80) NOT NULL,
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    error_code        VARCHAR(60),
    CONSTRAINT uq_agents_execution_idem
        UNIQUE (tenant_id, owner_user_id, idempotency_key),
    CONSTRAINT ck_agents_execution_status
        CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED',
                          'TIMED_OUT','CANCELLED'))
);

CREATE TABLE agents_flow_steps (
    id              UUID PRIMARY KEY,
    execution_id    UUID NOT NULL REFERENCES agents_flow_executions (id),
    node_id         VARCHAR(80) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    request_id      UUID,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    error_code      VARCHAR(60),
    CONSTRAINT uq_agents_step UNIQUE (execution_id, node_id),
    CONSTRAINT ck_agents_step_status
        CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED',
                          'TIMED_OUT','SKIPPED'))
);
```

Não copiar tokens/custo para `agents_flow_steps` como fonte primária. Os recibos
vêm da tentativa (`request_id`), mantendo a fonte única do `aigateway`.

## 5. Schema do grafo

Exemplo:

```json
{
  "schemaVersion": 1,
  "nodes": [
    {
      "id": "6c2a...",
      "kind": "task",
      "name": "Tarefa do usuário",
      "x": 0.1,
      "y": 0.2
    },
    {
      "id": "3f1b...",
      "kind": "agent",
      "agentKey": "policies",
      "name": "Políticas internas",
      "x": 0.4,
      "y": 0.2
    },
    {
      "id": "9d0c...",
      "kind": "result",
      "name": "Resultado",
      "x": 0.7,
      "y": 0.2
    }
  ],
  "edges": [
    { "from": "6c2a...", "to": "3f1b..." },
    { "from": "3f1b...", "to": "9d0c..." }
  ]
}
```

Regras:

- `schemaVersion` obrigatório.
- IDs opacos e únicos; não usar `step-1` gerado pelo cliente como identidade
  persistida.
- `kind`: `task`, `agent`, `merge`, `result`.
- `agentKey` apenas em `agent`.
- Nome é apresentação; o servidor pode sobrescrever com o catálogo.
- `x`,`y` normalizados em `[0,1]`; finitos.
- Sem HTML; labels são texto puro.
- Tamanho máximo do JSON: 64 KB (valor inicial proposto).

## 6. Validação server-side

O cliente pode antecipar, mas o servidor é autoritativo. Validar no save, na
publicação e na execução:

1. exatamente uma `task` e um `result`;
2. `task` sem entrada; `result` sem saída;
3. sem ciclos, autoloops ou arestas duplicadas;
4. todos os endpoints de aresta existem;
5. `result` alcançável a partir de `task`;
6. todos os nós alcançáveis (sem órfãos);
7. `merge` com ≥2 entradas e ≤1 saída;
8. `agentKey` existente, `ACTIVE` e visível ao ator/tenant;
9. `kind` conhecido;
10. coordenadas finitas em `[0,1]`;
11. limites de nós, arestas, profundidade e payload;
12. strings de nome/descrição com tamanho máximo;
13. nenhum campo desconhecido, se o schema for fechado.

Erros devem ter código estável e caminho do nó/aresta, por exemplo:

```json
{
  "code": "AGENT_FLOW_INVALID",
  "message": "O fluxo contém um ciclo.",
  "path": "/edges/3"
}
```

O texto em português fica na apresentação; o contrato usa códigos.

## 7. API

Rotas em português, seguindo o padrão atual.

| Método | Rota | Permissão | Uso |
|---|---|---|---|
| GET | `/api/v1/agentes/catalogo` | `AGENT:READ` | catálogo visível |
| GET | `/api/v1/agentes/fluxos` | `AGENT:READ` | listar fluxos |
| POST | `/api/v1/agentes/fluxos` | `AGENT:MANAGE` | criar rascunho |
| GET | `/api/v1/agentes/fluxos/{id}` | `AGENT:READ` | carregar |
| PUT | `/api/v1/agentes/fluxos/{id}` | `AGENT:MANAGE` | salvar/versionar |
| DELETE | `/api/v1/agentes/fluxos/{id}` | `AGENT:MANAGE` | arquivar |
| POST | `/api/v1/agentes/fluxos/{id}/validar` | `AGENT:MANAGE` | validar sem salvar |
| POST | `/api/v1/agentes/fluxos/{id}/publicar` | `AGENT:MANAGE` | publicar versão |
| POST | `/api/v1/agentes/fluxos/{id}/execucoes` | `AGENT:RUN` | executar (ADR-45) |
| GET | `/api/v1/agentes/execucoes/{id}` | `AGENT:READ` | status/recibos |

Regras:

- `GET /catalogo` retorna as 17 definições com `status`/`executable`; não retorna
  `merge`, `task` nem `result` como agentes.
- `tenant_id` do contexto; `owner_user_id` do JWT.
- Outro tenant → 404.
- Sem permissão/fora do escopo → 403.
- `PUT` com `If-Match`/`row_version`; conflito → 409/412.
- Paginação `page`/`size`, zero-based, máximo 100.
- `POST /execucoes` com `Idempotency-Key`.
- Nenhum `/ai/complete` público.

## 8. RBAC

Novo recurso `AGENT` e ações:

- `AGENT:READ` — catálogo/fluxos visíveis;
- `AGENT:MANAGE` — criar/editar/validar/publicar/arquivar;
- `AGENT:RUN` — executar fluxo visível.

Distribuição decidida em 21/11/2026:

| Papel | Permissões e escopo |
|---|---|
| `COLLABORATOR` | `AGENT:READ`, `AGENT:RUN` em fluxos próprios; sem `MANAGE` |
| `LEADER` | `AGENT:READ`, `AGENT:MANAGE`, `AGENT:RUN` no departamento |
| `MANAGER` | `AGENT:READ`, `AGENT:MANAGE`, `AGENT:RUN` no departamento |
| `EXECUTIVE` | `AGENT:READ`, `AGENT:RUN` no tenant; sem `MANAGE` |
| `TENANT_ADMIN` | `AGENT:READ`, `AGENT:MANAGE`, `AGENT:RUN` no tenant |

`AGENT:MANAGE` não implica `AGENT:READ`; ambas são explícitas. O catálogo global
é somente leitura via aplicação; o tenant pode criar override com `AGENT:MANAGE`.

Implementação:

1. adicionar `AGENT` em `sharedkernel/rbac/Resource.java`;
2. atualizar `SystemRole`;
3. criar migration de RBAC no padrão `V015`;
4. atualizar `docs/development/MATRIZ_PERMISSOES.md` e as contagens;
5. testar positivos e negativos (tenant A ≠ B, fora do escopo, sem permissão).

A matriz sugerida está em `MATRIZ_PERMISSOES.md` §5 e é **proposta**, não
implantada.

## 9. Execução (Marco B)

Depende do ADR-45 aceito.

### 9.1 Fluxo

1. carregar a versão publicada;
2. validar novamente o DAG;
3. ordenar topologicamente;
4. executar nós prontos, com paralelismo limitado;
5. cada `agent` chama `AICompletionService` com `purpose = AGENT_FLOW`;
6. `merge` concatena resultados em ordem estável (MVP);
7. `result` devolve o resultado final;
8. persistir status/etapas/request IDs;
9. buscar recibos em lote;
10. agregar custo conhecido, preservando `INCONCLUSIVE`.

### 9.2 Semânticas iniciais

- **Fail-fast:** primeira falha encerra a execução como `FAILED`.
- **Timeout:** por etapa e total.
- **Retry:** só para falhas transitórias seguras; `OUTCOME_UNKNOWN` não repete.
- **Idempotência:** `Idempotency-Key`; repetição devolve a mesma execução.
- **Paralelismo:** limite inicial 2; máximo de 20 nós/40 arestas.
- **Merge:** `CONCATENATE`; `SUMMARIZE` fica para decisão posterior.
- **Custo:** cada etapa é uma tentativa; recibos por `request_id`; sem duplicar
  `UsageRecord`.
- **Cancelamento:** abortar etapas pendentes e marcar `CANCELLED`.
- **Conteúdo:** prompt/resposta não entram em log, evento ou analytics.

### 9.3 Síncrono ou assíncrono

MVP: síncrono com limites duros. Se os pipelines crescerem:

- persistir `PENDING` e processar com executor interno limitado;
- polling em `GET /execucoes/{id}`;
- recuperar `RUNNING` órfão após restart;
- sem broker/Redis sem necessidade medida.

## 10. Integração com o chat

Não faz parte do MVP.

Quando aprovada:

- `agentFlowId` opcional em `SendMessageRequest`/`SendMessageCommand`;
- `chat -> agents.api` na allowlist;
- `ChatTurnResponse`/`MessageResponse` com múltiplos recibos ou recibo de
  execução com steps;
- teto SELF do chat preservado;
- envio sem `agentFlowId` mantém o comportamento atual.

## 11. Frontend

### 11.1 O que muda

- catálogo vem da API;
- `mount()` recebe `{ catalog, graph, schemaVersion, onChange, onSave, onConfirm }`;
- `mount()` devolve `{ root, getGraph, setGraph, validateGraph, consolidate, destroy }`;
- `onConfirm` chama save/publicação real, não apenas fecha o diálogo;
- salvar/carregar/publicar;
- autosave com debounce;
- status “salvo/não salvo”;
- conflito 409/412 com opção de recarregar/sobrescrever;
- erros de validação por nó/aresta;
- execução separada (quando ADR-45 aceito).

### 11.2 Drag-and-drop real

Hoje:

- arrastar cartão do catálogo → adiciona;
- arrastar cartão no canvas → move;
- conectar = clique Saída → clique Entrada.

Para “arrastar e soltar” de verdade:

- `pointerdown` na porta de saída;
- linha temporária seguindo o cursor;
- destaque de entradas válidas;
- `pointerup` na entrada cria a aresta;
- `Escape` cancela;
- feedback de erro sem perder o grafo.

Desktop é o alvo. Não há requisito mobile/touch; ainda assim, o fluxo por teclado
deve continuar funcional.

### 11.3 Corrigir o foco

O re-render atual recria os cards e destrói o elemento focado. Guardar
`{nodeId, port}`/`{edgeId}` e restaurar após o render. Sem isso, o fluxo de
teclado prometido não funciona.

### 11.4 Estados

- carregando catálogo;
- catálogo vazio;
- fluxo inválido;
- salvando;
- salvo;
- conflito;
- sem permissão;
- execução em andamento;
- execução concluída/falha/timeout/cancelada;
- custo desconhecido/parcial.

## 12. Observabilidade e segurança

- Nenhum prompt/resposta em log, evento ou módulo analítico.
- Metadados de execução: ator, tenant, versão, nó, status, request ID, latência,
  tokens/custo via recibo.
- `UsageRecord` continua fonte de custo; não copiar para `agents_flow_steps`.
- Catálogo não expõe segredo; `credentialRef` é só nome.
- Ferramentas por agente com allowlist; conhecimento com escopo.
- LLM nunca decide autorização.
- Isolamento por tenant em toda leitura/escrita; SQL nativo com `tenant_id`.
- Admin não lê conversas; se chat for integrado, teto SELF prevalece.
- Limites de payload, nós, arestas, paralelismo e custo para evitar abuso.

## 13. Testes

### Unitários

- validação do DAG;
- topological sort;
- ciclo longo;
- aresta duplicada;
- nó órfão;
- merge com 1 entrada;
- resultado com 2 entradas;
- merge `CONCATENATE` em ordem estável;
- fail-fast;
- timeout;
- idempotência;
- serialização/desserialização do grafo.

### ArchUnit

- `agents` só acessa a allowlist;
- sem import interno cross-module;
- sem dependência de `chat` no MVP.

### Integração PostgreSQL

- catálogo por tenant;
- fluxo/versão imutável;
- concorrência otimista;
- Tenant A ≠ B;
- RBAC 403/404;
- execução linear;
- execução com um ramo e merge;
- recibos por etapa;
- timeout/falha;
- payload acima do limite;
- idempotência de execução.

### Frontend

- montar/salvar/carregar;
- fechar/reabrir;
- foco por teclado;
- drag de nó;
- drag de conexão;
- `validateGraph`: agente órfão, resultado ausente, ramo aberto;
- `consolidate`: dois ramos, ramos direto no resultado e etapa posterior;
- conflito;
- erros de validação;
- catálogo vindo da API;
- sem `innerHTML`.

### E2E

Desktop, quando o frontend estiver estável. Não é requisito mobile.

## 14. Fases de entrega

### Fase 0 — Endurecer o protótipo

- corrigir foco;
- guarda de `AtlasAgentPlaza`;
- validar `drop`/coordenadas;
- layout inicial sem depender de `ResizeObserver`;
- testes puros adicionais;
- incluir `agent-plaza.js` na guarda anti-`innerHTML`.

Saída: editor local confiável, ainda sem persistência.

### Fase 1 — Marco A: editor persistente

- módulo `agents` (ADR-44);
- V027 para catálogo/fluxos, V028 para RBAC `AGENT:*` e V029 para o seed global;
  execução em V030+;
- seed das 17 definições por tenant, com `merge` fora do catálogo;
- catálogo real;
- CRUD/versões/publicação;
- validação server-side;
- frontend save/load/publish/autosave.

Saída: usuário monta, salva, reabre e publica. Ainda não executa.

### Fase 2 — Marco B: execução limitada

- ADR-45 aceito;
- executor topológico;
- 3 agentes iniciais;
- um ramo + merge `CONCATENATE`;
- fail-fast/timeout/idempotência;
- endpoint de execução/polling;
- recibos por etapa;
- UI de execução/resultado.

Saída: pipeline executável com limites claros.

### Fase 3 — Integração e escala

- chat com `agentFlowId` e múltiplos recibos;
- dashboards de custo por execução/agente;
- async com recuperação de execuções;
- catálogo maior;
- E2E desktop.

## 15. Riscos e entraves

| Risco | Impacto | Mitigação |
|---|---|---|
| Ownership ambíguo | Alto | ADR-44 aceito; `agents` é dono; `agentpolicies` só conhecimento |
| Agentes sem prompt/tools reais | Alto | Começar com 3 agentes; não executar os 18 cartões |
| Semântica de merge/falha | Alto | ADR-45; concatenação + fail-fast no MVP |
| Recibo único no chat | Alto | Endpoint separado; chat depois |
| Pipeline longo | Alto | Limites de nós/tempo; async só depois |
| Custo por fan-out | Alto | Limites de nós/paralelismo/tokens/custo; idempotência |
| Prompt injection/tools | Alto | Allowlist por agente; escopo de conhecimento; LLM não autoriza |
| Tenant/RBAC | Alto | Permissões explícitas; testes A≠B; 404 cross-tenant |
| Foco/teclado no editor | Médio | Restaurar foco; testes de DOM |
| Drag de conexão | Médio | Pointer Events desktop; fallback clique |
| Retenção de conteúdo | Médio | Não logar; decidir retenção do resultado |
| Provider real | Médio | STUB para testes; S4-D1 para demo |
| Complexidade de testes | Médio | Unit + IT + provider simulado + E2E desktop |

## 16. Decisões abertas

1. **Merge:** `CONCATENATE` no MVP; `SUMMARIZE` exige nova decisão.
2. **Falha:** fail-fast no MVP; best-effort fica para depois.
3. **Async:** síncrono limitado no MVP; async com recuperação depois.
4. **Visibilidade:** fluxo SELF, DEPARTMENT ou TENANT? Proposta: rascunho SELF;
   publicado conforme escopo; definir na implementação.
5. **Catálogo:** 3 agentes iniciais; expansão gradual.
6. **Retenção:** resultado final armazenado? por quanto tempo? LGPD.
7. **Orçamento:** alerta não bloqueia; decidir se execução tem limite duro.
8. **Chat:** integrar só depois de múltiplos recibos e allowlist.

## 17. Checklist de implementação

- [ ] ADR-45 aceito antes do motor de execução.
- [ ] Módulo `agents` criado com camadas e ArchUnit.
- [ ] `AGENT` no vocabulário RBAC + `SystemRole` + migration.
- [ ] Migrations `V027`-`V029` com testes de constraint; execução em `V030+`.
- [ ] Catálogo real; sem hardcode no front.
- [ ] CRUD/versão/publicação.
- [ ] Validação server-side com códigos.
- [ ] Frontend save/load/publish/autosave/conflito.
- [ ] Drag de conexão desktop + foco.
- [ ] Executor topológico com limites.
- [ ] Recibos por etapa; custo agregado.
- [ ] Testes unit/ArchUnit/integração/frontend/E2E.
- [ ] Documentação canônica atualizada.

## 18. Referências

- `ai/project.md` — escopo de produto.
- `ai/architecture.md` — módulos, ownership e allowlist.
- `ai/contracts.md` — contratos HTTP e `agents.api`.
- `ai/adr/ADR-44-agents-module-and-flow-persistence.md`.
- `ai/adr/ADR-45-agent-flow-execution.md`.
- `docs/development/MATRIZ_PERMISSOES.md`.
- `docs/development/PRACA_DOS_AGENTES.md` — protótipo atual.
- `ai/testing.md` — estratégia de testes.
