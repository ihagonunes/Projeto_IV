-- V014 - Schema `historical`: dataset sintético da QSOFT como massa de teste (SPI-75).
-- ADR-26: o histórico analítico é SEPARADO do operacional e NÃO tem FK para as
-- tabelas operacionais (nem internamente). As colunas espelham o dataset bruto
-- (text/bigint/double precision), sem constraints — é massa de teste/analítica.
-- Carregado por scripts/db/load-historical.ps1.

CREATE SCHEMA IF NOT EXISTS historical;

CREATE TABLE historical.companies (
    company_id     text,
    company_name   text,
    industry       text,
    company_size   text,
    employee_count bigint
);

CREATE TABLE historical.departments (
    department_id   text,
    company_id      text,
    department_name text
);

CREATE TABLE historical.employees (
    employee_id   text,
    company_id    text,
    department_id text,
    job_role      text,
    seniority     text,
    hire_date     text,
    cost_center   text,
    active        bigint
);

CREATE TABLE historical.projects (
    project_id   text,
    company_id   text,
    project_name text,
    project_type text,
    start_date   text,
    status       text
);

CREATE TABLE historical.builds (
    build_id       text,
    project_id     text,
    "timestamp"    text,
    status         text,
    duration       bigint,
    tests_executed bigint,
    tests_failed   bigint
);

CREATE TABLE historical.deployments (
    deployment_id text,
    project_id    text,
    "timestamp"   text,
    environment   text,
    status        text
);

CREATE TABLE historical.issues (
    issue_id    text,
    project_id  text,
    employee_id text,
    issue_type  text,
    priority    text,
    created_at  text,
    closed_at   text,
    status      text
);

CREATE TABLE historical.pull_requests (
    pull_request_id text,
    project_id      text,
    employee_id     text,
    created_at      text,
    merged_at       text,
    status          text,
    files_changed   bigint,
    additions       bigint,
    deletions       bigint,
    review_count    double precision
);

CREATE TABLE historical.ai_tools (
    ai_tool_id     text,
    tool_name      text,
    provider       text,
    tool_category  text,
    licensing_type text
);

CREATE TABLE historical.ai_models (
    model_id                        text,
    ai_tool_id                      text,
    model_name                      text,
    model_family                    text,
    input_price_usd_per_1k_tokens   double precision,
    output_price_usd_per_1k_tokens  double precision
);

CREATE TABLE historical.ai_usage_logs (
    usage_id      text,
    "timestamp"   text,
    employee_id   text,
    company_id    text,
    department_id text,
    ai_tool_id    text,
    model_id      text,
    application   text,
    request_count bigint,
    input_tokens  bigint,
    output_tokens bigint,
    latency_ms    double precision,
    request_status text,
    project_id    text,
    cost_center   text
);

CREATE TABLE historical.ai_billing (
    billing_id     text,
    company_id     text,
    billing_month  text,
    provider       text,
    ai_tool_id     text,
    model_id       text,
    cost_center    text,
    usage_quantity bigint,
    total_cost     double precision,
    currency       text
);

CREATE TABLE historical.ai_budgets (
    company_id    text,
    cost_center   text,
    year          bigint,
    month         bigint,
    budget_amount double precision
);

CREATE TABLE historical.ai_licenses (
    company_id      text,
    employee_id     text,
    ai_tool_id      text,
    license_type    text,
    assigned_date   text,
    expiration_date text,
    license_status  text
);

CREATE TABLE historical.approved_ai_tools (
    company_id          text,
    ai_tool_id          text,
    approval_status     text,
    approval_date       text,
    allowed_departments text
);

CREATE TABLE historical.security_events (
    event_id    text,
    company_id  text,
    employee_id text,
    "timestamp" text,
    ai_tool_id  text,
    event_type  text,
    severity    text,
    policy_id   text,
    action_taken text
);

CREATE TABLE historical.training_records (
    training_id       text,
    employee_id       text,
    company_id        text,
    course_name       text,
    course_category   text,
    enrollment_date   text,
    completion_date   text,
    completion_status text,
    score             double precision,
    duration_hours    double precision
);

-- Índices de apoio às consultas analíticas (filtros por empresa/departamento e período).
CREATE INDEX idx_historical_usage_company_ts ON historical.ai_usage_logs (company_id, "timestamp");
CREATE INDEX idx_historical_usage_company_dept ON historical.ai_usage_logs (company_id, department_id);
CREATE INDEX idx_historical_billing_company_month ON historical.ai_billing (company_id, billing_month);
CREATE INDEX idx_historical_employees_company ON historical.employees (company_id);
CREATE INDEX idx_historical_security_company_ts ON historical.security_events (company_id, "timestamp");

COMMENT ON SCHEMA historical IS
    'Histórico analítico (dataset QSOFT). Sem FK operacional (ADR-26); massa de teste/BI (SPI-75).';
