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
| ADR-34 | Contrato de autorização RBAC exposto a outros módulos | ACCEPTED | `ai/adr/ADR-34-rbac-authorization-contract-cross-module.md` |
| ADR-35 | Agente de políticas: recuperação de conhecimento e ferramentas (opção A) | ACCEPTED | `ai/adr/ADR-35-policy-agent-knowledge-retrieval.md` |
| ADR-36 | Concessão com escopo como fonte única de autorização | ACCEPTED | `ai/adr/ADR-36-scoped-role-grants.md` |
| ADR-37 | Provisionamento, delegação e revogação de concessões | ACCEPTED | `ai/adr/ADR-37-grant-provisioning-and-delegation.md` |
| ADR-38 | Provider real, tentativas duráveis e entrega idempotente de uso | ACCEPTED | `ai/adr/ADR-38-ai-execution-attempts-and-usage-delivery.md` |
| ADR-39 | Chat privado e recibo de execução | ACCEPTED | `ai/adr/ADR-39-private-chat-and-execution-receipt.md` |
| ADR-40 | Engagement no MVP: fontes, elegíveis e parâmetros de adoção, treinamento e AI Credits | ACCEPTED | `ai/adr/ADR-40-engagement-mvp-sources-and-formulas.md` |
| ADR-41 | Projeção determinística de orçamento (Pesquisa Operacional) | ACCEPTED | `ai/adr/ADR-41-deterministic-budget-projection.md` |
| ADR-42 | Ciclo de vida da conversa, recibo durável e modelos do usuário (emenda ADR-39) | ACCEPTED | `ai/adr/ADR-42-chat-lifecycle-and-durable-receipt.md` |

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

### Recuperação de senha (SPI-65) — BLOQUEADO por decisão

- **Origem:** SPI-65 / POPROJ-15 ("link de recuperação expira após tempo definido; nova senha segue política de segurança; link de uso único").
- **Lacuna 1 — entrega do link:** não existe infraestrutura de e-mail/notificação no projeto (nenhuma dependência de mail no `pom.xml`, nenhum notificador). Sem canal de entrega o fluxo não fecha: retornar o token na resposta HTTP permitiria sequestro de conta, e registrá-lo em log viola o AGENTS §9 ("logs nunca contêm tokens").
- **Lacuna 2 — política de senha:** não existe política de senha no projeto (nenhuma validação de força/complexidade em qualquer ponto).
- **Opções (a decidir):** (a) porta `PasswordResetNotifier` no `application` com implementação **no-op** no MVP (o token nunca é logado nem devolvido), deixando o provider real como evolução; (b) introduzir `spring-boot-starter-mail` (nova dependência → exige justificativa em `ai/dependencies.md` ou ADR); (c) manter fora do MVP.
- **Enquanto não decidido:** nenhum endpoint de recuperação de senha é exposto.

### Backend compartilhado para rate limiting (SPI-67) — decisão futura

- **Implementado:** rate limiting do login **em memória** (`LoginAttemptLimiter`), por instância, em janela fixa (padrão: 5 tentativas / 15 min), chaveado por IP + e-mail. Sem lockout persistente de conta, conforme a task e o ADR-30.
- **Limitações conhecidas:** (i) o limite é **por instância** — não é compartilhado entre réplicas; (ii) o estado se perde em restart; (iii) a chave usa o IP do socket (`getRemoteAddr()`), então atrás de um proxy reverso todos os clientes compartilham o IP do proxy (`X-Forwarded-For` exigiria configuração de proxy confiável).
- **Decisão futura:** um backend compartilhado (ex.: Redis) para o rate limiting exigiria dependência nova → justificar em `ai/dependencies.md` ou ADR.

## Simplificações de escopo (Sprint 2) — APLICADAS

Cortes deliberados para reduzir complexidade e superfície de segurança, sem remover função do usuário.

### 1. `PATCH /api/v1/usuarios/{id}` edita **apenas** o Department (não o papel/nível)

- **Motivo:** o modelo de autorização é por **papéis por tenant** (ADR-33) e o `PolicyEvaluator` lê `iam_role_assignments` — **não** o `hierarchyLevel`. Deixar `hierarchyLevel` no PATCH dava a falsa impressão de mover privilégios.
- **Efeito:** o corpo do PATCH é `{ departmentId }`. O nível continua em `POST /api/v1/usuarios/{id}/nivel`, como **rótulo organizacional**.
- **Contrato alterado** — `UpdateUserRequest`/`UpdateUserCommand` e `UserResponse` (ganhou `departmentId`).

### 2. `POST /api/v1/usuarios` **não** aceita senha — **SUPERADA pela TASK-S3-A7**

> O endpoint `POST /api/v1/usuarios` foi **removido** na Sprint 3 (A7): criar usuário sem
> senha deixava contas inextinguíveis/inutilizáveis. O único caminho de criação passa a ser
> o **convite** (`POST /api/v1/convites` + `POST /api/v1/convites/aceitar`), que carrega
> papel, departamento e define a senha no aceite. O texto abaixo fica como registro
> histórico.

- **Motivo:** o caminho legítimo para o usuário obter acesso é o **convite** (SPI-29/30), que define a senha no aceite. Aceitar senha no cadastro permitia ao administrador fixar/transmitir senha por canal inseguro.
- **Efeito:** o payload é `{ email, name, hierarchyLevel, departmentId? }`; o endpoint usa `CreateUser` (sem senha). O usuário criado assim não autentica até aceitar um convite (ou passar por recuperação de senha, quando existir).

### 3. `UserRepository.findById` é **sempre** tenant-scoped

- **Motivo:** a assinatura antiga alternava entre filtrar e não filtrar conforme houvesse `TenantContext` — uma armadilha para callers futuros (ex.: job assíncrono, listener), que poderiam ler usuários de outro tenant.
- **Efeito:** `findById` **exige** `TenantContext` (senão `IllegalStateException`); o caminho de autenticação usa `findByIdUnscoped` **explicitamente** (`TenantContextFilter`, onde o contexto ainda não existe).
