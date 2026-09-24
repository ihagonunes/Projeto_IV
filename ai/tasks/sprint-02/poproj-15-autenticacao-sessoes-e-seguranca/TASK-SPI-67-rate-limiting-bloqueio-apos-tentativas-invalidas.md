# TASK-SPI-67 — Rate limiting / bloqueio após tentativas inválidas

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-67
- **História PO:** POPROJ-15
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Autenticação, Sessões e Segurança

## Fonte original do consolidado
Bloqueio temporário após N tentativas inválidas; Recuperação de senha segue funcional.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Aplicar rate limiting no login sem criar novo estado persistente de conta por padrão.

### Requisitos reconciliados
- [ ] Rate limiting no login.
- [ ] Mensagens seguras.
- [ ] Lockout persistente só com nova decisão funcional.

### Motivo
ADR-30 exige rate limiting, não lockout permanente.

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

## Nota de implementação (implementado)

Rate limiting do login **em memória** (`LoginAttemptLimiter`, `iam/infrastructure/security`), aplicado no `AuthController` antes de autenticar.

- **Janela fixa** configurável: `app.security.login-rate-limit.max-attempts` (padrão 5) e `window-minutes` (padrão 15).
- **Chave** = IP do socket + e-mail normalizado — existe independentemente de a conta existir, o que evita enumeração.
- **Bloqueio temporário** → HTTP **429** com mensagem genérica (`TooManyLoginAttemptsException`); **não** há lockout persistente de conta.
- Login bem-sucedido **zera** o contador.
- Cobertura: `LoginAttemptLimiterTest` (unitário) e `LoginRateLimitIT` (integração).

**Limitações / decisão futura** (ver `ai/decisions.md` → *Decisões abertas*): o limite é por instância (não compartilhado entre réplicas) e se perde em restart; atrás de proxy reverso a chave por `getRemoteAddr()` é grosseira. Backend compartilhado (ex.: Redis) exigiria dependência nova.

> Requisito "recuperação de senha segue funcional" depende da SPI-65, que está **bloqueada por decisão** (não há canal de entrega nem política de senha).

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration` quando aplicável.
