# Módulo agents — pacotes de implementação

Jira: **SPI-180**.

Plano: `docs/development/AGENTES_PIPELINE_IMPLEMENTACAO.md`.

ADRs: `ADR-44` (módulo e persistência) e `ADR-45` (execução, **PROPOSED**).

## Ordem

| ID | Tarefa | Depende de | Estado |
|---|---|---|---|
| BE-01 | Módulo `agents`, migrations V027/V028 e RBAC | ADR-44 | Pronta para iniciar |
| BE-02 | Catálogo global, seed V029 e overrides | BE-01 | Bloqueada por BE-01 |
| BE-03 | Fluxos, versões e persistência do editor | BE-01, BE-02 | Bloqueada por BE-02 |
| BE-04 | Validação server-side e publicação | BE-03 | Bloqueada por BE-03 |
| BE-05 | Integração do frontend com a API | BE-02–BE-04 | Bloqueada por BE-04 |
| BE-06 | Execução de pipelines | ADR-45 ACCEPTED + BE-04 | **Bloqueada pelo ADR-45** |

## Regras fixas

- Uma tarefa por sessão/commit.
- Sem dependência Maven/npm nova.
- Sem broker, Redis, pgvector, SDK de provider ou serviço externo.
- `agents` importa apenas `sharedkernel`, `iam.api`, `aigateway.api` e
  `agentpolicies.api`.
- Sem FK/JPA cross-module.
- `tenant_id` sempre do contexto autenticado.
- Catálogo global é semeado por Flyway; o tenant só grava override.
- `merge`, `task` e `result` são tipos de nó; não são `AgentDefinition`.
- Execução não começa antes de ADR-45 `ACCEPTED`.

## Numeração

| Migration | Uso |
|---|---|
| V027 | Catálogo global, overrides, fluxos e versões |
| V028 | RBAC `AGENT:*` |
| V029 | Seed global das 17 definições |
| V030+ | Execução, somente após ADR-45 |

## Protocolo

Ler `AGENTS.md` §19, `ai/tasks/sprint-04/HANDOFF.md` e o pacote da tarefa.
Ao terminar, entregar o relatório do HANDOFF §7.
