-- V023 - módulo engagement: licenças, treinamentos e snapshots de AI Credits (ADR-17/18/19/40).
-- Referências a iam/aigateway por ID, sem FK cross-module (ADR-29).

CREATE TABLE engagement_ai_licenses (
    id            UUID         NOT NULL,
    tenant_id     UUID         NOT NULL,
    user_id       UUID         NOT NULL,
    ai_tool_id    UUID,
    license_type  VARCHAR(40)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    assigned_at   DATE         NOT NULL,
    expires_at    DATE,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_engagement_ai_licenses PRIMARY KEY (id),
    CONSTRAINT ck_eng_lic_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
    CONSTRAINT ck_eng_lic_dates  CHECK (expires_at IS NULL OR expires_at >= assigned_at)
);
CREATE UNIQUE INDEX uq_eng_lic_active ON engagement_ai_licenses
    (tenant_id, user_id, COALESCE(ai_tool_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status = 'ACTIVE';
CREATE INDEX idx_eng_lic_tenant ON engagement_ai_licenses (tenant_id, status);

CREATE TABLE engagement_training_records (
    id              UUID          NOT NULL,
    tenant_id       UUID          NOT NULL,
    user_id         UUID          NOT NULL,
    course_name     VARCHAR(200)  NOT NULL,
    course_category VARCHAR(80),
    status          VARCHAR(20)   NOT NULL,
    enrolled_at     DATE,
    completed_at    DATE,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_engagement_training_records PRIMARY KEY (id),
    CONSTRAINT ck_eng_tr_status CHECK (status IN ('ENROLLED','COMPLETED','DROPPED')),
    CONSTRAINT ck_eng_tr_completed CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);
CREATE INDEX idx_eng_tr_tenant_user ON engagement_training_records (tenant_id, user_id);

CREATE TABLE engagement_credit_snapshots (
    tenant_id              UUID          NOT NULL,
    user_id                UUID          NOT NULL,
    period                 DATE          NOT NULL,
    source_kind            VARCHAR(16)   NOT NULL,
    calculation_version    VARCHAR(20)   NOT NULL,
    usage_component        NUMERIC(5,2),
    training_component     NUMERIC(5,2),
    license_component      NUMERIC(5,2),
    license_applicability  VARCHAR(16)   NOT NULL,
    score                  NUMERIC(5,2)  NOT NULL,
    data_status            VARCHAR(20)   NOT NULL,
    closed_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_eng_credit_snapshots
        PRIMARY KEY (tenant_id, user_id, period, source_kind, calculation_version),
    CONSTRAINT ck_eng_cs_month CHECK (EXTRACT(DAY FROM period) = 1),
    CONSTRAINT ck_eng_cs_source CHECK (source_kind IN ('OPERACIONAL','HISTORICO')),
    CONSTRAINT ck_eng_cs_range CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_eng_cs_lic CHECK ((license_applicability = 'NOT_APPLICABLE') = (license_component IS NULL))
);
CREATE INDEX idx_eng_cs_tenant_period
    ON engagement_credit_snapshots (tenant_id, period, source_kind);
