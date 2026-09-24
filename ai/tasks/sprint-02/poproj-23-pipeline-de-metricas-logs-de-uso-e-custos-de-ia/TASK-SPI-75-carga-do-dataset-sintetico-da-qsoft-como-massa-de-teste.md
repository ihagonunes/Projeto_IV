# TASK-SPI-75 — Carga do dataset sintético da QSOFT como massa de teste

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-75
- **História PO:** POPROJ-23
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Pipeline de Métricas, Logs de Uso e Custos de IA

## Fonte original do consolidado
Ingestão de dados sintéticos com FKs consistentes para testes de filtros e performance.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Carregar dataset sintético em historical_* sem FKs operacionais.

### Requisitos reconciliados
- [ ] Persistir em historical_*.
- [ ] Sem FK para tabelas operacionais.
- [ ] Acesso via HistoricalUsageAdapter/read model.
- [ ] Consistência interna sem simular aggregates operacionais.

### Motivo
ADR-26/29.

## Área proprietária
`observability + aigateway`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ADR-13`
- `ADR-26`
- `ADR-29`

## Regras transversais
- Cross-module somente via `module.api.*`.
- Sem associação JPA/FK SQL cross-module.
- TenantId vem do contexto autenticado.
- Persistência muda somente com Flyway.
- PostgreSQL é o único banco suportado.
- Frontend nunca substitui autorização backend.
- Mudança estrutural exige ADR.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration` quando aplicável.
