# Decisões consolidadas

Este arquivo é um índice. Leia somente o ADR relacionado à tarefa.

| ID | Decisão | Status | Arquivo |
|---|---|---|---|
| ADR-01 | Modular Monolith | ACCEPTED | `ai/adr/ADR-01-modular-monolith.md` |
| ADR-02 | Cross-module Communication | ACCEPTED | `ai/adr/ADR-02-cross-module-communication.md` |
| ADR-03 | Multi-tenancy | ACCEPTED | `ai/adr/ADR-03-multi-tenancy.md` |
| ADR-04 | IAM | ACCEPTED | `ai/adr/ADR-04-iam.md` |
| ADR-05 | User Area Ownership | ACCEPTED | `ai/adr/ADR-05-user-area-ownership.md` |
| ADR-06 | Budget Ownership | ACCEPTED | `ai/adr/ADR-06-budget-ownership.md` |
| ADR-07 | ModelPolicy Ownership | ACCEPTED | `ai/adr/ADR-07-modelpolicy-ownership.md` |
| ADR-08 | Mandatory Default Model | ACCEPTED | `ai/adr/ADR-08-mandatory-default-model.md` |
| ADR-09 | AI Catalog | ACCEPTED | `ai/adr/ADR-09-ai-catalog.md` |
| ADR-10 | Provider Credentials | ACCEPTED | `ai/adr/ADR-10-provider-credentials.md` |
| ADR-11 | AI Completion | ACCEPTED | `ai/adr/ADR-11-ai-completion.md` |
| ADR-12 | AI Failure Handling | ACCEPTED | `ai/adr/ADR-12-ai-failure-handling.md` |
| ADR-13 | Usage Observability | ACCEPTED | `ai/adr/ADR-13-usage-observability.md` |
| ADR-14 | Knowledge Access Abstraction | ACCEPTED | `ai/adr/ADR-14-knowledge-access-abstraction.md` |
| ADR-15 | KnowledgeDocument Authorization | ACCEPTED | `ai/adr/ADR-15-knowledgedocument-authorization.md` |
| ADR-16 | Chat Privacy | ACCEPTED | `ai/adr/ADR-16-chat-privacy.md` |
| ADR-17 | Engagement | ACCEPTED | `ai/adr/ADR-17-engagement.md` |
| ADR-18 | Adoption Window | ACCEPTED | `ai/adr/ADR-18-adoption-window.md` |
| ADR-19 | AI Credits | ACCEPTED | `ai/adr/ADR-19-ai-credits.md` |
| ADR-20 | Governance Score | ACCEPTED | `ai/adr/ADR-20-governance-score.md` |
| ADR-21 | Missing Score Data | ACCEPTED | `ai/adr/ADR-21-missing-score-data.md` |
| ADR-22 | Security Compliance | ACCEPTED | `ai/adr/ADR-22-security-compliance.md` |
| ADR-23 | Utilization | ACCEPTED | `ai/adr/ADR-23-utilization.md` |
| ADR-24 | Project | ACCEPTED | `ai/adr/ADR-24-project.md` |
| ADR-25 | Agent Policies Lifecycle | ACCEPTED | `ai/adr/ADR-25-agent-policies-lifecycle.md` |
| ADR-26 | Operational and Historical Analytics Separation | ACCEPTED | `ai/adr/ADR-26-operational-and-historical-analytics-separation.md` |
| ADR-27 | Governance Score Period and Snapshot | ACCEPTED | `ai/adr/ADR-27-governance-score-period-and-snapshot.md` |
| ADR-28 | REST API Design | ACCEPTED | `ai/adr/ADR-28-rest-api-design.md` |
| ADR-29 | Modular Persistence | ACCEPTED | `ai/adr/ADR-29-modular-persistence.md` |
| ADR-30 | Authentication and JWT Security | ACCEPTED | `ai/adr/ADR-30-authentication-and-jwt-security.md` |
| ADR-31 | Physical Modular Structure and Enforcement | ACCEPTED | `ai/adr/ADR-31-physical-modular-structure-and-enforcement.md` |
| ADR-32 | Tenant resolution on login (email-domain fallback) | ACCEPTED (emendado pela SPI-54) | `ai/adr/ADR-32-tenant-resolution-on-login.md` |
| ADR-33 | Modelo de autorização (RBAC por papéis por tenant) | ACCEPTED | `ai/adr/ADR-33-authorization-model-roles.md` |

## Decisões abertas (pendentes de ADR)

Itens **identificados e ainda não decididos**. Não implementar antes de um ADR.

### Escopo de plataforma e ciclo de vida do tenant (SPI-57)

- **Origem:** SPI-57 / POPROJ-13 ("bloqueio global de login para tenants suspensos por admin de plataforma").
- **Já garantido:** `TenantStatus` (`ACTIVE`/`INACTIVE`), com bloqueio no login (`AuthenticateUser`) e em requests protegidos (`TenantContextFilter`); coberto por `TenantManagementIT`.
- **Em aberto:** **não existe ator autorizado a alterar o status do tenant.** A task pressupõe um "admin de plataforma"; a baseline só formaliza atores tenant-scoped (ADR-03/ADR-04/ADR-30). Suspender a partir do próprio tenant produz **lockout irreversível** — ninguém consegue autenticar para reativar.
- **Se decidido (proposta):** ADR novo (escopo de plataforma) + emenda/referência ao ADR-03 (exceção explícita ao isolamento) e possivelmente ao ADR-30 (JWT sem `tenant_id`); atualização de `ai/architecture.md` (ownership e allowlist de `platform`, hoje ausentes); ator/módulo de plataforma; migration para auditoria (`status_changed_at`/`status_changed_by`).
- **Enquanto não decidido:** nenhum endpoint de mudança de status de tenant é exposto.

### Domínio de e-mail corporativo único (SPI-54) — RESOLVIDO (emenda ao ADR-32)

- **Origem:** SPI-54 / POPROJ-13 ("validação backend para impedir sobreposição de domínios cadastrados").
- **Tensão original:** a task assume "não exige alteração estrutural", mas `Tenant` não possuía campo de domínio e `RegisterTenant` só validava unicidade de `slug`.
- **Decisão (opção b):** persistir o domínio com restrição `UNIQUE` e validar no cadastro, em vez de derivá-lo do `slug`.
- **Implementado:** migration `V007` (`iam_tenants.email_domain` + índice único), `Tenant.emailDomain`, `RegisterTenant` rejeitando sobreposição (`TenantDomainAlreadyExistsException` → 409), porta `TenantDomainRepository`, `emailDomain` no `TenantRequest`/`TenantResponse` e **emenda ao ADR-32** (login resolvido pelo domínio cadastrado).
- **Pendência (frontend):** `emailDomain` passou a ser **obrigatório** em `POST /api/v1/tenants`; o formulário de onboarding precisa enviar o campo. Tenants legados sem domínio seguem usando o fallback do ADR-32.
