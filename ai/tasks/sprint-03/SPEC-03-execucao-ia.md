# SPEC-03 — Provider real, tentativas duráveis e entrega de uso (ADR-38)

> **Status:** ADR-38 ACCEPTED (18/10/2026). O provider concreto da demo (decisão 11) ainda será
> informado pelo responsável. Implemente e teste tudo com o adaptador `STUB`. O
> `OPENAI_COMPATIBLE` é genérico (OpenAI, Groq, Azure OpenAI, Ollama) e só precisa de configuração
> quando o provider for escolhido.

## 1. Migration V017 (`V017__aigateway_provider_config_and_attempts.sql`)

```sql
ALTER TABLE aigateway_providers
    ADD COLUMN kind           VARCHAR(30)  NOT NULL DEFAULT 'STUB',
    ADD COLUMN base_url       VARCHAR(500),
    ADD COLUMN credential_ref VARCHAR(100),
    ADD COLUMN timeout_ms     INTEGER      NOT NULL DEFAULT 30000,
    ADD CONSTRAINT ck_aigw_provider_kind    CHECK (kind IN ('OPENAI_COMPATIBLE','STUB')),
    ADD CONSTRAINT ck_aigw_provider_cred    CHECK (credential_ref IS NULL OR credential_ref ~ '^[A-Z][A-Z0-9_]{2,99}$'),
    ADD CONSTRAINT ck_aigw_provider_timeout CHECK (timeout_ms BETWEEN 1000 AND 120000),
    ADD CONSTRAINT ck_aigw_provider_url     CHECK (kind <> 'OPENAI_COMPATIBLE' OR base_url IS NOT NULL);

ALTER TABLE aigateway_models ADD COLUMN external_name VARCHAR(150);   -- nulo = usa name

CREATE TABLE aigateway_request_attempts (
    request_id           UUID          NOT NULL,
    tenant_id            UUID          NOT NULL,
    user_id              UUID          NOT NULL,
    department_id        UUID,
    purpose              VARCHAR(30)   NOT NULL,           -- CHAT | POLICY_AGENT
    model_id             UUID,
    provider_id          UUID,
    authorization_status VARCHAR(16)   NOT NULL,           -- GRANTED | DENIED
    denial_reason        VARCHAR(30),
    execution_status     VARCHAR(20)   NOT NULL,           -- NOT_STARTED | IN_FLIGHT | SUCCEEDED | FAILED | TIMED_OUT | OUTCOME_UNKNOWN
    delivery_status      VARCHAR(20)   NOT NULL,           -- NOT_APPLICABLE | PENDING | DELIVERED | DEAD
    input_tokens         BIGINT,
    output_tokens        BIGINT,
    cost_status          VARCHAR(20)   NOT NULL,           -- KNOWN | NOT_APPLICABLE | INCONCLUSIVE
    estimated_cost       NUMERIC(15,6),
    unit_price_input     NUMERIC(12,6),
    unit_price_output    NUMERIC(12,6),
    latency_ms           BIGINT,
    error_code           VARCHAR(40),
    delivery_attempts    SMALLINT      NOT NULL DEFAULT 0,
    next_delivery_at     TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_aigw_attempts PRIMARY KEY (request_id),
    CONSTRAINT ck_aigw_att_auth     CHECK (authorization_status IN ('GRANTED','DENIED')),
    CONSTRAINT ck_aigw_att_exec     CHECK (execution_status IN ('NOT_STARTED','IN_FLIGHT','SUCCEEDED','FAILED','TIMED_OUT','OUTCOME_UNKNOWN')),
    CONSTRAINT ck_aigw_att_delivery CHECK (delivery_status IN ('NOT_APPLICABLE','PENDING','DELIVERED','DEAD')),
    CONSTRAINT ck_aigw_att_cost     CHECK (cost_status IN ('KNOWN','NOT_APPLICABLE','INCONCLUSIVE')),
    CONSTRAINT ck_aigw_att_cost_val CHECK ((estimated_cost IS NOT NULL) = (cost_status = 'KNOWN')),
    CONSTRAINT ck_aigw_att_denied   CHECK (authorization_status <> 'DENIED'
        OR (execution_status = 'NOT_STARTED' AND delivery_status = 'NOT_APPLICABLE' AND cost_status = 'NOT_APPLICABLE'))
);
CREATE INDEX idx_aigw_att_tenant_created ON aigateway_request_attempts (tenant_id, created_at DESC);
CREATE INDEX idx_aigw_att_pending ON aigateway_request_attempts (next_delivery_at) WHERE delivery_status = 'PENDING';
CREATE INDEX idx_aigw_att_inflight ON aigateway_request_attempts (created_at) WHERE execution_status = 'IN_FLIGHT';
```

Nenhuma coluna de conteúdo (prompt, resposta, texto de erro do provider). `error_code` é um código
técnico curto (ex.: `HTTP_429`, `TIMEOUT`, `PARSE`).

## 2. Contratos

### 2.1 `aigateway.api` (novo; substitui o uso direto de `CompleteAIRequest`)

```java
public interface AICompletionService {
    CompletionResult complete(CompletionCommand command);
}
public record CompletionCommand(TenantId tenantId, UserId userId, UUID requestedModelId,
                                String purpose, List<CompletionMessage> messages,
                                List<ToolSpec> tools /* pode ser vazio */) {}
public record CompletionMessage(String role /* SYSTEM|USER|ASSISTANT|TOOL */, String content,
                                String toolCallId /* só TOOL */) {}
public record ToolSpec(String name, String description, String jsonSchemaParameters) {}
public record ToolCall(String id, String name, String argumentsJson) {}
public record CompletionResult(UUID requestId, String content, List<ToolCall> toolCalls,
                               UUID modelId, String modelName, UUID providerId, String providerName,
                               Long inputTokens, Long outputTokens, BigDecimal estimatedCost,
                               String costStatus, long latencyMs) {}
```

- O `departmentId` **não** vem do chamador. O gateway resolve a lotação atual pelo `IdentityQuery`
  e aplica a política de modelos (`ResolveModelForUser`, ADR-07/08).
- O `requestId` é sempre gerado pelo servidor.

Exceções públicas, em `aigateway.api.exception`, para o chamador mapear:

| Exceção | HTTP |
|---|---|
| `ModelNotAllowedException` | 403 |
| `NoModelConfiguredException` / `ModelUnavailableException` / `ProviderInactiveException` | 409 |
| `AIProviderTimeoutException` | 504 |
| `AIProviderFailureException` | 502 |
| `AIAttemptNotRecordedException` | 503 |

As exceções de domínio atuais podem ser movidas ou espelhadas. Nenhum outro módulo importa
`aigateway.domain`.

### 2.2 Porta interna `aigateway.application.port.ProviderClient`

```java
ProviderResponse call(ProviderCall call);  // call: provider config + externalModelName + messages + tools + timeout
record ProviderResponse(String content, List<ToolCall> toolCalls, Usage usage, boolean billingDeclaredZero) {}
sealed interface Usage { record Known(long in, long out) implements Usage {} record Unknown() implements Usage {} }
// lança ProviderTimeout / ProviderFailure(errorCode)
```

### 2.3 Adaptadores (`aigateway.infrastructure.provider`)

- **`OpenAiCompatibleProviderClient`**:
  - `RestClient` com `JdkClientHttpRequestFactory` e timeout do provider;
  - chama `POST {base_url}/chat/completions` com `model = external_name ?: name`, `messages` e
    `tools` (formato `type: function`);
  - o header `Authorization: Bearer <segredo>` vem de `CredentialResolver`;
  - o uso vem de `usage.prompt_tokens` / `usage.completion_tokens`; sem `usage` → `Unknown`;
  - **nunca** loga corpo nem header.
- **`EnvCredentialResolver`**: lê `System.getenv(credential_ref)`. Se faltar a variável →
  `ProviderFailure("CREDENTIAL_MISSING")`. Em teste, a resolução deve ser substituível.
- **`StubProviderClient`**: determinístico; se comporta conforme o `external_name` do modelo:

  | `external_name` | Comportamento |
  |---|---|
  | `stub-echo` (padrão) | `"[stub] " + última mensagem USER truncada`; tokens = nº de palavras |
  | `stub-timeout` | lança `ProviderTimeout` |
  | `stub-error` | lança `ProviderFailure("HTTP_500")` |
  | `stub-unknown-usage` | sucesso com `Unknown` |
  | `stub-tool` | 1ª chamada pede a ferramenta `buscar_politicas`; a 2ª responde |

  O stub também grava a última `ProviderCall` em memória, para os testes verificarem o que o LLM
  recebeu (SPEC-05, TR do agente).

## 3. Fluxo (`aigateway.application.CompleteAIRequest` reescrito)

1. Resolver o modelo pela política (ADR-08). Se negar → `DENIED` (motivo; `NOT_STARTED`;
   `NOT_APPLICABLE`) em transação própria; lançar a exceção correspondente.
2. Gravar `GRANTED` + `IN_FLIGHT` + preços fotografados em transação própria (`REQUIRES_NEW`) e
   confirmar. Se falhar → `AIAttemptNotRecordedException` (503) e **não** chamar o provider.
3. Chamar o provider **fora** de transação.
4. Gravar o resultado numa transação:
   - sucesso + `Known` → `SUCCEEDED`, `KNOWN`, custo = preço fotografado, `PENDING`;
   - sucesso + `Unknown` → `SUCCEEDED`, `INCONCLUSIVE`, `NOT_APPLICABLE`;
   - timeout → `TIMED_OUT`, `INCONCLUSIVE`, `NOT_APPLICABLE`;
   - erro → `FAILED`; `KNOWN` com 0 só se `billingDeclaredZero`, senão `INCONCLUSIVE`; entrega
     `PENDING` só se `KNOWN`.
5. Depois do commit, se `PENDING`: `UsageDeliveryPublisher.deliver(requestId)` publica
   `AIRequestCompleted` de forma síncrona. Sucesso → `DELIVERED`. Exceção → `delivery_attempts++`,
   `next_delivery_at = now + min(30s × 2^attempts, 1h)`. **Não** propaga ao cliente.
6. Job `@Scheduled(fixedDelayString = "${app.ai.delivery.interval-ms:30000}")`:
   - `SELECT … WHERE delivery_status='PENDING' AND next_delivery_at <= now() FOR UPDATE SKIP LOCKED LIMIT 50`
     → reentrega; com `delivery_attempts >= ${app.ai.delivery.max-attempts:8}` → `DEAD` + log
     `ERROR` (só o `request_id`);
   - `IN_FLIGHT` com `created_at < now - (timeout + 60s)` → `OUTCOME_UNKNOWN`, `INCONCLUSIVE`.
   - O job exige `@EnableScheduling` (em `platform.config`), desligável por
     `app.ai.delivery.enabled`.
7. No `observability`:
   - `AIRequestCompletedListener` **sem** `try/catch`;
   - `RecordUsage` com `INSERT … ON CONFLICT (request_id) DO NOTHING` (query nativa com
     `tenant_id`), tratando conflito como sucesso.

## 4. Endpoints

| Rota | Permissão | Targeting | Regra |
|---|---|---|---|
| `GET /api/v1/ai-gateway/tentativas?de&ate&status&deliveryStatus&userId&departmentId&page&size` (RF047) | USAGE:READ | BT | `visibilityFor(USAGE:READ)` sobre `user_id`/`department_id`; filtro fora do escopo → 403; só metadados |
| `POST/GET /api/v1/ai-gateway/providers` (existentes) | AI_TOOL:MANAGE | TO | aceitam e devolvem `kind`, `baseUrl`, `credentialRef`, `timeoutMs`; **nunca** o segredo |
| `POST /api/v1/ai-gateway/models` (existente) | AI_TOOL:MANAGE | TO | aceita `externalName` |

Tentativas com `authorization_status = DENIED` aparecem na RF047, só com metadados (decisão 14 —
recomendação; confirmar).

## 5. Testes (TR) — `STUB` e Testcontainers

| ID | Cenário | Esperado |
|---|---|---|
| TR01 | Sucesso (`stub-echo`) | SUCCEEDED/KNOWN/DELIVERED; 1 `UsageRecord` com o mesmo `request_id` |
| TR02 | Modelo fora da política | DENIED/NOT_STARTED/NOT_APPLICABLE; 403; sem `UsageRecord` |
| TR03 | `stub-timeout` | TIMED_OUT/INCONCLUSIVE; 504; dashboard inalterado |
| TR04 | `RecordUsage` falha (bean de teste que lança 1×) | cliente 200; PENDING; após rodar o job → DELIVERED e exatamente 1 registro |
| TR05 | Reentrega duplicada | 1 registro; DELIVERED |
| TR06 | Linha IN_FLIGHT antiga inserida | job → OUTCOME_UNKNOWN/INCONCLUSIVE |
| TR07 | Entrega falha N vezes | DEAD; aparece em `tentativas?deliveryStatus=DEAD` |
| TR08 | 0 tokens × `stub-unknown-usage` × período vazio | agregação distingue os três |
| TR09 | Preço muda depois da tentativa | custo histórico inalterado |
| TR10 | Tentativas: MANAGER(A) e outro tenant | só A; outro tenant vazio |
| TR11 | Schema | nenhuma coluna `content`/`prompt`/`response`/`secret` (consulta a `information_schema`) |
| TR12 | `OpenAiCompatibleProviderClient` contra `MockRestServiceServer`/HTTP local | request correto; `usage` mapeado; header não logado |
