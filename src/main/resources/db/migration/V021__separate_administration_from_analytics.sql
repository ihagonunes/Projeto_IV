-- FEAT-DASH-001: administração não concede leitura analítica.
-- Mantém usuários, concessões e as permissões de cadastro/convite/branding.
DELETE FROM iam_role_permissions p
USING iam_roles r
WHERE p.role_id = r.id AND r.name = 'TENANT_ADMIN'
  AND p.action = 'READ' AND p.resource IN ('USAGE', 'GOVERNANCE_SCORE');
