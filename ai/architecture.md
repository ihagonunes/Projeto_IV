# Arquitetura

## Estilo

Monólito Modular Java/Spring Boot, um único projeto Maven e um único PostgreSQL no MVP.

```text
br.com.qsoft.governance
├── sharedkernel
├── iam
├── department
├── project
├── aigateway
├── chat
├── agentpolicies
├── observability
├── engagement
├── governancescore
└── platform
```

## Estrutura interna padrão

```text
<module>/
├── api/
├── domain/
├── application/
└── infrastructure/
```

Exceção: `sharedkernel` não segue layers de negócio; contém apenas tipos transversais em `id/`, `event/`, `tenant/`, `exception/` e `rbac/` (vocabulário `resource × action` — ADR-34).

Direção interna:

```text
infrastructure → application → domain
```

Outro módulo pode acessar somente `<module>.api.*`.

## Ownership

| Módulo | Ownership principal |
|---|---|
| sharedkernel | IDs, DomainEvent, TenantContext, exceções base, vocabulário de autorização (`Resource`/`Action`/`ScopeType`/`AccessTarget`/`VisibilityScope`/`Targeting` — ADR-34/ADR-36) |
| iam | Tenant, User, autenticação, papéis de sistema, concessões com escopo (`RoleAssignment`), `AuthorizationQuery`, contrato `@RequiresPermission` (ADR-36/ADR-37) |
| department | Department, Area, UserAreaMembership, BudgetLimit, ModelPolicy |
| project | Project mínimo |
| aigateway | Provider, AITool, Model, credenciais por referência, completion |
| chat | Conversation, Message |
| agentpolicies | KnowledgeDocument/Version, Agent, AgentExecution, KnowledgeAccess |
| observability | UsageRecord, custo, BudgetStatus, Alert |
| engagement | AILicense, TrainingRecord, adoção, AI Credits |
| governancescore | score, breakdown, snapshot/histórico |

## Allowlist síncrona

| Consumidor | Pode depender de |
|---|---|
| iam | sharedkernel |
| department | sharedkernel, iam |
| project | sharedkernel, iam, department |
| aigateway | sharedkernel, iam, department |
| chat | sharedkernel, iam, aigateway |
| agentpolicies | sharedkernel, iam, department, aigateway |
| observability | sharedkernel, iam, department |
| engagement | sharedkernel, iam, observability, aigateway |
| governancescore | sharedkernel, iam, engagement, observability |

ArchUnit deve falhar para qualquer dependência não declarada ou import interno cross-module.

## Multi-tenancy

- `tenant_id` obrigatório em dados empresariais relevantes.
- TenantId deriva do contexto autenticado.
- Hibernate filter pode ser defesa transversal, nunca única defesa.
- SQL nativo tenant-aware filtra `tenant_id` explicitamente.
- testes provam Tenant A ≠ Tenant B.

## Persistência

- PostgreSQL único.
- tabelas/repositories pertencem ao módulo.
- FK intramodule permitida.
- FK/JPA association cross-module proibida.
- Flyway é a única evolução de schema.
- Hibernate usa `ddl-auto=validate`.
- histórico QSOFT fica em `historical_*`, sem FK operacional.

## AI Gateway

```text
requestedModelId?
├─ sim → precisa pertencer a allowedModelIds
└─ não → defaultModelId

Model AVAILABLE
→ Provider ACTIVE
→ Provider Adapter
→ CompletionResult
→ AIRequestCompleted
```

Múltiplos Providers podem estar ACTIVE. Provider local não implica Docker.

## Segurança

- login: `email + password`; `tenantSlug` é **opcional** e, quando ausente, o tenant é
  resolvido pelo domínio de e-mail cadastrado (ADR-32, emendado pela SPI-54);
- BCrypt;
- JWT Bearer stateless, TTL 30 min, sem refresh no MVP;
- claims estáveis: UserId/TenantId + iat/exp;
- cada requisição protegida lê as **concessões ativas** do IAM, sem cache; revogar vale
  na requisição seguinte (ADR-36/ADR-37);
- permissões/escopos vêm do IAM atual (nunca do frontend ou do LLM);
- Tenant/User ACTIVE revalidados;
- Chat é privado inclusive de MANAGER/ADMIN;
- LLM nunca decide autorização.

## Pendências de decisão (não decidido)

- **Escopo de plataforma (SPI-57).** A baseline assume que todo principal autenticado possui `tenant_id` e é tenant-scoped (ADR-03/ADR-30). O bloqueio global de tenant (`TenantStatus` `ACTIVE`/`INACTIVE`) já é aplicado no login (`AuthenticateUser`) e em requests protegidos (`TenantContextFilter`), porém **não há ator autorizado a alterar o status**: a task pressupõe um "admin de plataforma" que não existe, e suspender a partir do próprio tenant gera lockout irreversível. Exige ADR antes de virar endpoint — ver `ai/decisions.md` → *Decisões abertas*.

- **Domínio de e-mail corporativo (SPI-54) — resolvido.** Cada tenant passa a ter um domínio único (`iam_tenants.email_domain`, migration `V007`), validado no cadastro (409 em sobreposição). A resolução do tenant no login usa o domínio **cadastrado**, com fallback legado (ADR-32 emendado). Pendência de frontend: `emailDomain` é obrigatório em `POST /api/v1/tenants`.

## Ambientes

```text
DEV LOCAL
Spring Boot local → PostgreSQL local

REPRODUZÍVEL
Docker Compose → backend + PostgreSQL
```

Docker não cria container por módulo.
