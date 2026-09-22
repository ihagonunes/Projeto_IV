-- V006 - Seed inicial do Tenant e Administrador Principal (Manalistas)
-- Permite login imediato em ambiente local sem necessidade de scripts manuais.

INSERT INTO tenant (id, slug, name, status, created_at, updated_at)
VALUES (
    'b621948f-0e43-4086-a2fc-ed0f2737d716',
    'manalistas',
    'Manalistas',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET 
    slug = 'manalistas', 
    name = 'Manalistas',
    status = 'ACTIVE';

INSERT INTO app_user (id, tenant_id, email, name, hierarchy_level, department_id, status, password_hash, created_at, updated_at)
VALUES (
    'c9091449-3cd7-4c51-be69-b47b90886aa8',
    'b621948f-0e43-4086-a2fc-ed0f2737d716',
    'admin@manalistas.com.br',
    'Administrador Manalistas',
    'EXECUTIVE',
    NULL,
    'ACTIVE',
    '$2a$10$zyry7qzQvluZWzyWMfBpJu/0j/gzDx/p30tiTgXz73pXpVdEfOKxi',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO UPDATE SET 
    email = 'admin@manalistas.com.br',
    password_hash = '$2a$10$zyry7qzQvluZWzyWMfBpJu/0j/gzDx/p30tiTgXz73pXpVdEfOKxi',
    status = 'ACTIVE';

INSERT INTO user_permission (user_id, permission) VALUES
    ('c9091449-3cd7-4c51-be69-b47b90886aa8', 'MANAGE_TENANT'),
    ('c9091449-3cd7-4c51-be69-b47b90886aa8', 'MANAGE_USERS'),
    ('c9091449-3cd7-4c51-be69-b47b90886aa8', 'MANAGE_HIERARCHY'),
    ('c9091449-3cd7-4c51-be69-b47b90886aa8', 'MANAGE_DEPARTMENTS'),
    ('c9091449-3cd7-4c51-be69-b47b90886aa8', 'MANAGE_MODEL_POLICIES'),
    ('c9091449-3cd7-4c51-be69-b47b90886aa8', 'MANAGE_AI_CATALOG'),
    ('c9091449-3cd7-4c51-be69-b47b90886aa8', 'MANAGE_KNOWLEDGE'),
    ('c9091449-3cd7-4c51-be69-b47b90886aa8', 'MANAGE_LICENSES'),
    ('c9091449-3cd7-4c51-be69-b47b90886aa8', 'MANAGE_TRAINING')
ON CONFLICT (user_id, permission) DO NOTHING;
