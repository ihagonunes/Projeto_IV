# TASK-AGENTS-BE-02 — Catálogo global, seed V029 e overrides

## Metadados

- **Jira:** SPI-180.
- **Depende de:** BE-01.
- **Módulo:** `agents`.
- **Migration:** `V029`.
- **Não depende de:** ADR-45.

## Objetivo

Disponibilizar o catálogo real das 17 definições globais, com override por
tenant, e expor leitura/administração pela API.

## Fora de escopo

- persistência de fluxos;
- validação/publicação de fluxos;
- execução;
- frontend.

## Critérios de aceitação

- [ ] `V029` semeia idempotentemente as **17** definições globais:
  - [ ] `policies`, `writing`, `data` com status `ACTIVE`;
  - [ ] as demais com status `DRAFT`;
  - [ ] `agent_key` estável, nome, descrição e categoria;
  - [ ] `prompt_template`, `tools_json`, `knowledge_scope` e `model_policy`
    preenchidos com a proposta inicial revisável;
  - [ ] `merge`, `task` e `result` não entram no catálogo.
- [ ] O seed não duplica ao rodar em banco já migrado.
- [ ] `GET /api/v1/agentes/catalogo`:
  - [ ] `AGENT:READ`, escopo do ator;
  - [ ] resolve global + override do tenant;
  - [ ] retorna status e indicador de executabilidade;
  - [ ] não aceita `tenant_id` do cliente;
  - [ ] não expõe segredo nem prompt completo na listagem (quando aplicável).
- [ ] `PUT /api/v1/agentes/catalogo/{agentKey}/override`:
  - [ ] `AGENT:MANAGE` no tenant;
  - [ ] cria/atualiza override de prompt, ferramentas, conhecimento, política e
    status;
  - [ ] valida `agent_key` e versão existentes;
  - [ ] conflito de versão → 409/412;
  - [ ] outro tenant → 404.
- [ ] `DELETE /api/v1/agentes/catalogo/{agentKey}/override` remove o override e
  volta ao global, com `AGENT:MANAGE`.
- [ ] Nenhum endpoint permite editar o catálogo global em runtime.
- [ ] Testes:
  - [ ] seed 17 e idempotência;
  - [ ] resolução global + override;
  - [ ] override de Tenant A não aparece no Tenant B;
  - [ ] 403 sem `AGENT:MANAGE`;
  - [ ] 404 cross-tenant;
  - [ ] status/executabilidade.
- [ ] `ai/contracts.md` e `docs/development/MATRIZ_PERMISSOES.md` atualizados.

## Arquivos prováveis

- `src/main/resources/db/migration/V029__agents_catalog_seed.sql`
- `src/main/java/br/com/qsoft/governance/agents/application/**`
- `src/main/java/br/com/qsoft/governance/agents/infrastructure/**`
- `src/main/java/br/com/qsoft/governance/agents/api/**`
- `src/test/java/br/com/qsoft/governance/agents/**`
- `ai/contracts.md`

## Checks

- `./scripts/check`
- `./scripts/check-integration`
- `node --test src/test/frontend/*.test.js` — não aplicável se o front não for tocado.

## Relatório

Usar `ai/tasks/sprint-04/HANDOFF.md` §7.
