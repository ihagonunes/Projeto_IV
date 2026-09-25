-- V009 - Módulo department: departamentos (base organizacional, pré-requisito do POPROJ-21).
-- tenant_id é referência CROSS-MODULE por ID (o agregado Tenant pertence ao módulo iam):
-- o ADR-29 é explícito — "FKs apenas intramodule; cross-module por IDs/ports" — portanto
-- NÃO há FOREIGN KEY aqui. O isolamento é garantido pela aplicação (predicado explícito
-- por tenant_id) + índice.
-- leader_user_id também é referência cross-module por ID, sem FK (ADR-29).
-- status permite remoção LÓGICA (evita referência órfã em app_user.department_id).

CREATE TABLE department_departments (
    id             UUID          NOT NULL,
    tenant_id      UUID          NOT NULL,
    name           VARCHAR(150)  NOT NULL,
    description    VARCHAR(500),
    leader_user_id UUID,
    monthly_budget NUMERIC(15,2),
    status         VARCHAR(20)   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_department_departments PRIMARY KEY (id),
    CONSTRAINT uq_department_departments_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_department_departments_tenant ON department_departments (tenant_id);

COMMENT ON TABLE department_departments IS
    'Departamentos do tenant (dono: módulo department). tenant_id sem FK cross-module (ADR-29).';
COMMENT ON COLUMN department_departments.monthly_budget IS
    'BudgetLimit mensal do departamento (ADR-06). Nulo = sem teto definido.';
