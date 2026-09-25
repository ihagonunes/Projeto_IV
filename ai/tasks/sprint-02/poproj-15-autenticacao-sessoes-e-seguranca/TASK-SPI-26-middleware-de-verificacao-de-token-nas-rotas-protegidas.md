# TASK-SPI-26 — Middleware de verificação de token nas rotas protegidas

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-26
- **História PO:** POPROJ-15
- **Status:** Pull Request (PR & MR)
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `complexidade-media`, `sprint-1`, `sprint-2`
- **Grupo:** Autenticação, Sessões e Segurança

## Fonte original do consolidado
Validar JWT em rotas protegidas e injetar usuário autenticado no contexto.

### Critérios originais
- Validação de assinatura/expiração/claims
- Bloqueio de token inválido/expirado
- Nenhuma rota sensível aberta

## Versão reconciliada com a arquitetura

### Objetivo técnico
Validar JWT e estabelecer TenantContext após validação criptográfica.

### Requisitos reconciliados
- [ ] Validar assinatura/expiração.
- [ ] Revalidar Tenant ACTIVE e User ACTIVE.
- [ ] Resolver permissões/AuthorizationScope atuais.
- [ ] Bloquear token inválido/expirado.

### Motivo
ADR-30 exige revalidação de estado mutável.

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
