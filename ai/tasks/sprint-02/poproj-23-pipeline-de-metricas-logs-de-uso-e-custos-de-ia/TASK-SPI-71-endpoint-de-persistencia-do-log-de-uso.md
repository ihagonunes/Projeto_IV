# TASK-SPI-71 — Endpoint de persistência do log de uso

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-71
- **História PO:** POPROJ-23
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Pipeline de Métricas, Logs de Uso e Custos de IA

## Fonte original do consolidado
Gravação não-bloqueante resiliente a falhas de banco.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Persistir UsageRecord internamente a partir de AIRequestCompleted.

### Requisitos reconciliados
- [ ] Handler interno no Observability.
- [ ] Idempotência por RequestId.
- [ ] Falha do Observability não invalida completion válida.
- [ ] Sem POST público genérico de logs.

### Motivo
Comunicação cross-module por contratos/eventos.

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
