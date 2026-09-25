-- V010 - Módulo aigateway: catálogo técnico (Provider e Model) - SPI-79/81.
-- ADR-09: Provider e Model são conceitos distintos; cada Model referencia um Provider.
-- A FK provider_id -> aigateway_providers é INTRAMÓDULO (permitida).
-- tenant_id é referência cross-module por ID (módulo iam), SEM FK (ADR-29).

CREATE TABLE aigateway_providers (
    id         UUID         NOT NULL,
    tenant_id  UUID         NOT NULL,
    name       VARCHAR(150) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_aigateway_providers PRIMARY KEY (id),
    CONSTRAINT uq_aigateway_providers_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_aigateway_providers_tenant ON aigateway_providers (tenant_id);

CREATE TABLE aigateway_models (
    id          UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    provider_id UUID         NOT NULL,
    name        VARCHAR(150) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_aigateway_models PRIMARY KEY (id),
    CONSTRAINT uq_aigateway_models_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_aigateway_models_provider FOREIGN KEY (provider_id)
        REFERENCES aigateway_providers (id)
);

CREATE INDEX idx_aigateway_models_tenant ON aigateway_models (tenant_id);
CREATE INDEX idx_aigateway_models_provider ON aigateway_models (provider_id);

COMMENT ON TABLE aigateway_providers IS 'Fornecedores de IA do tenant (dono: módulo aigateway).';
COMMENT ON TABLE aigateway_models IS 'Modelos do catálogo do tenant; referencia o Provider por FK intramódulo (ADR-09).';
