# Contratos públicos

Contratos são Java APIs internas entre módulos. Não equivalem automaticamente a HTTP.

## HTTP — filtros de métricas (POPROJ-53)

- `GET /api/v1/observability/metrics/usage` e `/costs`: `from`/`to` opcionais
  aceitam data ISO (`YYYY-MM-DD`, limites de dia UTC inclusivos) ou timestamp ISO.
  O padrão existente é uma janela de 30 dias até agora; intervalo invertido → 400.
  `modelId` e `userId` são combináveis e preservam a visibilidade aplicada pelo backend.
- `GET /api/v1/ai-gateway/models/dashboard`: `USAGE:READ` em qualquer escopo;
  catálogo do tenant autenticado com modelos `AVAILABLE`, somente `{ id, name }`.
  Não concede administração do catálogo nem amplia o escopo de consumo.
- Rótulos dos modelos nas métricas são resolvidos na apresentação; não existe
  dependência síncrona de `observability` para `aigateway`.

## Módulos

| Módulo | Contratos públicos principais |
|---|---|
| iam | `IdentityQuery` (`getUserContext`/`eligibleUsers`; `UserContext` inclui `name`), `HistoricalIdentityQuery` (`employeeId`/`employeeIds`), `AuthorizationQuery` (`isAllowed`/`visibilityFor`), `@RequiresPermission`/`Targeting` em `iam.api.rbac` (ADR-34/ADR-36/ADR-37/ADR-40) |
| department | `OrganizationQuery`, `DepartmentBudgetQuery` (`budgetFor`/`budgetsFor` + `BudgetLimitInfo`), `DepartmentModelPolicyQuery` |
| project | `ProjectQueryPort` |
| aigateway | `AICompletionService`, `ModelResolutionQuery` (`resolve`/`allowedModels`), `AIToolCatalogQuery`, catálogo administrativo |
| chat | `ListConversations` (status/q), `UpdateConversation`, `DeleteConversation`, commands/queries de Conversation/Message |
| agentpolicies | `KnowledgeAccessService`, `AskPolicyAgent`, KnowledgeDocument commands/queries |
| observability | `UsageActivityQuery` (dias ativos por usuário, dia UTC e `tenant_id` explícito), `UsageMetricsQueryPort`, `CostMetricsQueryPort`, `BudgetStatusQueryPort` (implementado por `QueryBudgetStatus`) |
| engagement | `LicenseQueryPort`, `TrainingQueryPort`, `EngagementMetricsQueryPort`, `AICreditsQueryPort` |
| governancescore | `GovernanceScoreQueryPort`, Breakdown, Department scores, History |

`AlertQueryPort` foi substituído pelo status de orçamento calculado na leitura (sem tabela nova, sem evento e sem bloqueio — A105).

### Chat (ADR-42)

- `GET /api/v1/conversas?status=ACTIVE|ARCHIVED&q=&page=&size=` — só conversas do ator,
  `q` literal (ILIKE escapado), ordem `updated_at DESC, id DESC`; `DELETED` nunca aparece.
- `PATCH /api/v1/conversas/{id}` — `titulo` e/ou `status` (`ACTIVE`/`ARCHIVED`); excluída → 404.
- `DELETE /api/v1/conversas/{id}` — exclusão lógica (`DELETED` + `deleted_at`), 204.
- `GET /api/v1/conversas/modelos` — `ModelResolutionQuery.allowedModels` (política do departamento,
  `available`/`unavailableReason`).
- Erros do chat: `{ "message": "...", "code": "..." }`, com códigos estáveis
  (`CONVERSATION_NOT_FOUND`, `CONVERSATION_ARCHIVED`, `MODEL_NOT_ALLOWED`, `NO_MODEL_CONFIGURED`,
  `PROVIDER_FAILURE`, `PROVIDER_TIMEOUT`, `ATTEMPT_NOT_RECORDED`, `INVALID_REQUEST`).

## Autorização (ADR-34/ADR-36/ADR-37)

`iam.api.query.AuthorizationQuery` (leitura das concessões ativas a cada chamada, sem cache):

```java
boolean isAllowed(TenantId tenantId, UserId actor,
                  Resource resource, Action action, AccessTarget target);

VisibilityScope visibilityFor(TenantId tenantId, UserId actor,
                             Resource resource, Action action);
```

`iam.api.rbac`:

```java
enum Targeting { TENANT_ONLY, BY_TARGET }   // default: TENANT_ONLY

@interface RequiresPermission {
    Resource resource();
    Action action();
    Targeting targeting() default Targeting.TENANT_ONLY;
}
```

`sharedkernel.rbac` (vocabulário transversal):

```java
enum ScopeType { SELF, DEPARTMENT, TENANT }

sealed interface AccessTarget {
    record TenantTarget() implements AccessTarget {}
    record DepartmentTarget(UUID departmentId) implements AccessTarget {}
    record OwnedTarget(UserId owner, UUID ownerDepartment) implements AccessTarget {}
}

sealed interface VisibilityScope {
    record TenantWide() implements VisibilityScope {}
    record Departments(Set<UUID> ids, UserId self) implements VisibilityScope {}
    record SelfOnly(UserId self) implements VisibilityScope {}
    record None() implements VisibilityScope {}
    default boolean coversDepartment(UUID departmentId);
}
```

- `BY_TARGET` aceita qualquer concessão aplicável e **exige** a checagem fina no caso de uso
  (`isAllowed` com o alvo, ou `visibilityFor` para consultas agregadas).
- O teto de `CHAT` é sempre `SELF`, mesmo em concessões `TENANT`/`DEPARTMENT`.

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

## Chat (ADR-39)

Backend privado do chat. O dono é **sempre** o ator do JWT, nunca um id do corpo.
Gate `CHAT:CREATE`/`CHAT:READ` em `BY_TARGET` (o teto SELF é aplicado no caso de uso).
Conversa de outra pessoa → **404**; usuário só `TENANT_ADMIN` (sem `CHAT:*`) → **403**.
O conteúdo nunca vai para log nem evento.

### `POST /api/v1/conversas` (201)

Request:

```json
{ "mensagem": "Olá, preciso de ajuda com políticas", "requestedModelId": null }
```

Response (mesmo formato no `POST /{id}/mensagens`):

```json
{
  "conversa": {
    "id": "6f0c…",
    "titulo": "Olá, preciso de ajuda com políticas",
    "status": "ACTIVE",
    "createdAt": "2026-10-20T12:00:00Z",
    "updatedAt": "2026-10-20T12:00:01Z"
  },
  "mensagens": [
    { "id": "…", "role": "USER", "content": "Olá…", "requestId": null,
      "createdAt": "2026-10-20T12:00:00Z" },
    { "id": "…", "role": "ASSISTANT", "content": "[stub] Olá…",
      "requestId": "7a1b…", "createdAt": "2026-10-20T12:00:01Z" }
  ],
  "recibo": {
    "requestId": "7a1b…",
    "modelo": "gpt-4o",
    "provider": "OpenAI",
    "tokensEntrada": 7,
    "tokensSaida": 7,
    "custoEstimado": 0.000280,
    "statusCusto": "KNOWN",
    "latenciaMs": 12
  }
}
```

O `requestId` do recibo é o mesmo da tentativa (`aigateway_request_attempts`) e do
`UsageRecord`. Em falha do provider a mensagem USER fica gravada, nenhuma ASSISTANT é criada e o
erro segue o mapa da seção Completion (ex.: 502).

### `GET /api/v1/conversas?page=0&size=20`

```json
{ "content": [ { "id": "6f0c…", "titulo": "…", "status": "ACTIVE",
                 "createdAt": "…", "updatedAt": "…" } ],
  "totalElements": 1, "page": 0, "size": 20, "totalPages": 1 }
```

### `GET /api/v1/conversas/{id}/mensagens`

```json
[ { "id": "…", "role": "USER", "content": "…", "requestId": null,
    "createdAt": "…" } ]
```

### `POST /api/v1/conversas/{id}/mensagens` (201)

Mesmo request/response de `POST /api/v1/conversas`; 404 se a conversa não for do ator.

## Agente de políticas (ADR-35 opção A)

Recuperação nativa (`to_tsvector('portuguese', …)`), sem pgvector/embeddings.
`KnowledgeAccessService.search(tenantId, userId, query)` aplica `tenant_id` nas três tabelas e a
lotação do `IdentityQuery` **antes** do ranking; sem lotação, só documentos `tenant_wide`.

### `POST /api/v1/agente-politicas/perguntas` (`KNOWLEDGE_DOCUMENT:READ`, `BY_TARGET`)

Request:

```json
{ "pergunta": "Posso usar o ChatGPT com dados de cliente?", "requestedModelId": null }
```

Response:

```json
{
  "resposta": "…[1]…",
  "fontes": [
    { "documentId": "…", "titulo": "Política de Uso de IA", "versao": 1, "trecho": "…" }
  ],
  "ferramentasUsadas": ["buscar_politicas"],
  "recibo": { "requestId": "…", "modelo": "…", "provider": "…", "tokensEntrada": 120,
              "tokensSaida": 340, "custoEstimado": 0.000920, "statusCusto": "KNOWN",
              "latenciaMs": 812 }
}
```

- Sem trechos recuperados: o LLM **não** é chamado, `resposta` informa que não encontrou política
  aplicável, `fontes`/`ferramentasUsadas` vazios e `recibo` nulo.
- `AskPolicyAgent` registra `RUNNING` → `COMPLETED`/`FAILED` com `request_id`, `document_ids` e
  `tools_called` (somente metadados; nunca pergunta/resposta). Ferramentas da allowlist:
  `buscar_politicas` e `modelo_permitido`; no máximo 2 rodadas. Ferramenta desconhecida é ignorada
  e registrada.
- Pergunta: 1 a 2000 caracteres. Erros da completion seguem o mapa da seção Completion.

## REST

- prefixo `/api/v1`;
- login: `/api/v1/auth/login`;
- tenant não é parâmetro confiável do cliente;
- sem `/ai/complete` público;
- paginação `page`/`size`, zero-based;
- default 20, máximo 100.

Navegador: BFF intercepta POST `/api/v1/auth/login`, estabelece cookie HttpOnly e
retorna `{authenticated:true}` sem JWT. POST `/api/v1/auth/logout` expira cookie;
GET `/api/v1/auth/session` valida sessão no Spring. Header `X-Qsoft-Request: 1`
e origem válida protegem escritas com cookie (ADR-30 emendado). API Spring direta
mantém Bearer. `/observability/metrics/me/usage` e `/me/costs` restringem dados
ao userId autenticado, independentemente da concessão mais ampla do ator.

## Dashboard histórico (FEAT-DASH-001, identificação local)

Desde V021, TENANT_ADMIN não concede USAGE:READ nem GOVERNANCE_SCORE:READ.
Administrador usa o mesmo login por email/senha e gerencia cadastro, convites
e branding; conta exclusivamente administrativa recebe 403 nas APIs analíticas.
EXECUTIVE mantém escopo TENANT analítico, sem TENANT:MANAGE. Ver emenda ADR-36.

`GET /api/v1/observability/metrics/historical`: autenticação + USAGE:READ com
visibilidade IAM efetiva SELF/DEPARTMENT/TENANT, com vínculos explícitos por tenant.
`GET /api/v1/observability/metrics/historical/me` fixa o usuário autenticado.
Sem vínculo pessoal autorizado retorna 403; nunca amplia para o tenant.
No endpoint geral, userId opcional é UUID atual validado pelo resolvedor de escopo.
from/to ISO date inclusivos; departmentId/modelId são IDs textuais da fonte.
Resposta inclui sourceKind HISTORICAL, USD, período/cobertura, catálogos do tenant,
ativos, série diária e custos por modelo com unpricedRecords. Não soma operação,
não expõe conteúdo/segredos; ver `docs/development/DASHBOARD_E_ORCAMENTOS.md`.
