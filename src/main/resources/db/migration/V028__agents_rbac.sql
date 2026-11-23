-- V028 - RBAC do módulo agents: AGENT:READ, AGENT:MANAGE e AGENT:RUN
-- (ADR-44 / SPI-180), no padrão da V015. Novos tenants são provisionados pelo
-- SystemRole; esta migration alinha os tenants já existentes.
--
-- AGENT:MANAGE não implica AGENT:READ; cada permissão é explícita.

INSERT INTO iam_role_permissions (role_id, resource, action)
SELECT r.id, v.resource, v.action
  FROM iam_roles r
  JOIN (VALUES
        ('COLLABORATOR', 'AGENT', 'READ'),
        ('COLLABORATOR', 'AGENT', 'RUN'),
        ('LEADER',       'AGENT', 'READ'),
        ('LEADER',       'AGENT', 'MANAGE'),
        ('LEADER',       'AGENT', 'RUN'),
        ('MANAGER',      'AGENT', 'READ'),
        ('MANAGER',      'AGENT', 'MANAGE'),
        ('MANAGER',      'AGENT', 'RUN'),
        ('EXECUTIVE',    'AGENT', 'READ'),
        ('EXECUTIVE',    'AGENT', 'RUN'),
        ('TENANT_ADMIN', 'AGENT', 'READ'),
        ('TENANT_ADMIN', 'AGENT', 'MANAGE'),
        ('TENANT_ADMIN', 'AGENT', 'RUN')
       ) AS v(role_name, resource, action)
    ON v.role_name = r.name
ON CONFLICT DO NOTHING;
