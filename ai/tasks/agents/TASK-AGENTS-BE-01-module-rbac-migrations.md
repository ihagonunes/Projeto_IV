# TASK-AGENTS-BE-01 — Módulo agents, migrations V027/V028 e RBAC

## Metadados

- **Jira:** SPI-180.
- **Depende de:** ADR-44 ACCEPTED.
- **Módulo:** `agents`.
- **Migrations:** `V027`, `V028`.
- **Não depende de:** ADR-45.

## Objetivo

Criar a fundação backend do módulo `agents`: package, camadas, persistência de
catálogo/fluxos/versões e permissões `AGENT:*`.

## Fora de escopo

- endpoints HTTP de fluxo;
- seed das 17 definições;
- validação de grafo/publicação;
- frontend;
- execução de agentes;
- qualquer alteração em `chat`, `agentpolicies` ou `aigateway` além de consumir
  suas superfícies `api.*`.

## Critérios de aceitação

- [ ] Criar `br.com.qsoft.governance.agents` com `api/`, `domain/`, `application/`
  e `infrastructure/`.
- [ ] ArchUnit atualizado para:
  - [ ] `agents -> sharedkernel, iam.api, aigateway.api, agentpolicies.api`;
  - [ ] nenhum import de `domain`, `application`, `infrastructure`, repository,
    entity ou adapter de outro módulo;
  - [ ] nenhuma dependência de `chat` no MVP.
- [ ] `V027` cria somente:
  - [ ] `agents_catalog` (global, sem `tenant_id`);
  - [ ] `agents_catalog_overrides` (`tenant_id`, `agent_key`, `version`);
  - [ ] `agents_flows` (`tenant_id`, owner, department, status, row_version,
    current_version_id, soft delete);
  - [ ] `agents_flow_versions` (`flow_id` FK intramódulo, versão, schema,
    graph_json, validação, autor);
  - [ ] índices e constraints de unicidade/status.
- [ ] Não criar tabelas de execução em V027; elas pertencem ao BE-06/V030+.
- [ ] `V028` adiciona `AGENT` ao vocabulário e `AGENT:READ`, `AGENT:MANAGE`,
  `AGENT:RUN` ao `SystemRole`, respeitando:
  - [ ] `COLLABORATOR`: READ/RUN SELF, sem MANAGE;
  - [ ] `LEADER`: READ/MANAGE/RUN DEPARTMENT;
  - [ ] `MANAGER`: READ/MANAGE/RUN DEPARTMENT;
  - [ ] `EXECUTIVE`: READ/RUN TENANT, sem MANAGE;
  - [ ] `TENANT_ADMIN`: READ/MANAGE/RUN TENANT.
- [ ] `AGENT:MANAGE` não implica `AGENT:READ`.
- [ ] Migration de RBAC segue o padrão de `V015` e não altera permissões alheias.
- [ ] Testes:
  - [ ] unit de `SystemRole` para cada papel;
  - [ ] ArchUnit;
  - [ ] persistência de catálogo/override/flow/version;
  - [ ] constraints de status e unicidade;
  - [ ] Tenant A não lê nem altera fluxo/override do Tenant B.
- [ ] `docs/development/MATRIZ_PERMISSOES.md` e contagens atualizados.
- [ ] `ai/architecture.md`, `ai/contracts.md` e `ai/testing.md` conferidos.

## Arquivos prováveis

- `src/main/java/br/com/qsoft/governance/agents/**`
- `src/main/java/br/com/qsoft/governance/sharedkernel/rbac/Resource.java`
- `src/main/java/br/com/qsoft/governance/iam/domain/rbac/SystemRole.java`
- `src/main/resources/db/migration/V027__agents_catalog_flows.sql`
- `src/main/resources/db/migration/V028__agents_rbac.sql`
- `src/test/java/br/com/qsoft/governance/agents/**`
- `src/test/java/br/com/qsoft/governance/architecture/ArchitectureRulesTest.java`
- `docs/development/MATRIZ_PERMISSOES.md`

## Checks

- `./scripts/check`
- `./scripts/check-integration`
- `node --test src/test/frontend/*.test.js` — não deve ser necessário se o front
  não for tocado; registrar como não aplicável.

## Relatório

Usar `ai/tasks/sprint-04/HANDOFF.md` §7.
