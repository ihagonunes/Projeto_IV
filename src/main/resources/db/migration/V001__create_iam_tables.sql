-- V001 - Módulo IAM: tabela de tenants (organizações).
-- Tenant é a raiz do multi-tenancy; por isso não possui tenant_id próprio.

CREATE TABLE tenant (
    id         UUID         NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_slug UNIQUE (slug)
);

CREATE INDEX idx_tenant_created_at ON tenant (created_at);
