-- V002 - Módulo IAM: tabela de usuários.
-- app_user (evita a palavra reservada "user" no PostgreSQL).
-- department_id é referência cross-module persistida apenas como ID (sem FK), conforme ADR-05/ADR-29.
-- FK apenas intramodule: app_user.tenant_id -> tenant(id).

CREATE TABLE app_user (
    id              UUID        NOT NULL,
    tenant_id       UUID        NOT NULL,
    email           VARCHAR(255) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    hierarchy_level VARCHAR(20)  NOT NULL,
    department_id   UUID,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT fk_app_user_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenants (id)
);

CREATE INDEX idx_app_user_tenant_id ON app_user (tenant_id);
