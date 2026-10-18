# SPEC-04 — Chat privado e recibo (ADR-39)

Depende de: SPEC-01 (autorização com alvo e teto `CHAT = SELF`) e SPEC-03 (`AICompletionService`).
UI: SPI-136 (Lyn). Este backend é o contrato que a UI consome. **Não alterar arquivos do componente
de chat do front sem combinar com a Lyn.**

## 1. Migration V018 (`V018__chat_conversations.sql`, módulo `chat`)

```sql
CREATE TABLE chat_conversations (
    id            UUID         NOT NULL,
    tenant_id     UUID         NOT NULL,
    owner_user_id UUID         NOT NULL,
    title         VARCHAR(200) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_chat_conversations PRIMARY KEY (id),
    CONSTRAINT ck_chat_conv_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE INDEX idx_chat_conv_owner ON chat_conversations (tenant_id, owner_user_id, updated_at DESC);

CREATE TABLE chat_messages (
    id              UUID        NOT NULL,
    tenant_id       UUID        NOT NULL,
    conversation_id UUID        NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL,
    request_id      UUID,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id),
    CONSTRAINT ck_chat_msg_role CHECK (role IN ('USER','ASSISTANT'))
);
CREATE INDEX idx_chat_msg_conversation ON chat_messages (conversation_id, created_at);
```

- `owner_user_id` e `request_id` são IDs de outros módulos, sem FK.
- Todas as queries filtram por `tenant_id` **e** `owner_user_id`.

## 2. Domínio e casos de uso (`chat`)

- `Conversation.start(tenant, owner, firstMessage)`: o título são os primeiros 60 caracteres da
  primeira mensagem.
- `Message`: conteúdo de 1 a 8000 caracteres.
- `SendMessage`:
  1. carrega a conversa por `(tenant, id, owner = ator)`; se não achar → 404;
  2. grava a mensagem USER (transação 1);
  3. monta o histórico (últimas 20 mensagens) e chama `AICompletionService` com `purpose=CHAT`
     **fora** de transação;
  4. grava a mensagem ASSISTANT com `request_id` (transação 2) e devolve a resposta e o recibo.
  - Se a completion falhar, a mensagem USER fica e nenhuma ASSISTANT é criada. O erro é mapeado
    conforme SPEC-03 §2.1.
- Conteúdo nunca vai para log nem evento.

## 3. Endpoints

| Rota | Permissão | Targeting | Regra |
|---|---|---|---|
| `POST /api/v1/conversas` `{ "mensagem":"…", "requestedModelId":null }` | CHAT:CREATE | BT | cria a conversa e envia a 1ª mensagem; 201 com conversa, mensagens e recibo |
| `GET /api/v1/conversas?page&size` | CHAT:READ | BT | só as do ator, ordenadas por `updated_at` desc |
| `GET /api/v1/conversas/{id}/mensagens` | CHAT:READ | BT | dono = ator, senão 404 |
| `POST /api/v1/conversas/{id}/mensagens` `{ "mensagem":"…", "requestedModelId":null }` | CHAT:CREATE | BT | dono = ator, senão 404 |

O `TENANT_ADMIN` não tem `CHAT:*`: um usuário que é só admin recebe 403 no gate. O teto SELF
garante que nenhum escopo amplia o acesso (AZ12).

Recibo (SPI-134), no corpo do envio:

```json
"recibo": { "requestId":"…", "modelo":"…", "provider":"…", "tokensEntrada":120,
            "tokensSaida":340, "custoEstimado":0.000920, "statusCusto":"KNOWN", "latenciaMs":812 }
```

## 4. Testes

- **IT AZ12:** EXECUTIVE (escopo TENANT) e MANAGER do mesmo depto tentam
  `GET /conversas/{id}/mensagens` de outro usuário → 404. Usuário só `TENANT_ADMIN` → 403.
- **IT:** fluxo completo com o `STUB`:
  - o recibo bate com a tentativa e com o `UsageRecord` (mesmo `request_id`, tokens e custo);
  - falha do provider (`stub-error`) → 502 e a mensagem USER persistida, sem ASSISTANT.
- **IT:** isolamento de tenant e paginação.
- **Unit:** validação do tamanho da mensagem e do título.
- **ArchUnit:** a regra de allowlist já cobre `chat → aigateway.api` / `iam.api`.
