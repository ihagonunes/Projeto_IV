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
