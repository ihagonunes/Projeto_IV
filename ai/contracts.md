# Contratos públicos

Contratos são Java APIs internas entre módulos. Não equivalem automaticamente a HTTP.

## Módulos

| Módulo | Contratos públicos principais |
|---|---|
| iam | `IdentityQuery`, `AuthorizationQuery`, contrato de autorização RBAC (`@RequiresPermission` em `iam.api.rbac` — ADR-34) |
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

`aigateway.api.completion.AICompletionService` (ADR-11/ADR-38):

```text
CompletionCommand
- tenantId            (do contexto autenticado)
- userId
- requestedModelId?
- purpose             (CHAT | POLICY_AGENT)
- messages[]          (role SYSTEM | USER | ASSISTANT | TOOL, content, toolCallId?)
- tools[]?            (ToolSpec: name, description, jsonSchemaParameters)

CompletionResult
- requestId           (sempre gerado pelo servidor)
- content
- toolCalls[]
- modelId, modelName, providerId, providerName
- inputTokens?, outputTokens?
- estimatedCost?
- costStatus          (KNOWN | NOT_APPLICABLE | INCONCLUSIVE)
- latencyMs
```

- O `departmentId` **não** é informado pelo chamador: o Gateway resolve a lotação atual pelo
  `IdentityQuery` e aplica a `ModelPolicy` (`ResolveModelForUser`, ADR-07/08).
- O `requestId` é gerado pelo servidor e identifica a tentativa e o `UsageRecord`.
- Exceções públicas em `aigateway.api.exception`, para o chamador mapear:
  - `ModelNotAllowedException` → 403;
  - `NoModelConfiguredException`, `ModelUnavailableException`, `ProviderInactiveException` → 409;
  - `AIProviderTimeoutException` → 504;
  - `AIProviderFailureException` → 502;
  - `AIAttemptNotRecordedException` → 503.
- Antes de chamar o provider, o Gateway grava a tentativa `IN_FLIGHT` em transação própria
  (`REQUIRES_NEW`); `DENIED` e o resultado final também são persistidos (ADR-38).
- O Gateway calcula o custo uma única vez, com os preços fotografados no início da tentativa.

## REST

- prefixo `/api/v1`;
- login: `/api/v1/auth/login`;
- tenant não é parâmetro confiável do cliente;
- sem `/ai/complete` público;
- paginação `page`/`size`, zero-based;
- default 20, máximo 100.
