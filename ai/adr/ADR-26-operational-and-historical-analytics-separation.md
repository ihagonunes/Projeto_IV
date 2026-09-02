# ADR-26 — Operational and Historical Analytics Separation

## Status

ACCEPTED

## Contexto

O dataset QSOFT possui semântica histórica diferente das entidades operacionais criadas pela aplicação.

## Decisão

Importar dados históricos em historical_* no PostgreSQL e acessá-los por HistoricalUsageAdapter/HistoricalEngagementAdapter/HistoricalGovernanceAdapter. Não converter histórico artificialmente em aggregates operacionais nem criar FKs para tabelas operacionais.

## Alternativas consideradas

Mesclar histórico e operação nas mesmas tabelas; migrar cada linha histórica para entidade de domínio.

## Consequências

Preserva significado e permite analytics híbrido, ao custo de adapters/read models adicionais.
