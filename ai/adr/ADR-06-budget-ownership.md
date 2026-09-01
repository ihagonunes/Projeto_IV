# ADR-06 — Budget Ownership

## Status

ACCEPTED

## Contexto

Orçamento é meta organizacional; custo realizado é fato de observabilidade.

## Decisão

Department é fonte de verdade de BudgetLimit mensal. Observability é dono de ActualCost, BudgetStatus e Alert e consulta o orçamento por port.

## Alternativas consideradas

Budget dentro de Observability; cópia local de orçamento nos dois módulos.

## Consequências

Separa meta de realizado e evita duas fontes de verdade. Cálculo analítico depende de integração síncrona clara.
