-- IAM Module - Initial Schema
-- V001 cria somente a raiz do multi-tenancy: a tabela tenant.
-- app_user é criado pela V002 (schema canônico da entidade UserJpaEntity).

CREATE TABLE IF NOT EXISTS tenant (
    id         UUID         NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_slug UNIQUE (slug)
);

-- Indices for performance
CREATE INDEX IF NOT EXISTS idx_tenant_slug ON tenant(slug);
CREATE INDEX IF NOT EXISTS idx_tenant_created_at ON tenant(created_at);
