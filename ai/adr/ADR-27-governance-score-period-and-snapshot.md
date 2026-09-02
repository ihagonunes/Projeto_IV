# ADR-27 — Governance Score Period and Snapshot

## Status

ACCEPTED

## Contexto

Score precisa ser reproduzível no tempo mesmo quando fórmulas/dados evoluírem.

## Decisão

ScorePeriod=YearMonth. Governance/Utilization usam mês; Adoption usa 30 dias encerrados no fim do período; Training é as-of period-end. Persistir GovernanceScoreSnapshot mensal com CalculationVersion e torná-lo imutável após fechamento.

## Alternativas consideradas

Recalcular histórico sempre; período rolling indefinido; snapshot sem versão.

## Consequências

Histórico torna-se auditável e comparável; correções retroativas exigem procedimento/versionamento explícito.
