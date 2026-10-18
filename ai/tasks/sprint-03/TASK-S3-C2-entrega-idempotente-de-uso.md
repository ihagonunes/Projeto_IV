# TASK-S3-C2 — Entrega idempotente de uso com reentrega agendada

## Metadados
- **Onda:** C · **Depende de:** C1
- **Jira:** tarefa criada na C1 (N05); SPI-78 pode ser fechada citando TR04/TR05
- **Módulos:** `aigateway` (publicador e job), `observability` (listener e `RecordUsage`)
- **Spec:** `SPEC-03` §3 (passos 5–7), §5 (TR04–TR07)

## Critérios de aceitação
- [ ] `UsageDeliveryPublisher` no `aigateway`:
  - [ ] sucesso → DELIVERED;
  - [ ] exceção → `delivery_attempts++` e backoff `min(30s × 2^n, 1h)`;
  - [ ] nunca propaga a exceção para o cliente.
- [ ] Job `@Scheduled` com `FOR UPDATE SKIP LOCKED`:
  - [ ] reentrega as tentativas vencidas;
  - [ ] passa para DEAD quando atingir `max-attempts`;
  - [ ] converte IN_FLIGHT expirado em OUTCOME_UNKNOWN/INCONCLUSIVE.
- [ ] Propriedades em `application.yml`, com os valores da SPEC-03: `app.ai.delivery.enabled`,
      `interval-ms`, `max-attempts`, `inflight-grace-ms`.
- [ ] `@EnableScheduling` numa config de `platform`.
- [ ] Nos ITs, o job é desligado e acionado manualmente no teste, para ser determinístico.
- [ ] `AIRequestCompletedListener` **sem** `try/catch`.
- [ ] `RecordUsage` grava com `INSERT … ON CONFLICT (request_id) DO NOTHING` (query nativa com
      `tenant_id`).
- [ ] ITs TR04, TR05, TR06 e TR07.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
