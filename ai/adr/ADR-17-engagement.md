# ADR-17 — Engagement

## Status

ACCEPTED

## Contexto

Licenças e treinamentos são fatos transacionais; adoção e créditos são métricas derivadas.

## Decisão

Engagement é dono de AILicense/TrainingRecord e deriva métricas. EligibleUsers vem apenas de IAM Query Port; existe no máximo uma licença ACTIVE por tenant+user+AITool. Histórico usa HistoricalEngagementAdapter.

## Alternativas consideradas

Projeção local de usuários; múltiplas licenças ativas iguais; transformar histórico em entidades operacionais.

## Consequências

Evita drift de identidade e mantém histórico separado, ao custo de depender de queries de outros módulos para cálculo.
