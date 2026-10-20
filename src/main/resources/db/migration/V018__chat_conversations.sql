-- V018 - Módulo chat: conversas privadas e mensagens (ADR-39).
-- owner_user_id e request_id são IDs de outros módulos, sem FK (ADR-29).
-- Todas as consultas filtram por tenant_id e owner_user_id.

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
