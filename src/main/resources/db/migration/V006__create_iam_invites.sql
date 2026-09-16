-- V006 - Módulo IAM: convites de usuário (SPI-29/SPI-30).
-- Token aleatório e único, com expiração e status; tenant-aware.
-- department_id é referência cross-module por ID (sem FK).

CREATE TABLE iam_invites (
    id              UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    email           VARCHAR(255) NOT NULL,
    hierarchy_level VARCHAR(20)  NOT NULL,
    department_id   UUID,
    token           VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    accepted_at     TIMESTAMPTZ,

    CONSTRAINT pk_iam_invites PRIMARY KEY (id),
    CONSTRAINT uq_iam_invites_token UNIQUE (token),
    CONSTRAINT fk_iam_invites_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenants (id)
);

CREATE INDEX idx_iam_invites_tenant ON iam_invites (tenant_id);
