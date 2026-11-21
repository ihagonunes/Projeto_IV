# ADR-44 — Módulo `agents`: catálogo, fluxos e persistência de agentes

## Status

ACCEPTED (2026-11-18; emendado em 2026-11-21 para catálogo global com override
por tenant) — decisão de ownership do responsável. A implementação permanece
pendente; a matriz de permissões e as migrations descritas aqui são o alvo
vinculante, não código já existente.

## Contexto

- A Sprint 4/PRs #28 e #29 entregaram uma praça visual de agentes apenas no frontend
  (`src/main/resources/static/public/js/pages/agent-plaza.js`). O rascunho vive
  em memória, o catálogo é hardcoded e não há persistência, execução ou alteração
  no envio do chat (`docs/development/PRACA_DOS_AGENTES.md`).
- `ai/project.md` listava "multiagentes" como fora de escopo. O responsável
  decidiu incluir **pipelines determinísticos de agentes** no produto; agentes
  autônomos sem DAG continuam fora.
- `ai/architecture.md` atribuía `Agent` a `agentpolicies`, mas o código não tem
  entidade/tabela `Agent`: `agentpolicies/api/` está vazio e a V019 cria apenas
  documentos, versões, chunks e `AgentExecution`.
- `chat` é dono de `Conversation`/`Message` (ADR-39/42). `aigateway` é dono de
  Provider/Model/Attempt/Completion (ADR-09/11/38). Nenhum dos dois deve ser dono
  de catálogo ou grafo de agentes.
- A UI atual não pode evoluir para persistência sem uma fronteira explícita; o
  `mount()` esconde o grafo e o catálogo, e não há contrato de save/load.

## Decisão

1. **Novo módulo `agents`.** Ele é o dono de:
   - `AgentDefinition` — catálogo global com identidade estável (`agent_key`),
     nome, descrição, categoria, prompt/template versionado, allowlist de
     ferramentas, escopo de conhecimento e política de modelo;
   - `AgentDefinitionOverride` — override tenant-scoped de prompt, ferramentas,
     conhecimento, política de modelo e status;
   - `AgentFlow` — metadados do fluxo, dono, tenant, visibilidade, status e
     versão corrente;
   - `AgentFlowVersion` — snapshot imutável do grafo, com `schema_version`;
   - `AgentFlowExecution` e `AgentFlowStep` — ownership futuro, somente após o
     ADR-45; não entram no Marco A.

2. **Fronteiras.** O módulo pode depender de `sharedkernel`, `iam.api`,
   `aigateway.api` e `agentpolicies.api` (esta última para o nó de políticas,
   quando a porta existir). Não pode importar `domain`, `application`,
   `infrastructure`, repository, entity ou adapter desses módulos. Não há FK
   cross-module; uso de IDs + portas.

3. **`agentpolicies` permanece especializado.** Ele continua dono de
   `KnowledgeDocument/Version`, `AgentExecution` e `KnowledgeAccess`. O módulo
   `agents` não duplica documentos nem execuções de políticas; para um nó de
   políticas, consome uma porta pública em `agentpolicies.api` (a criar).

4. **Chat não é dono de agente.** `agents` não depende de `chat` no MVP. A
   integração `chat -> agents.api` (envio com `agentFlowId`) só pode ser
   adicionada com o ADR-45 aceito e a allowlist atualizada.

5. **Persistência PostgreSQL, Flyway, `ddl-auto=validate`.** Prefixo de tabelas
   `agents_` e migrations a partir de `V027` (V024–V026 já foram usadas):
   - `agents_catalog` — definições globais, sem `tenant_id`: `agent_key`,
     `version`, `status`, prompt, tools, knowledge scope, model policy,
     timestamps; `UNIQUE(agent_key, version)`;
   - `agents_catalog_overrides` — chave `(tenant_id, agent_key, version)`;
     sobrescreve prompt, tools, knowledge scope, model policy e status;
   - `agents_flows` (`tenant_id`, `owner_user_id`, `department_id`, `name`,
     `status`, `current_version_id`, `row_version`, timestamps, `deleted_at`);
   - `agents_flow_versions` (`flow_id` FK intramódulo, `version_number`,
     `schema_version`, `graph_json JSONB`, `validation_status`, autor/timestamps;
     `UNIQUE(flow_id, version_number)`);
   - as tabelas de execução (`agents_flow_executions`/`agents_flow_steps`) só
     entram depois do ADR-45, em `V030+`;
   - as definições globais são semeadas por Flyway (`V029`), não por um ator de
     plataforma em runtime. O admin do tenant só cria overrides.
   - `tenant_id` de fluxos/overrides sempre do contexto autenticado;
     `owner_user_id` do JWT; nunca do cliente. Outro tenant → 404.

6. **Grafo versionado.** O `graph_json` guarda `schemaVersion`, `nodes` e `edges`
   com `agent_key` estável e IDs opacos únicos. Não guardar nome de exibição como
   identidade. A versão publicada é imutável e pinada na execução.

7. **Validação server-side obrigatória.** O servidor revalida o DAG no save, na
   publicação e na execução: uma tarefa, um resultado, sem ciclos/autoloop/aresta
   duplicada, endpoints existentes, `result` alcançável, `merge` com ≥2 entradas,
   `agent_key` existente/ativo/visível, coordenadas finitas em `[0,1]`, limites de
   nós/arestas e tamanho do JSON. O cliente só antecipa o feedback.

8. **RBAC.** Novo recurso `AGENT` no vocabulário do `sharedkernel` e permissões
   `AGENT:READ`, `AGENT:MANAGE`, `AGENT:RUN`:
   - `READ` — catálogo e fluxos visíveis;
   - `MANAGE` — criar/editar/validar/publicar/arquivar fluxos e administrar
     catálogo conforme escopo;
   - `RUN` — executar fluxo visível.
   A matriz e o `SystemRole` serão atualizados em migration própria, no padrão da
   V015. Não reutilizar `AI_TOOL:MANAGE` (admin-only) nem `CHAT:CREATE` (SELF de
   conversa) para fluxos.

9. **Sem dependência nova, broker, fila ou provider SDK.** O MVP usa Java/Spring
   e PostgreSQL. Execução síncrona limitada é o ponto de partida; async exige
   ADR-45.

10. **Frontend desktop.** A plataforma não tem objetivo mobile. O editor deve
    funcionar em desktop; não há requisito de layout/touch mobile. Acessibilidade
    por teclado continua desejável e deve ser testada em desktop.

## Alternativas consideradas

- **Estender `agentpolicies`** — reduz um módulo, mas mistura conhecimento/RAG
  com catálogo e orquestração genérica; o nome e o domínio atual não representam
  os 18 agentes de negócio.
- **Colocar em `chat`** — chat é dono de conversa e mensagem; fluxo não é
  conversa. Criaria acoplamento e ampliaria a superfície de privacidade.
- **Colocar em `aigateway`** — gateway é dono de provider/model/attempt; catálogo
  de agentes não é catálogo de modelos.
- **Rascunho só em memória/localStorage** — não atende multiusuário, auditoria,
  versionamento ou tenant isolation.

## Consequências

- Novo package `br.com.qsoft.governance.agents` com `api/`, `domain/`,
  `application/` e `infrastructure/`, sem módulo Maven separado.
- Atualização de `ai/architecture.md` (árvore, ownership e allowlist), ArchUnit,
  `ai/contracts.md`, `MATRIZ_PERMISSOES.md` e `SystemRole`.
- Migrations `V027` (schema), `V028` (RBAC) e `V029` (seed global); execução
  somente em `V030+`, após ADR-45.
- O frontend deixa de usar catálogo hardcoded e ganha `save/load/publish`,
  validação e estados de erro.
- Execução de pipeline não faz parte deste ADR; depende do ADR-45.
- O escopo de multiagentes autônomos continua fora; o produto entrega DAG
  determinístico, não enxame autônomo.
