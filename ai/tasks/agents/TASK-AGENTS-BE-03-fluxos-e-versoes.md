# TASK-AGENTS-BE-03 — Fluxos, versões e persistência do editor

## Metadados

- **Jira:** SPI-180.
- **Depende de:** BE-01, BE-02.
- **Módulo:** `agents`.
- **Migration:** nenhuma nova; usa V027.
- **Não depende de:** ADR-45.

## Objetivo

Implementar o ciclo de vida persistente do fluxo: criar, listar, carregar, salvar
nova versão e arquivar, com concorrência otimista e isolamento por tenant/dono.

## Fora de escopo

- validação profunda do DAG e publicação (BE-04);
- execução;
- frontend;
- seed do catálogo.

## Critérios de aceitação

- [ ] `GET /api/v1/agentes/fluxos?status=&page=&size=`:
  - [ ] `AGENT:READ`;
  - [ ] paginação zero-based, máximo 100;
  - [ ] `DELETED` nunca aparece;
  - [ ] escopo SELF/DEPARTMENT/TENANT resolvido no backend.
- [ ] `POST /api/v1/agentes/fluxos`:
  - [ ] `AGENT:MANAGE`;
  - [ ] cria rascunho com owner/tenant do contexto;
  - [ ] aceita `schemaVersion`, `graph_json` e metadados;
  - [ ] não aceita owner/tenant do cliente;
  - [ ] `agent_key` referencia catálogo global/override.
- [ ] `GET /api/v1/agentes/fluxos/{id}`:
  - [ ] outro tenant/dono fora do escopo → 404;
  - [ ] retorna `row_version`, versão corrente, status e grafo.
- [ ] `PUT /api/v1/agentes/fluxos/{id}`:
  - [ ] `AGENT:MANAGE`;
  - [ ] exige `If-Match`/`row_version`;
  - [ ] conflito → 412;
  - [ ] cria versão imutável com `version_number` incrementado;
  - [ ] atualiza `current_version_id` e `row_version`;
  - [ ] grafo inválido pode ser persistido com `validation_status=INVALID`;
  - [ ] não aceita `step-1` como identidade persistida do servidor.
- [ ] `DELETE /api/v1/agentes/fluxos/{id}`:
  - [ ] soft delete (`deleted_at`/`DELETED`);
  - [ ] `AGENT:MANAGE`;
  - [ ] outro tenant/dono fora do escopo → 404.
- [ ] Testes:
  - [ ] CRUD completo;
  - [ ] versão imutável;
  - [ ] `If-Match`/412;
  - [ ] soft delete;
  - [ ] paginação;
  - [ ] Tenant A ≠ B;
  - [ ] colaborador só vê os próprios fluxos.
- [ ] `ai/contracts.md` atualizado.

## Arquivos prováveis

- `src/main/java/br/com/qsoft/governance/agents/domain/**`
- `src/main/java/br/com/qsoft/governance/agents/application/**`
- `src/main/java/br/com/qsoft/governance/agents/infrastructure/persistence/**`
- `src/main/java/br/com/qsoft/governance/agents/infrastructure/web/**`
- `src/test/java/br/com/qsoft/governance/agents/**`
- `ai/contracts.md`

## Checks

- `./scripts/check`
- `./scripts/check-integration`
- `node --test src/test/frontend/*.test.js` — não aplicável se o front não for tocado.

## Relatório

Usar `ai/tasks/sprint-04/HANDOFF.md` §7.
