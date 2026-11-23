-- V027 - Agents: catálogo global, overrides por tenant, fluxos e versões
-- (ADR-44 / SPI-180). Execução (agents_flow_executions/steps) pertence ao
-- ADR-45 e só entra em V030+.

CREATE TABLE agents_catalog (
    id              UUID         PRIMARY KEY,
    agent_key       VARCHAR(80)  NOT NULL,
    version         INTEGER      NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    category        VARCHAR(60)  NOT NULL,
    prompt_template TEXT         NOT NULL,
    tools_json      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    knowledge_scope JSONB        NOT NULL DEFAULT '{}'::jsonb,
    model_policy    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_agents_catalog UNIQUE (agent_key, version),
    CONSTRAINT ck_agents_catalog_version CHECK (version > 0),
    CONSTRAINT ck_agents_catalog_key CHECK (btrim(agent_key) <> ''),
    CONSTRAINT ck_agents_catalog_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_agents_catalog_category CHECK (btrim(category) <> ''),
    CONSTRAINT ck_agents_catalog_prompt CHECK (btrim(prompt_template) <> ''),
    CONSTRAINT ck_agents_catalog_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_agents_catalog_tools CHECK (jsonb_typeof(tools_json) = 'array'),
    CONSTRAINT ck_agents_catalog_knowledge CHECK (jsonb_typeof(knowledge_scope) = 'object'),
    CONSTRAINT ck_agents_catalog_model CHECK (jsonb_typeof(model_policy) = 'object')
);

CREATE INDEX idx_agents_catalog_status ON agents_catalog (status);

CREATE TABLE agents_catalog_overrides (
    tenant_id        UUID         NOT NULL,
    agent_key        VARCHAR(80)  NOT NULL,
    version          INTEGER      NOT NULL,
    prompt_template  TEXT,
    tools_json       JSONB,
    knowledge_scope  JSONB,
    model_policy     JSONB,
    status           VARCHAR(20),
    updated_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_agents_catalog_overrides PRIMARY KEY (tenant_id, agent_key, version),
    CONSTRAINT fk_agents_override_catalog FOREIGN KEY (agent_key, version)
        REFERENCES agents_catalog (agent_key, version),
    CONSTRAINT ck_agents_override_version CHECK (version > 0),
    CONSTRAINT ck_agents_override_key CHECK (btrim(agent_key) <> ''),
    CONSTRAINT ck_agents_override_prompt CHECK (prompt_template IS NULL OR btrim(prompt_template) <> ''),
    CONSTRAINT ck_agents_override_status
        CHECK (status IS NULL OR status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_agents_override_tools
        CHECK (tools_json IS NULL OR jsonb_typeof(tools_json) = 'array'),
    CONSTRAINT ck_agents_override_knowledge
        CHECK (knowledge_scope IS NULL OR jsonb_typeof(knowledge_scope) = 'object'),
    CONSTRAINT ck_agents_override_model
        CHECK (model_policy IS NULL OR jsonb_typeof(model_policy) = 'object')
);

CREATE TABLE agents_flows (
    id                 UUID         PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    owner_user_id      UUID         NOT NULL,
    department_id      UUID,
    name               VARCHAR(120) NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    current_version_id UUID,
    row_version        BIGINT       NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT ck_agents_flow_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_agents_flow_row_version CHECK (row_version > 0),
    CONSTRAINT ck_agents_flow_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_agents_flow_published_version
        CHECK (status <> 'PUBLISHED' OR current_version_id IS NOT NULL)
);

CREATE INDEX idx_agents_flow_owner
    ON agents_flows (tenant_id, owner_user_id, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_agents_flow_department
    ON agents_flows (tenant_id, department_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_agents_flow_tenant_updated
    ON agents_flows (tenant_id, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE agents_flow_versions (
    id                UUID         PRIMARY KEY,
    flow_id           UUID         NOT NULL,
    version_number    INTEGER      NOT NULL,
    schema_version    INTEGER      NOT NULL,
    graph_json        JSONB        NOT NULL,
    validation_status VARCHAR(20)  NOT NULL,
    created_by        UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_agents_flow_version_flow FOREIGN KEY (flow_id) REFERENCES agents_flows (id),
    CONSTRAINT uq_agents_flow_version UNIQUE (flow_id, version_number),
    CONSTRAINT ck_agents_flow_version_number CHECK (version_number > 0),
    CONSTRAINT ck_agents_flow_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_agents_flow_validation CHECK (validation_status IN ('VALID', 'INVALID')),
    CONSTRAINT ck_agents_flow_graph CHECK (jsonb_typeof(graph_json) = 'object')
);
