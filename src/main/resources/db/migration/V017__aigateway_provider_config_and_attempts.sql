-- V017 - Módulo aigateway: configuração de provider, external_name e tentativas duráveis (ADR-38).

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
