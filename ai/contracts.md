# Contratos públicos

Contratos são Java APIs internas entre módulos. Não equivalem automaticamente a HTTP.

## Módulos

| Módulo | Contratos públicos principais |
|---|---|
| iam | `IdentityQuery`, `AuthorizationQuery` |
| department | `OrganizationQuery`, `DepartmentBudgetQuery`, `DepartmentModelPolicyQuery` |
| project | `ProjectQueryPort` |
| aigateway | `AICompletionService`, `AIToolCatalogQuery`, catálogo administrativo |
| chat | commands/queries de Conversation/Message |
| agentpolicies | KnowledgeDocument commands/queries, `KnowledgeAccessService`, `AgentExecutionService` |
| observability | `UsageMetricsQueryPort`, `CostMetricsQueryPort`, `BudgetStatusQueryPort`, `AlertQueryPort` |
| engagement | `LicenseQueryPort`, `TrainingQueryPort`, `EngagementMetricsQueryPort`, `AICreditsQueryPort` |
| governancescore | `GovernanceScoreQueryPort`, Breakdown, Department scores, History |

## Eventos definitivos do MVP

### UserProvisioned

Produtor: IAM  
Consumidor: Engagement, para elegibilidade.

### UserDeactivated

Produtor: IAM  
Consumidor: Engagement, para retirar da população elegível.

### AIRequestCompleted

Produtor: AI Gateway  
Consumidor: Observability.

Regra: é a **única origem operacional de UsageRecord**.

`AIRequestFailed` não existe no MVP.

`AgentExecutionCompleted` só deve ser criado se surgir consumidor explícito.

## Completion

```text
CompletionCommand
- userId
- tenantId
- departmentId
- conversationId?
- messages[]
- requestedModelId?
- generationConfigOverride?

CompletionResult
- content
- modelId
- UsageStats
- estimatedCost
- latencyMs
```

O Gateway calcula custo uma única vez.

## REST

- prefixo `/api/v1`;
- login: `/api/v1/auth/login`;
- tenant não é parâmetro confiável do cliente;
- sem `/ai/complete` público;
- paginação `page`/`size`, zero-based;
- default 20, máximo 100.
