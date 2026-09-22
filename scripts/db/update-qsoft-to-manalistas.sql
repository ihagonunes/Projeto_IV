-- ============================================================================
-- SCRIPT DE ATUALIZAÇÃO: RENOMEAR QSOFT PARA MANALISTAS E SEED DE ACESSO
-- BANCO: PostgreSQL (projeto_integrado_iv)
-- DATA: 2026-09-18
-- ============================================================================

BEGIN;

-- 1. Garantir existência da tabela 'tenant' (utilizada pela aplicação Spring Boot / TenantJpaEntity)
CREATE TABLE IF NOT EXISTS tenant (
    id         UUID         NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    name       VARCHAR(200) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_slug UNIQUE (slug)
);

-- 2. Migrar dados de iam_tenants (se existir) para tenant
INSERT INTO tenant (id, slug, name, status, created_at, updated_at)
SELECT id, slug, name, status, created_at, updated_at
FROM iam_tenants
ON CONFLICT (id) DO UPDATE 
SET slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    updated_at = EXCLUDED.updated_at;

-- 3. Remover registro duplicado de admin se existir
DELETE FROM app_user 
WHERE id = '839653bd-1dcb-49bc-aa46-906700f84571';

-- 4. Atualizar nome e slug do Tenant Principal de QSOFT para Manalistas em ambas as tabelas
UPDATE tenant 
SET name = 'Manalistas', 
    slug = 'manalistas' 
WHERE id = 'b621948f-0e43-4086-a2fc-ed0f2737d716' OR slug = 'qsoft';

UPDATE iam_tenants 
SET name = 'Manalistas', 
    slug = 'manalistas' 
WHERE id = 'b621948f-0e43-4086-a2fc-ed0f2737d716' OR slug = 'qsoft';

-- 5. Atualizar o Administrador Principal
UPDATE app_user 
SET name = 'Administrador Manalistas',
    email = 'admin@manalistas.com.br',
    password_hash = '$2a$10$zyry7qzQvluZWzyWMfBpJu/0j/gzDx/p30tiTgXz73pXpVdEfOKxi', -- admin123
    status = 'ACTIVE'
WHERE id = 'c9091449-3cd7-4c51-be69-b47b90886aa8' OR (tenant_id = 'b621948f-0e43-4086-a2fc-ed0f2737d716' AND email LIKE 'admin%');

-- 6. Garantir hash e status ACTIVE para todos os 18 administradores das empresas (admin@cXX.com.br)
UPDATE app_user 
SET password_hash = '$2a$10$zyry7qzQvluZWzyWMfBpJu/0j/gzDx/p30tiTgXz73pXpVdEfOKxi', -- admin123
    status = 'ACTIVE'
WHERE email LIKE 'admin@c%.com.br';

COMMIT;
