-- V019 - Módulo agentpolicies: conhecimento com busca nativa (ADR-35 opção A).
-- Sem extensão: to_tsvector('portuguese', ...) é nativo do PostgreSQL.

CREATE TABLE agentpolicies_documents (
    id          UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    title       VARCHAR(200) NOT NULL,
    tenant_wide BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_ap_documents PRIMARY KEY (id),
    CONSTRAINT uq_ap_documents_tenant_title UNIQUE (tenant_id, title)
);
CREATE TABLE agentpolicies_document_scopes (
    document_id   UUID NOT NULL,
    tenant_id     UUID NOT NULL,
    department_id UUID NOT NULL,
    CONSTRAINT pk_ap_document_scopes PRIMARY KEY (document_id, department_id),
    CONSTRAINT fk_ap_scopes_document FOREIGN KEY (document_id) REFERENCES agentpolicies_documents (id)
);
CREATE TABLE agentpolicies_document_versions (
    id             UUID        NOT NULL,
    tenant_id      UUID        NOT NULL,
    document_id    UUID        NOT NULL,
    version_number INTEGER     NOT NULL,
    status         VARCHAR(20) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ap_versions PRIMARY KEY (id),
    CONSTRAINT uq_ap_versions_number UNIQUE (document_id, version_number),
    CONSTRAINT fk_ap_versions_document FOREIGN KEY (document_id) REFERENCES agentpolicies_documents (id),
    CONSTRAINT ck_ap_versions_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','ACTIVE','SUPERSEDED'))
);
CREATE UNIQUE INDEX uq_ap_versions_one_active ON agentpolicies_document_versions (document_id) WHERE status = 'ACTIVE';
CREATE TABLE agentpolicies_chunks (
    id            UUID    NOT NULL,
    tenant_id     UUID    NOT NULL,
    version_id    UUID    NOT NULL,
    ordinal       INTEGER NOT NULL,
    text          TEXT    NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('portuguese', text)) STORED,
    CONSTRAINT pk_ap_chunks PRIMARY KEY (id),
    CONSTRAINT fk_ap_chunks_version FOREIGN KEY (version_id) REFERENCES agentpolicies_document_versions (id)
);
CREATE INDEX idx_ap_chunks_search ON agentpolicies_chunks USING GIN (search_vector);
CREATE INDEX idx_ap_chunks_tenant ON agentpolicies_chunks (tenant_id);
CREATE TABLE agentpolicies_executions (
    id            UUID        NOT NULL,
    tenant_id     UUID        NOT NULL,
    user_id       UUID        NOT NULL,
    request_id    UUID,
    status        VARCHAR(20) NOT NULL,
    document_ids  UUID[]      NOT NULL DEFAULT '{}',
    tools_called  VARCHAR(60)[] NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ,
    CONSTRAINT pk_ap_executions PRIMARY KEY (id),
    CONSTRAINT ck_ap_exec_status CHECK (status IN ('RUNNING','COMPLETED','FAILED'))
);
CREATE INDEX idx_ap_exec_tenant_created ON agentpolicies_executions (tenant_id, created_at DESC);
