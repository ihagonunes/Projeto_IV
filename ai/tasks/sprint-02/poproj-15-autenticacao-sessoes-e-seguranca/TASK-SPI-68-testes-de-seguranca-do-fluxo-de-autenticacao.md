# TASK-SPI-68 — Testes de segurança do fluxo de autenticação

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-68
- **História PO:** POPROJ-15
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Autenticação, Sessões e Segurança

## Fonte original do consolidado
Cobertura de login, expiração, recuperação de senha, rate limiting e ataques simulados em CI.

## Versão reconciliada com a arquitetura
A task está compatível com a baseline atual e não exige alteração estrutural.

## Área proprietária
`iam + platform/security`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/testing.md`
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
