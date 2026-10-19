-- V015 - IAM: concessões com escopo explícito (ADR-36/ADR-37).

-- 0) Abortos (ambiguidade é decidida por pessoa, nunca por regra permissiva)
DO $$
DECLARE n INTEGER;
BEGIN
    SELECT count(*) INTO n FROM iam_roles
     WHERE name NOT IN ('COLLABORATOR','LEADER','MANAGER','EXECUTIVE','TENANT_ADMIN');
    IF n > 0 THEN RAISE EXCEPTION 'V015: % papel(is) fora dos papéis de sistema.', n; END IF;

    SELECT count(*) INTO n FROM (
        SELECT user_id FROM user_permission GROUP BY user_id
        HAVING count(DISTINCT permission) BETWEEN 1 AND 8) p;
    IF n > 0 THEN RAISE EXCEPTION 'V015: % usuário(s) com permissões legadas parciais.', n; END IF;

    SELECT count(*) INTO n FROM iam_role_assignments a JOIN iam_roles r ON r.id = a.role_id
     WHERE (r.name IN ('LEADER','MANAGER') AND a.department_id IS NULL)
        OR (r.name IN ('COLLABORATOR','EXECUTIVE','TENANT_ADMIN') AND a.department_id IS NOT NULL);
    IF n > 0 THEN RAISE EXCEPTION 'V015: % atribuição(ões) com departamento incompatível com o papel.', n; END IF;

    SELECT count(*) INTO n FROM iam_role_assignments a JOIN iam_roles r ON r.id = a.role_id
     WHERE r.tenant_id <> a.tenant_id;
    IF n > 0 THEN RAISE EXCEPTION 'V015: % atribuição(ões) com papel de outro tenant.', n; END IF;
END $$;

-- 1) Papéis: só papéis de sistema; todos os 5 em todo tenant
ALTER TABLE iam_roles
    ADD CONSTRAINT ck_iam_roles_system_name
        CHECK (name IN ('COLLABORATOR','LEADER','MANAGER','EXECUTIVE','TENANT_ADMIN')),
    ADD CONSTRAINT uq_iam_roles_tenant_id UNIQUE (tenant_id, id);

INSERT INTO iam_roles (id, tenant_id, name, created_at, updated_at)
SELECT gen_random_uuid(), t.id, k.name, now(), now()
  FROM iam_tenants t
 CROSS JOIN (VALUES ('COLLABORATOR'),('LEADER'),('MANAGER'),('EXECUTIVE'),('TENANT_ADMIN')) AS k(name)
 WHERE NOT EXISTS (SELECT 1 FROM iam_roles r WHERE r.tenant_id = t.id AND r.name = k.name);

-- 2) Matriz nova (substitui a anterior; não há papéis customizados)
DELETE FROM iam_role_permissions;

WITH ladder(resource, action, min_rank) AS (VALUES
        ('CHAT','CREATE',1),('CHAT','READ',1),('KNOWLEDGE_DOCUMENT','READ',1),
        ('USAGE','READ',1),('MODEL','READ',1),
        ('USER','READ',2),('GOVERNANCE_SCORE','READ',2),
        ('USER','MANAGE',3),('INVITE','MANAGE',3),('ROLE','READ',3),('DEPARTMENT','READ',3),
        ('PROJECT','MANAGE',3),('AI_TOOL','READ',3),
        ('TENANT','READ',4)),
     ranks(name, r) AS (VALUES ('COLLABORATOR',1),('LEADER',2),('MANAGER',3),('EXECUTIVE',4))
INSERT INTO iam_role_permissions (role_id, resource, action)
SELECT ro.id, l.resource, l.action
  FROM iam_roles ro
  JOIN ranks k ON k.name = ro.name
  JOIN ladder l ON l.min_rank <= k.r;

INSERT INTO iam_role_permissions (role_id, resource, action)
SELECT ro.id, p.resource, p.action
  FROM iam_roles ro
 CROSS JOIN (VALUES
        ('TENANT','READ'),('TENANT','MANAGE'),('ROLE','READ'),('ROLE','MANAGE'),
        ('USER','READ'),('USER','MANAGE'),('INVITE','READ'),('INVITE','MANAGE'),
        ('DEPARTMENT','READ'),('DEPARTMENT','MANAGE'),('AI_TOOL','READ'),('AI_TOOL','MANAGE'),
        ('MODEL','READ'),('MODEL','MANAGE'),
        ('KNOWLEDGE_DOCUMENT','READ'),('KNOWLEDGE_DOCUMENT','MANAGE'),
        ('LICENSE','MANAGE'),('TRAINING','MANAGE'),('USAGE','READ'),('GOVERNANCE_SCORE','READ')
     ) AS p(resource, action)
 WHERE ro.name = 'TENANT_ADMIN';

-- 3) Concessões: escopo, origem, autoria, revogação
ALTER TABLE iam_role_assignments
    ADD COLUMN scope_type VARCHAR(16),
    ADD COLUMN origin     VARCHAR(16),
    ADD COLUMN granted_by UUID,
    ADD COLUMN granted_at TIMESTAMPTZ,
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revoked_by UUID;

UPDATE iam_role_assignments a
   SET scope_type = CASE r.name WHEN 'COLLABORATOR' THEN 'SELF'
                                WHEN 'LEADER'       THEN 'DEPARTMENT'
                                WHEN 'MANAGER'      THEN 'DEPARTMENT'
                                ELSE 'TENANT' END,
       origin = 'MIGRATION',
       granted_at = now()
  FROM iam_roles r
 WHERE r.id = a.role_id;

-- Admins legados (as 9 permissões do V004) viram TENANT_ADMIN
INSERT INTO iam_role_assignments
       (id, tenant_id, user_id, role_id, department_id, scope_type, origin, granted_at)
SELECT gen_random_uuid(), u.tenant_id, u.id, r.id, NULL, 'TENANT', 'MIGRATION', now()
  FROM app_user u
  JOIN iam_roles r ON r.tenant_id = u.tenant_id AND r.name = 'TENANT_ADMIN'
 WHERE u.id IN (SELECT user_id FROM user_permission
                 GROUP BY user_id HAVING count(DISTINCT permission) = 9);

ALTER TABLE iam_role_assignments
    ALTER COLUMN scope_type SET NOT NULL,
    ALTER COLUMN origin     SET NOT NULL,
    ALTER COLUMN granted_at SET NOT NULL,
    ALTER COLUMN granted_at SET DEFAULT now(),
    ADD CONSTRAINT ck_iam_ra_scope  CHECK (scope_type IN ('SELF','DEPARTMENT','TENANT')),
    ADD CONSTRAINT ck_iam_ra_origin CHECK (origin IN ('BOOTSTRAP','INVITE','DIRECT','MIGRATION')),
    ADD CONSTRAINT ck_iam_ra_dept   CHECK ((scope_type = 'DEPARTMENT') = (department_id IS NOT NULL)),
    ADD CONSTRAINT ck_iam_ra_revoke CHECK ((revoked_at IS NULL) = (revoked_by IS NULL)),
    ADD CONSTRAINT fk_iam_ra_role_same_tenant FOREIGN KEY (tenant_id, role_id)
        REFERENCES iam_roles (tenant_id, id),
    ADD CONSTRAINT fk_iam_ra_granted_by FOREIGN KEY (granted_by) REFERENCES app_user (id),
    ADD CONSTRAINT fk_iam_ra_revoked_by FOREIGN KEY (revoked_by) REFERENCES app_user (id);

CREATE UNIQUE INDEX uq_iam_ra_active ON iam_role_assignments
    (tenant_id, user_id, role_id, COALESCE(department_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE revoked_at IS NULL;

-- 4) Convites carregam a concessão; token só como hash; pendentes antigos revogados
ALTER TABLE iam_invites
    ADD COLUMN role_id    UUID,
    ADD COLUMN invited_by UUID;

UPDATE iam_invites SET status = 'REVOKED' WHERE status = 'PENDING';
UPDATE iam_invites SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');

ALTER TABLE iam_invites RENAME COLUMN token TO token_hash;
ALTER TABLE iam_invites RENAME CONSTRAINT uq_iam_invites_token TO uq_iam_invites_token_hash;
ALTER TABLE iam_invites
    ADD CONSTRAINT fk_iam_invites_role_same_tenant FOREIGN KEY (tenant_id, role_id)
        REFERENCES iam_roles (tenant_id, id),
    ADD CONSTRAINT fk_iam_invites_invited_by FOREIGN KEY (invited_by) REFERENCES app_user (id),
    ADD CONSTRAINT ck_iam_invites_pending_role CHECK (status <> 'PENDING' OR role_id IS NOT NULL);
