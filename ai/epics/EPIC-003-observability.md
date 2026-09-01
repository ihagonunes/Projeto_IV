# EPIC-003 — Observability e FinOps
## Objetivo
Registrar uso/custo e comparar realizado com orçamento, sem armazenar conteúdo.
## Módulos
`observability`, `department`
## Critérios arquiteturais
- UsageRecord originado por AIRequestCompleted;
- custo não recalculado;
- thresholds 80%/100%;
- histórico separado por adapters.
