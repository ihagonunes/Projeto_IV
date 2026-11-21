# TASK-AGENTS-BE-06 — Execução determinística de pipelines

## Metadados

- **Jira:** SPI-180.
- **Depende de:** ADR-45 `ACCEPTED` + BE-04.
- **Módulo:** `agents` + integração com `aigateway.api`.
- **Migration:** `V030+`.
- **Estado:** **BLOQUEADA** até o aceite do ADR-45. Não iniciar.

## Objetivo

Implementar o motor topológico do Marco B: executar uma versão publicada,
persistir etapas, chamar `AICompletionService` por agente e devolver resultado,
status e recibos.

## Fora de escopo

- integração direta com o chat no MVP;
- broker/fila/Redis;
- merge com LLM (`SUMMARIZE`);
- agentes autônomos sem DAG;
- execução assíncrona sem decisão explícita.

## Critérios de aceitação

- [ ] ADR-45 `ACCEPTED`; sem isso, não alterar código de execução.
- [ ] `POST /api/v1/agentes/fluxos/{id}/execucoes` com `Idempotency-Key`.
- [ ] `GET /api/v1/agentes/execucoes/{id}` com status, etapas e recibos.
- [ ] Executor topológico com fail-fast e limite de paralelismo.
- [ ] Cada nó `AGENT` chama `AICompletionService` com `purpose=AGENT_FLOW`.
- [ ] Modelo resolvido pela política do departamento; cliente não escolhe modelo
  arbitrário.
- [ ] `merge` `CONCATENATE` em ordem estável; resultado final é o nó `result`.
- [ ] Timeout por etapa/total e cancelamento propagado.
- [ ] Retry apenas para falhas transitórias seguras; `OUTCOME_UNKNOWN` não repete.
- [ ] Idempotência devolve a mesma execução sem duplicar tentativas/custo.
- [ ] Recibos por `request_id` via `aigateway.api`; custo agregado preserva
  `INCONCLUSIVE`.
- [ ] Tabelas `agents_flow_executions`/`agents_flow_steps` em V030+.
- [ ] Testes de concorrência, timeout, falha parcial, idempotência, tenant A ≠ B
  e custo parcial.
- [ ] Nenhum prompt/resposta em log, evento ou analytics.

## Checks

- `./scripts/check`
- `./scripts/check-integration`
- `node --test src/test/frontend/*.test.js` — quando houver UI de execução.

## Relatório

Usar `ai/tasks/sprint-04/HANDOFF.md` §7.
