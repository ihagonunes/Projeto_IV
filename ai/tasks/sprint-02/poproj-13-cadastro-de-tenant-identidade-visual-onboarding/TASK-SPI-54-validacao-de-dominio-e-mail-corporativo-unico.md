# TASK-SPI-54 — Validação de domínio/e-mail corporativo único

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-54
- **História PO:** POPROJ-13
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Cadastro de Tenant, Identidade Visual & Onboarding

## Fonte original do consolidado
Validação backend para impedir sobreposição de domínios cadastrados.

## Versão reconciliada com a arquitetura
A task está compatível com a baseline atual e não exige alteração estrutural.

## Área proprietária
`iam + presentation`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ADR-03`
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

## Nota de implementação (implementado)

A "Versão reconciliada" afirmava que a task **não exige alteração estrutural** — o que **não se confirmou**: `Tenant` não possuía campo de domínio e `RegisterTenant` só validava unicidade de `slug`.

**Implementado (opção b):** domínio persistido com `UNIQUE` e validado no cadastro.
- Migration `V007` (`iam_tenants.email_domain` + índice único).
- `Tenant.emailDomain` (normalizado, imutável), `RegisterTenant` rejeitando sobreposição (`TenantDomainAlreadyExistsException` → 409).
- Porta `TenantDomainRepository`; `emailDomain` em `TenantRequest`/`TenantResponse`.
- **Emenda ao ADR-32:** o login resolve o tenant pelo domínio cadastrado; o "primeiro rótulo" fica apenas como fallback para tenants sem domínio.

**Pendência de frontend:** `emailDomain` é obrigatório em `POST /api/v1/tenants`.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration` quando aplicável.
