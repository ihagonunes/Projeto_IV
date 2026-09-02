# ADR-20 — Governance Score

## Status

ACCEPTED

## Contexto

A organização precisa de um indicador executivo explicável e não dependente de LLM.

## Decisão

FinalScore = Governance 40% + Adoption 20% + Training 20% + Utilization 20%. O módulo é read-oriented e consome GovernanceMetricsQueryPort/ports analíticos sem acessar repositories externos.

## Alternativas consideradas

Score gerado por IA; média simples de departments; score individual de colaborador.

## Consequências

Cálculo é auditável e determinístico, limitado a TENANT/DEPARTMENT no MVP.
