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

Exceção: `sharedkernel` não segue layers de negócio; contém apenas tipos transversais em `id/`, `event/`, `tenant/` e `exception/`.

Direção interna:

```text
infrastructure → application → domain
```

Outro módulo pode acessar somente `<module>.api.*`.

## Ownership

| Módulo | Ownership principal |
|---|---|
| sharedkernel | IDs, DomainEvent, TenantContext, exceções base |
| iam | Tenant, User, autenticação, permissões, AuthorizationScope |
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
| aigateway | sharedkernel, department |
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

- login: `tenantSlug + email + password`;
- BCrypt;
- JWT Bearer stateless, TTL 30 min, sem refresh no MVP;
- claims estáveis: UserId/TenantId + iat/exp;
- permissões/escopos vêm do IAM atual;
- Tenant/User ACTIVE revalidados;
- Chat é privado inclusive de MANAGER/ADMIN;
- LLM nunca decide autorização.

## Ambientes

```text
DEV LOCAL
Spring Boot local → PostgreSQL local

REPRODUZÍVEL
Docker Compose → backend + PostgreSQL
```

Docker não cria container por módulo.
