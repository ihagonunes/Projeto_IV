-- V013 - Módulo observability: UsageRecord operacional (SPI-69/73/76).
-- ADR-13: AIRequestCompleted (emitido pelo Gateway) é a ÚNICA origem operacional
-- de UsageRecord; ele é IMUTÁVEL e IDEMPOTENTE por request_id (UNIQUE).
-- ADR-26: o histórico analítico vive em historical_* (sem FK operacional) — esta
-- tabela é o operacional.
-- ADR-29: tenant_id/department_id/user_id/model_id/provider_id são referências
-- cross-module POR ID, sem FK.
-- Sem prompt/resposta/documento/segredo: apenas metadados, tokens, custo e latência.

CREATE TABLE observability_usage_records (
    id             UUID          NOT NULL,
    request_id     UUID          NOT NULL,
    tenant_id      UUID          NOT NULL,
    department_id  UUID,
    user_id        UUID,
    model_id       UUID,
    provider_id    UUID,
    input_tokens   BIGINT        NOT NULL,
    output_tokens  BIGINT        NOT NULL,
    estimated_cost NUMERIC(15,6) NOT NULL,
    latency_ms     BIGINT,
    status         VARCHAR(30)   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_observability_usage_records PRIMARY KEY (id),
    CONSTRAINT uq_observability_usage_records_request UNIQUE (request_id)
);

-- Índices para os filtros compostos do dashboard/consulta (SPI-76):
-- período + tenant e período + tenant + departamento.
CREATE INDEX idx_usage_records_tenant_created
    ON observability_usage_records (tenant_id, created_at DESC);
CREATE INDEX idx_usage_records_tenant_department_created
    ON observability_usage_records (tenant_id, department_id, created_at DESC);
CREATE INDEX idx_usage_records_tenant_user_created
    ON observability_usage_records (tenant_id, user_id, created_at DESC);

COMMENT ON TABLE observability_usage_records IS
    'UsageRecord operacional (dono: observability). Idempotente por request_id; sem conteúdo privado (ADR-13).';
