# TASK-SPI-57 — Status do tenant (ativo/suspenso)

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-57
- **História PO:** POPROJ-13
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Cadastro de Tenant, Identidade Visual & Onboarding

## Fonte original do consolidado
Bloqueio global de login para tenants suspensos por admin de plataforma.

## Versão reconciliada com a arquitetura

### Objetivo técnico
Usar Tenant ACTIVE/INACTIVE para bloqueio global.

### Requisitos reconciliados
- [ ] ACTIVE permite login.
- [ ] INACTIVE bloqueia login e requests protegidos.
- [ ] Não criar SUSPENDED sem novo ADR.

### Motivo
Baseline formaliza ACTIVE | INACTIVE.

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

## Nota de implementação (revisão pendente)

Os requisitos reconciliados desta task estão **satisfeitos** e cobertos por teste de integração (`TenantManagementIT`): `ACTIVE` permite login; `INACTIVE` bloqueia login e requests protegidos (mesmo com token válido); não existe `SUSPENDED`.

**Pendência (não implementada de propósito):** não há endpoint para **alterar** o status do tenant. O ator previsto ("admin de plataforma") não existe no código — só há administradores do próprio tenant (roles por tenant), e suspender a partir de dentro produz lockout irreversível. Criar esse ator é decisão arquitetural e exige ADR; ver `ai/decisions.md` → *Decisões abertas*.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration` quando aplicável.
