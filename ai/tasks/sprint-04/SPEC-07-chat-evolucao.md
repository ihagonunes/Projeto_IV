# SPEC-07 — Chat: ciclo de vida, recibo durável, modelos do usuário e idempotência (ADR-42)

Base: ADR-16, ADR-38, ADR-39 e **ADR-42**.
Pendências de origem: `docs/development/CHAT_FRONTEND_BACKEND_PENDENCIAS.md` §4.1, §4.2, §4.5 e
§4.6.
Tarefas: S4-C1 e S4-C2 (backend); S4-C3 (UI, com a responsável pelo componente de chat).

> **Rotas:** ficam as existentes, `/api/v1/conversas`. As rotas sugeridas na SPI-140
> (`/api/v1/chat/conversations`) **não** são criadas.

## 1. Migration V022 (`V022__chat_lifecycle_and_idempotency.sql`)

```sql
ALTER TABLE chat_conversations DROP CONSTRAINT ck_chat_conv_status;
ALTER TABLE chat_conversations
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_chat_conv_status CHECK (status IN ('ACTIVE','ARCHIVED','DELETED')),
    ADD CONSTRAINT ck_chat_conv_deleted CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));

ALTER TABLE chat_messages ADD COLUMN client_message_id VARCHAR(64);
CREATE UNIQUE INDEX uq_chat_msg_client_id ON chat_messages (conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

DROP INDEX IF EXISTS idx_chat_conv_owner;
CREATE INDEX idx_chat_conv_owner ON chat_conversations (tenant_id, owner_user_id, status, updated_at DESC, id DESC);
```

- Nomes reais na V018 (conferidos em 27/10): constraint `ck_chat_conv_status` e índice `idx_chat_conv_owner`.
- `client_message_id`: 8 a 64 caracteres, `[A-Za-z0-9_-]`, gerado pelo cliente (UUID v4
  recomendado).

## 2. Contratos novos em `aigateway.api`

```java
public interface AttemptReceiptQuery {
    /** Só devolve tentativas do tenant cujo user_id == owner; ids desconhecidos são omitidos. */
    Map<UUID, ReceiptInfo> receipts(TenantId tenantId, UserId owner, Set<UUID> requestIds);  // máx. 100 ids
}
public record ReceiptInfo(UUID requestId, UUID modelId, String modelName, String providerName,
                          Long inputTokens, Long outputTokens, BigDecimal estimatedCost,
                          String costStatus, Long latencyMs, String executionStatus) {}

// ModelResolutionQuery ganha:
AllowedModels allowedModels(TenantId tenantId, UserId userId);
public record AllowedModels(UUID defaultModelId, List<AllowedModel> models) {}
public record AllowedModel(UUID id, String name, String providerName, boolean available, String unavailableReason) {}
```

- `allowedModels`:
  - usa a política do departamento atual do usuário, resolvida no servidor (ADR-07/08);
  - `available=false` quando o modelo ou o provider estão inativos, com o motivo
    (`MODEL_UNAVAILABLE` ou `PROVIDER_INACTIVE`);
  - sem política → `defaultModelId=null`, `models=[]`.
- `modelName` no recibo vem do catálogo atual, pelo `model_id` da tentativa. Se o modelo foi
  removido → `modelName=null` (a UI mostra "modelo indisponível").

## 3. Endpoints (módulo `chat`)

| Rota | Permissão (BY_TARGET) | Regra |
|---|---|---|
| `GET /api/v1/conversas?status=ACTIVE\|ARCHIVED&q=&page&size` | CHAT:READ | só as do ator; `status` padrão ACTIVE; `q` (≤ 100 chars) filtra o título por `ILIKE` com escape de `%`/`_`; DELETED nunca aparece; ordem `updated_at DESC, id DESC` |
| `GET /api/v1/conversas/{id}/mensagens?page&size` | CHAT:READ | dono = ator, senão 404; cada ASSISTANT ganha `recibo` (via `AttemptReceiptQuery` em lote); paginação ascendente por `created_at`, padrão 50, máximo 200 |
| `GET /api/v1/conversas/modelos` | CHAT:READ | `AllowedModels` do ator |
| `PATCH /api/v1/conversas/{id}` `{ "titulo"?:string, "status"?:"ACTIVE"\|"ARCHIVED" }` | CHAT:CREATE | dono, senão 404; título não branco, ≤ 200; DELETED → 404; corpo vazio → 400 |
| `DELETE /api/v1/conversas/{id}` | CHAT:CREATE | dono, senão 404; soft delete (`DELETED` + `deleted_at`); idempotente: excluída de novo → 404; responde 204 |
| `POST /api/v1/conversas` e `POST /{id}/mensagens` (existentes) | CHAT:CREATE | aceitam `clientMessageId` opcional; conversa ARCHIVED → 409 `CONVERSATION_ARCHIVED` |

Não há permissão nova na matriz: `CHAT:CREATE` cobre escrita e `CHAT:READ` cobre leitura (match
exato, ADR-36). O `TENANT_ADMIN` continua sem CHAT.

### Idempotência do envio (`clientMessageId`)

1. Se já existe uma mensagem USER com o mesmo `(conversation_id, client_message_id)`:
   - se existe a ASSISTANT seguinte com `request_id` → 200 com o **mesmo** turno e o recibo, sem
     nova chamada ao provider;
   - se não existe → 409 `TURN_IN_PROGRESS_OR_FAILED`, com `messageId` e `requestId` (se houver).
2. A corrida entre dois envios iguais é resolvida pelo índice único: o perdedor recebe a regra 1.
3. Na primeira mensagem (`POST /conversas`), a idempotência vale por `(owner, client_message_id)`.
   Implementar com consulta por dono antes de criar a conversa, sem índice novo; registrar a
   limitação de corrida se houver.

### Erros com código estável

O corpo de erro do chat passa a ser `{ "message": "...", "code": "..." }`.

Códigos: `CONVERSATION_NOT_FOUND`, `CONVERSATION_ARCHIVED`, `TURN_IN_PROGRESS_OR_FAILED`,
`MODEL_NOT_ALLOWED`, `NO_MODEL_CONFIGURED`, `PROVIDER_FAILURE`, `PROVIDER_TIMEOUT` e
`ATTEMPT_NOT_RECORDED`.

## 4. Fora de escopo (registrado)

| Item | Motivo |
|---|---|
| Busca no conteúdo das mensagens | Privacidade e custo |
| Streaming/SSE e cancelamento no servidor | Evolução |
| Regenerar resposta | Precisa de decisão de produto sobre versões |
| Anexos | Precisam de ADR |
| Cota pessoal | Decisão S4-D11: o consumo pessoal informativo usa os endpoints existentes `GET /api/v1/observability/metrics/me/usage` e `/me/costs`, sem cota |
| Expurgo do conteúdo excluído | Decisão futura de retenção |

## 5. Testes

| ID | Cenário | Tipo |
|---|---|---|
| CH01 | Outro usuário do mesmo tenant: PATCH/DELETE/GET mensagens → 404; MANAGER e EXECUTIVE → 404; só-`TENANT_ADMIN` → 403 | IT |
| CH02 | Outro tenant → 404 em todas as rotas | IT |
| CH03 | Arquivar → some da lista padrão e aparece em `status=ARCHIVED`; envio → 409; restaurar → envio volta a funcionar | IT |
| CH04 | Excluir → some de todas as listas; GET/PATCH/envio → 404; segundo DELETE → 404 | IT |
| CH05 | Recibo durável: depois de recarregar (`GET mensagens`), cada ASSISTANT traz recibo igual ao da resposta do envio e ao `UsageRecord` (tokens/custo/requestId) | IT |
| CH06 | `AttemptReceiptQuery` não devolve a tentativa de outro dono, mesmo com o `requestId` certo | IT |
| CH07 | `clientMessageId` repetido → mesmo turno, sem nova tentativa (contar linhas em `aigateway_request_attempts`) | IT |
| CH08 | Busca `q` com `%`/`_` é literal; sem vazamento entre donos | IT |
| CH09 | `GET /conversas/modelos`: COLLABORATOR vê só os modelos da política do depto; modelo inativo aparece com `available=false` | IT |
| CH10 | Nenhum conteúdo de mensagem em log (appender de teste) nas rotas novas | IT |
