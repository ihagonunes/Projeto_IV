-- LOCAL-BUDGET-001: teto departamental independente da composição da equipe.
ALTER TABLE department_spending_reservations ADD COLUMN department_id uuid;
UPDATE department_spending_reservations r
SET department_id = (a.value->>'departmentId')::uuid
FROM department_spending_plans p,
     LATERAL jsonb_array_elements(p.plan->'allocations') a(value)
WHERE r.tenant_id=p.tenant_id AND r.month=p.month
  AND a.value->>'userId'=r.user_id::text;
CREATE INDEX idx_spending_department_month
 ON department_spending_reservations(tenant_id,month,department_id);
