# TASK-SPI-69 — Modelagem do schema ai_usage_log

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-69
- **História PO:** POPROJ-23
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Pipeline de Métricas, Logs de Uso e Custos de IA

## Fonte original do consolidado
Schema compatível com a estrutura de logs sintéticos da QSOFT.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Modelar UsageRecord operacional no Observability, separado de histórico.

### Requisitos reconciliados
- [ ] UsageRecord imutável/idempotente por RequestId.
- [ ] Persistir metadados/tokens/custo/latência.
- [ ] Sem prompt/response/documento/segredo.
- [ ] Histórico em historical_*.

### Motivo
ADR-13/26.

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
