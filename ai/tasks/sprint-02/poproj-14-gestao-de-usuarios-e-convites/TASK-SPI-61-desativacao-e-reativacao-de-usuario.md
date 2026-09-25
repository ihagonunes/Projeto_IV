# TASK-SPI-61 — Desativação e reativação de usuário

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-61
- **História PO:** POPROJ-14
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Gestão de Usuários e Convites

## Fonte original do consolidado
Usuário inativo impedido de efetuar login; Histórico preservado; Reativação restaura acesso.

## Versão reconciliada com a arquitetura
A task está compatível com a baseline atual e não exige alteração estrutural.

## Área proprietária
`iam`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/contracts.md`
- `ADR-04`
- `ADR-05`
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
