"use strict";

// BUGFIX-DB-001. Contrato da V014; dados históricos não são entidades operacionais.
const fields = (text, integers = "", floats = "") => Object.fromEntries([
    ...text.split(" ").filter(Boolean).map(name => [name, "text"]),
    ...integers.split(" ").filter(Boolean).map(name => [name, "bigint"]),
    ...floats.split(" ").filter(Boolean).map(name => [name, "double precision"])
]);
const tables = {
    companies: fields("company_id company_name industry company_size", "employee_count"),
    departments: fields("department_id company_id department_name"),
    employees: fields("employee_id company_id department_id job_role seniority hire_date cost_center", "active"),
    projects: fields("project_id company_id project_name project_type start_date status"),
    builds: fields("build_id project_id timestamp status", "duration tests_executed tests_failed"),
    deployments: fields("deployment_id project_id timestamp environment status"),
    issues: fields("issue_id project_id employee_id issue_type priority created_at closed_at status"),
    pull_requests: fields("pull_request_id project_id employee_id created_at merged_at status", "files_changed additions deletions", "review_count"),
    ai_tools: fields("ai_tool_id tool_name provider tool_category licensing_type"),
    ai_models: fields("model_id ai_tool_id model_name model_family", "", "input_price_usd_per_1k_tokens output_price_usd_per_1k_tokens"),
    ai_usage_logs: fields("usage_id timestamp employee_id company_id department_id ai_tool_id model_id application request_status project_id cost_center", "request_count input_tokens output_tokens", "latency_ms"),
    ai_billing: fields("billing_id company_id billing_month provider ai_tool_id model_id cost_center currency", "usage_quantity", "total_cost"),
    ai_budgets: fields("company_id cost_center", "year month", "budget_amount"),
    ai_licenses: fields("company_id employee_id ai_tool_id license_type assigned_date expiration_date license_status"),
    approved_ai_tools: fields("company_id ai_tool_id approval_status approval_date allowed_departments"),
    security_events: fields("event_id company_id employee_id timestamp ai_tool_id event_type severity policy_id action_taken"),
    training_records: fields("training_id employee_id company_id course_name course_category enrollment_date completion_date completion_status", "", "score duration_hours")
};
const dateTimes = new Set(["timestamp", "created_at", "closed_at", "merged_at"]);
const dates = new Set(["hire_date", "start_date", "assigned_date", "expiration_date", "approval_date", "enrollment_date", "completion_date"]);
module.exports = { tables, dateTimes, dates };
