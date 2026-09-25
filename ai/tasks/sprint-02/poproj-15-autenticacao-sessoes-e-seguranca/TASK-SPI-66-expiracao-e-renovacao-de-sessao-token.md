# TASK-SPI-66 — Expiração e renovação de sessão/token

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-66
- **História PO:** POPROJ-15
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Autenticação, Sessões e Segurança

## Fonte original do consolidado
Token expirado bloqueia acesso imediatamente; Comportamento uniforme em todos os endpoints protegidos.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Tratar expiração do JWT sem refresh token.

### Requisitos reconciliados
- [ ] Token expirado bloqueia imediatamente.
- [ ] Comportamento uniforme nos endpoints protegidos.
- [ ] Sem refresh token.
- [ ] Novo acesso exige novo login.

### Motivo
ADR-30: JWT 30 min, sem refresh no MVP.

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
