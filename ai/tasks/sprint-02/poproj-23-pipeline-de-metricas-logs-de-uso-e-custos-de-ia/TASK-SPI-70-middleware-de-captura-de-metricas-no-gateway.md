# TASK-SPI-70 — Middleware de captura de métricas no Gateway

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-70
- **História PO:** POPROJ-23
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Pipeline de Métricas, Logs de Uso e Custos de IA

## Fonte original do consolidado
Interceptação assíncrona com registro de latência, status e metadados sem impacto na resposta.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Emitir AIRequestCompleted no Gateway como única origem operacional de UsageRecord.

### Requisitos reconciliados
- [ ] Gateway coleta métricas da completion.
- [ ] Emitir AIRequestCompleted.
- [ ] Observability consome como efeito secundário.
- [ ] Sem segunda trilha operacional por middleware.

### Motivo
ADR-13.

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
