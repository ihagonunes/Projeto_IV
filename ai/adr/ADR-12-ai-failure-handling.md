# ADR-12 — AI Failure Handling

## Status

ACCEPTED

## Contexto

Falhas antes de existir completion não representam uso bem-sucedido e não devem poluir analytics de produto.

## Decisão

Falhas pré-resposta retornam exceção abstrata ao consumidor e logging técnico estruturado. AIRequestFailed não existe no MVP.

## Alternativas consideradas

Criar UsageRecord de falha; publicar evento de domínio para toda falha de SDK.

## Consequências

Separa observabilidade técnica de métricas de produto; troubleshooting depende de logging técnico adequado.
