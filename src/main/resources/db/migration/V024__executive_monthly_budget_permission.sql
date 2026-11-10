-- SPI-121: orçamento mensal é gerido pelo executivo no próprio tenant.
INSERT INTO iam_role_permissions (role_id, resource, action)
SELECT id, 'DEPARTMENT_BUDGET', 'MANAGE'
FROM iam_roles
WHERE name = 'EXECUTIVE'
ON CONFLICT DO NOTHING;
