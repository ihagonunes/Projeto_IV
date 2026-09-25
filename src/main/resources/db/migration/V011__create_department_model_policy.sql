-- V011 - Módulo department: ModelPolicy (modelos permitidos por departamento) - SPI-80.
-- ADR-07: a política de modelos pertence ao department.
-- ADR-08: se houver modelos permitidos, o default é obrigatório e pertence ao conjunto;
--         conjunto vazio implica default nulo.
-- ADR-29: allowed_model_ids/default_model_id são referências CROSS-MODULE por ID
--         (módulo aigateway), SEM FK. A FK da tabela de itens é intramódulo.

CREATE TABLE department_model_policies (
    department_id    UUID        NOT NULL,
    default_model_id UUID,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_department_model_policies PRIMARY KEY (department_id)
);

CREATE TABLE department_model_policy_allowed_models (
    department_id UUID NOT NULL,
    model_id      UUID NOT NULL,

    CONSTRAINT pk_department_model_policy_allowed_models PRIMARY KEY (department_id, model_id),
    CONSTRAINT fk_dmpam_policy FOREIGN KEY (department_id)
        REFERENCES department_model_policies (department_id)
);

COMMENT ON TABLE department_model_policies IS
    'ModelPolicy do departamento (ADR-07): default_model_id e conjunto de modelos permitidos.';
