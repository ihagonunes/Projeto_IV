# Módulo agents — pacotes de implementação

Jira: **SPI-180**.

Plano: `docs/development/AGENTES_PIPELINE_IMPLEMENTACAO.md`.

ADRs: `ADR-44` (módulo e persistência) e `ADR-45` (execução, **ACCEPTED** em
2026-11-22).

## Ordem e estado

| ID | Tarefa | Depende de | Estado |
|---|---|---|---|
| BE-01 | Módulo `agents`, migrations V027/V028 e RBAC | ADR-44 | ✅ Implementada (V027/V028) |
| BE-02 | Catálogo global, seed V029 e overrides | BE-01 | ✅ Implementada (V029) |
| BE-03 | Fluxos, versões e persistência do editor | BE-01, BE-02 | ✅ Implementada |
| BE-04 | Validação server-side e publicação | BE-03 | ✅ Implementada |
| BE-05 | Integração do frontend com a API | BE-02–BE-04 | ✅ Implementada (revisão da responsável pelo chat pendente) |
| BE-06 | Execução de pipelines | ADR-45 ACCEPTED + BE-04 | ✅ Implementada (V030) |
| BE-07 | Execução no frontend + E2E Playwright + seed | BE-05, BE-06 | ✅ Implementada; E2E executado em 22/11/2026 |

## Regras fixas

- Uma tarefa por sessão/commit.
- Sem dependência Maven/npm nova. O E2E usa o **Playwright MCP**; não adicionar
  `@playwright/test` sem justificativa em `ai/dependencies.md`.
- Sem broker, Redis, pgvector, SDK de provider ou serviço externo.
- `agents` importa apenas `sharedkernel`, `iam.api`, `aigateway.api` e
  `agentpolicies.api`.
- Sem FK/JPA cross-module.
- `tenant_id` sempre do contexto autenticado.
- Catálogo global é semeado por Flyway; o tenant só grava override.
- `merge`, `task` e `result` são tipos de nó; não são `AgentDefinition`.
- Execução só de versão `PUBLISHED` + `VALID` (ADR-45).
- Nenhum prompt/resposta em log, evento, analytics ou console.
- E2E usa banco descartável e provider **STUB**; nunca credencial real.

## Numeração

| Migration | Uso |
|---|---|
| V027 | Catálogo global, overrides, fluxos e versões |
| V028 | RBAC `AGENT:*` |
| V029 | Seed global das 17 definições |
| V030 | Execução (`agents_flow_executions`/`agents_flow_steps`) |
| V031 | Remove `result_text` (decisão 4-B: conteúdo só na resposta síncrona) |
| V032+ | Só com nova decisão (ex.: limite de concorrência) |

## Protocolo

Ler `AGENTS.md` §19, `ai/tasks/sprint-04/HANDOFF.md` e o pacote da tarefa.
Ao terminar, entregar o relatório do HANDOFF §7.

## Estado das pendências conhecidas

- Revisão humana do componente de chat (BE-05) ainda não registrada.
- Sem retry automático para falhas transitórias no BE-06.
- Sem limite de execuções concorrentes por tenant/usuário.
- Decisão 4-B (23/11/2026): o resultado final não é persistido; só a resposta
  síncrona do POST o devolve. Sem conteúdo armazenado, não há retenção a decidir.
- Integração `chat -> agents.api` fora do MVP (emenda ao ADR-45 + allowlist).
- E2E de navegador: executado no BE-07 (`docs/development/E2E_AGENTES_PLAYWRIGHT.md`).
