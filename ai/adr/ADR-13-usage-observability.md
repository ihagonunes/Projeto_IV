# ADR-13 — Usage Observability

## Status

ACCEPTED

## Contexto

Uso e custo precisam de uma origem operacional única para evitar dupla contagem entre Chat e Agent.

## Decisão

AIRequestCompleted emitido pelo Gateway é a única fonte operacional de UsageRecord. Gateway calcula custo individual uma vez. UsageRecord é imutável, idempotente por RequestId e pode carregar AIToolId.

## Alternativas consideradas

Chat/Agent gravam UsageRecord; Observability recalcula custo.

## Consequências

Evita duplicidade e divergência financeira. Consumidores aceitam que Observability é efeito secundário da completion.

## Emenda (ADR-38)

- `UsageRecord` representa **consumo conhecido** (tokens presentes). Uso desconhecido (`UNKNOWN`) não
  gera `UsageRecord`.
- Custo desconhecido não vira zero: ele vive apenas na tentativa (`aigateway_request_attempts`,
  `cost_status = INCONCLUSIVE`).
- A entrega de `AIRequestCompleted` é durável: a tentativa registra `PENDING`, `DELIVERED` ou `DEAD`, e
  a reentrega é idempotente pelo `request_id`.
