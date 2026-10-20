-- V016 - Módulo department: orçamento mensal por departamento (ADR-06/SPEC-02).
-- O department_departments.monthly_budget existente permanece como orçamento PADRÃO
-- (vale para meses sem linha própria; não há backfill).

CREATE TABLE department_monthly_budgets (
    id                  UUID          NOT NULL,
    tenant_id           UUID          NOT NULL,
    department_id       UUID          NOT NULL,
    month               DATE          NOT NULL,
    amount              NUMERIC(15,2) NOT NULL,
    alert_threshold_pct SMALLINT      NOT NULL DEFAULT 80,
    updated_by          UUID          NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_department_monthly_budgets PRIMARY KEY (id),
    CONSTRAINT uq_department_monthly_budgets UNIQUE (tenant_id, department_id, month),
    CONSTRAINT fk_dmb_department FOREIGN KEY (department_id) REFERENCES department_departments (id),
    CONSTRAINT ck_dmb_amount    CHECK (amount >= 0),
    CONSTRAINT ck_dmb_threshold CHECK (alert_threshold_pct BETWEEN 1 AND 100),
    CONSTRAINT ck_dmb_month     CHECK (EXTRACT(DAY FROM month) = 1)
);
CREATE INDEX idx_dmb_tenant_month ON department_monthly_budgets (tenant_id, month);
