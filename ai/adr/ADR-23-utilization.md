# ADR-23 — Utilization

## Status

ACCEPTED

## Contexto

Utilização no score deve medir sustentabilidade financeira, não simplesmente volume de tokens/requests.

## Decisão

BudgetHealth: 100 até 80% do orçamento; queda linear 100→50 entre 80–100%; 50→0 entre 100–120%; 0 em 120% ou acima. Sem orçamento: 0 + INSUFFICIENT_DATA.

## Alternativas consideradas

Mais uso = score maior; bloqueio ao exceder orçamento; thresholds configuráveis no MVP.

## Consequências

Incentiva previsibilidade de custo sem bloquear usuários e mantém regra transparente.
