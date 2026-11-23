-- V030 - Execução determinística de pipelines de agentes (ADR-45 / SPI-180).
-- Não guarda prompt nem resposta de etapa; `result_text` é o resultado final
-- (retenção é decisão de produto) e o custo é derivado dos recibos do aigateway.

CREATE TABLE agents_flow_executions (
    id               UUID         PRIMARY KEY,
    tenant_id        UUID         NOT NULL,
    flow_id          UUID         NOT NULL,
    flow_version_id  UUID         NOT NULL,
    owner_user_id    UUID         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    idempotency_key  VARCHAR(80)  NOT NULL,
    result_text      TEXT,
    error_code       VARCHAR(60),
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_agents_execution_flow FOREIGN KEY (flow_id) REFERENCES agents_flows (id),
    CONSTRAINT fk_agents_execution_version FOREIGN KEY (flow_version_id)
        REFERENCES agents_flow_versions (id),
    CONSTRAINT uq_agents_execution_idem UNIQUE (tenant_id, owner_user_id, idempotency_key),
    CONSTRAINT ck_agents_execution_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED')),
    CONSTRAINT ck_agents_execution_idem_key CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_agents_execution_finished
        CHECK ((status IN ('PENDING', 'RUNNING')) = (finished_at IS NULL))
);

CREATE INDEX idx_agents_execution_flow
    ON agents_flow_executions (tenant_id, flow_id, created_at DESC);
CREATE INDEX idx_agents_execution_owner
    ON agents_flow_executions (tenant_id, owner_user_id, created_at DESC);

CREATE TABLE agents_flow_steps (
    id            UUID        PRIMARY KEY,
    execution_id  UUID        NOT NULL,
    node_id       VARCHAR(80) NOT NULL,
    node_kind     VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    request_id    UUID,
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    error_code    VARCHAR(60),
    position      INTEGER     NOT NULL,

    CONSTRAINT fk_agents_step_execution FOREIGN KEY (execution_id)
        REFERENCES agents_flow_executions (id) ON DELETE CASCADE,
    CONSTRAINT uq_agents_step UNIQUE (execution_id, node_id),
    CONSTRAINT ck_agents_step_kind CHECK (node_kind IN ('task', 'agent', 'merge', 'result')),
    CONSTRAINT ck_agents_step_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'SKIPPED')),
    CONSTRAINT ck_agents_step_node CHECK (btrim(node_id) <> ''),
    CONSTRAINT ck_agents_step_position CHECK (position >= 0)
);

CREATE INDEX idx_agents_step_execution ON agents_flow_steps (execution_id);
