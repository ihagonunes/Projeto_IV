# TASK-SPI-35 — Endpoint de atribuição/alteração de papel

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-35
- **História PO:** POPROJ-16
- **Status:** Dev
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `complexidade-media`, `sprint-1`, `sprint-2`
- **Grupo:** Atribuição de Papéis

## Fonte original do consolidado
Alteração de nível hierárquico dentro do tenant restrita a administradores autorizados.

## Versão reconciliada com a arquitetura
A task está compatível com a baseline atual e não exige alteração estrutural.

## Área proprietária
`iam`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ADR-04`

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
