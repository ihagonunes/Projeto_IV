# TASK-SPI-16 — Aplicar filtro obrigatório de tenant_id nas queries de leitura

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-16
- **História PO:** POPROJ-51
- **Status:** Dev
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `complexidade-media`, `sprint-1`, `sprint-2`
- **Grupo:** Multi-tenancy & Isolamento Lógico

## Fonte original do consolidado
Aplicar isolamento de tenant em todas as consultas de leitura do MVP.

### Critérios originais
- Consultas protegidas incluem filtro de tenant autenticado
- Ausência de tenant impede consulta
- Testes comprovam não vazamento entre tenants

## Versão reconciliada com a arquitetura
A task está compatível com a baseline atual e não exige alteração estrutural.

## Área proprietária
`sharedkernel + iam + persistência tenant-aware`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/testing.md`
- `ADR-03`
- `ADR-29`
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
