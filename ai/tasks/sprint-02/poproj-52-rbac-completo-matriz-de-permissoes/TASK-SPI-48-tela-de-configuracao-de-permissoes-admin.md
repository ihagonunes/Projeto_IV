# TASK-SPI-48 — Tela de configuração de permissões (admin)

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-48
- **História PO:** POPROJ-52
- **Status:** Dev
- **Responsável:** Lyn
- **Prioridade:** Medium
- **Categorias:** `complexidade-media`, `sprint-2`
- **Grupo:** RBAC Completo & Matriz de Permissões

## Fonte original do consolidado
Interface web para administradores visualizarem e configurarem matrizes de permissão.

## Versão reconciliada com a arquitetura
A task está compatível com a baseline atual e não exige alteração estrutural.

## Área proprietária
`iam`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ai/testing.md`
- `ADR-04`
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
