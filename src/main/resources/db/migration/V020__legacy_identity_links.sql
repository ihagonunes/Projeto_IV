-- FEAT-DASH-001: vínculos explícitos; nenhuma FK para o schema histórico.
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_tenant_identity UNIQUE (tenant_id, id);
CREATE TABLE iam_legacy_user_links (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    legacy_employee_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (tenant_id, user_id),
    UNIQUE (tenant_id, legacy_employee_id),
    FOREIGN KEY (tenant_id, user_id) REFERENCES app_user (tenant_id, id)
);

ALTER TABLE department_departments ADD CONSTRAINT uq_department_tenant_identity UNIQUE (tenant_id, id);
CREATE TABLE department_legacy_links (
    tenant_id UUID NOT NULL,
    department_id UUID NOT NULL,
    legacy_department_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (tenant_id, department_id),
    UNIQUE (tenant_id, legacy_department_id),
    FOREIGN KEY (tenant_id, department_id) REFERENCES department_departments (tenant_id, id)
);

CREATE INDEX idx_historical_usage_employee_period ON historical.ai_usage_logs (company_id, employee_id, "timestamp");
CREATE INDEX idx_historical_usage_department_period ON historical.ai_usage_logs (company_id, department_id, "timestamp");
