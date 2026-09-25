# TASK-SPI-78 — Testes de integração do pipeline de logging

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-78
- **História PO:** POPROJ-23
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Pipeline de Métricas, Logs de Uso e Custos de IA

## Fonte original do consolidado
Testes end-to-end: requisição -> middleware -> cálculo de custo -> banco -> API.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Testar completion → AIRequestCompleted → Observability → UsageRecord.

### Requisitos reconciliados
- [ ] PostgreSQL real/Testcontainers.
- [ ] Idempotência por RequestId.
- [ ] Cobrir efeito secundário de Observability.
- [ ] Sem endpoint público de ingestão inexistente.

### Motivo
Pipeline oficial do ADR-13.

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
