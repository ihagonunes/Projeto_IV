-- IAM Module - Initial Schema
-- Merged tenant definition (keeps status and TIMESTAMPTZ) and app_user table

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

-- Application User table
CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(255),
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    UNIQUE(tenant_id, email)
);

-- Indices for performance
CREATE INDEX IF NOT EXISTS idx_app_user_email ON app_user(email);
CREATE INDEX IF NOT EXISTS idx_app_user_tenant_id ON app_user(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_slug ON tenant(slug);
CREATE INDEX IF NOT EXISTS idx_tenant_created_at ON tenant(created_at);

