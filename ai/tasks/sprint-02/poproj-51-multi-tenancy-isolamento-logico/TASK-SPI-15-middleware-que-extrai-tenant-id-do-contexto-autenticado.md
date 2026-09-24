# TASK-SPI-15 — Middleware que extrai tenant_id do contexto autenticado

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-15
- **História PO:** POPROJ-51
- **Status:** Code Review
- **Responsável:** IHAN DE OLIVEIRA NUNES
- **Prioridade:** Medium
- **Categorias:** `complexidade-media`, `sprint-1`, `sprint-2`
- **Grupo:** Multi-tenancy & Isolamento Lógico

## Fonte original do consolidado
Implementar middleware responsável por obter `tenant_id` do contexto autenticado e disponibilizá-lo às camadas seguintes.

### Critérios originais
- Identifica tenant autenticado
- Contexto padronizado
- Rejeição se sem tenant
- Bloqueio de substituição via cliente
- Cobertura de testes

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
