-- IAM Module - Initial Schema
-- V001 cria a raiz do multi-tenancy: a tabela iam_tenants.
-- app_user é criado pela V002 (schema canônico da entidade UserJpaEntity),
-- cuja FK referencia iam_tenants.

CREATE TABLE IF NOT EXISTS iam_tenants (
    id         UUID         NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_iam_tenants PRIMARY KEY (id),
    CONSTRAINT uq_iam_tenants_slug UNIQUE (slug)
);

-- Indices for performance
CREATE INDEX IF NOT EXISTS idx_iam_tenants_slug ON iam_tenants(slug);
CREATE INDEX IF NOT EXISTS idx_iam_tenants_created_at ON iam_tenants(created_at);
