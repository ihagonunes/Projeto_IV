# TASK-SPI-92 — Layout e componentes visuais do dashboard

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-92
- **História PO:** POPROJ-53
- **Status:** Tarefas pendentes
- **Responsável:** Lyn
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Dashboard de Métricas, Filtros e Agregações

## Fonte original do consolidado
Interface responsiva aderente à identidade visual do tenant.

## Versão reconciliada com a arquitetura
A task está compatível com a baseline atual e não exige alteração estrutural.

## Área proprietária
`observability + presentation`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ADR-13`
- `ADR-28`
- `ADR-30`

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
