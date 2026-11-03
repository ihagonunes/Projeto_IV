-- V022 - chat: ciclo de vida, busca e idempotência (ADR-42/SPEC-07).
-- O conteúdo excluído permanece no banco; não há expurgo nesta sprint (S4-D7).

ALTER TABLE chat_conversations DROP CONSTRAINT ck_chat_conv_status;
ALTER TABLE chat_conversations
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_chat_conv_status CHECK (status IN ('ACTIVE','ARCHIVED','DELETED')),
    ADD CONSTRAINT ck_chat_conv_deleted CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));

ALTER TABLE chat_messages ADD COLUMN client_message_id VARCHAR(64);
CREATE UNIQUE INDEX uq_chat_msg_client_id ON chat_messages (conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

DROP INDEX IF EXISTS idx_chat_conv_owner;
CREATE INDEX idx_chat_conv_owner
    ON chat_conversations (tenant_id, owner_user_id, status, updated_at DESC, id DESC);
