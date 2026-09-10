-- =====================================================================
-- CORREÇÃO — remove FK cross-module + adiciona tenant_id que faltava
-- Rode isso no banco NOVO onde a migração já foi feita (não perde
-- nenhum dado — são ALTERs, não recriação de tabela).
-- =====================================================================
-- Alvo: historical_pull_requests, historical_issues, historical_builds,
-- historical_deployments. Essas 4 são as únicas tabelas do schema com
-- FK cross-module real (confirmado via pg_constraint) e as únicas sem
-- tenant_id de verdade (confirmado via inspeção de todas as 34 tabelas).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) historical_pull_requests
-- ---------------------------------------------------------------------

ALTER TABLE public.historical_pull_requests DROP CONSTRAINT historical_pull_requests_project_id_fkey;

--- RODAR DAQUI PRA BAIXO

ALTER TABLE public.historical_pull_requests ADD COLUMN tenant_id uuid;

UPDATE public.historical_pull_requests hpr
SET tenant_id = pp.tenant_id
FROM public.project_projects pp
WHERE pp.id = hpr.project_id;

ALTER TABLE public.historical_pull_requests ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX idx_hist_pr_tenant ON public.historical_pull_requests(tenant_id);


-- ---------------------------------------------------------------------
-- 2) historical_issues
-- ---------------------------------------------------------------------
ALTER TABLE public.historical_issues DROP CONSTRAINT historical_issues_project_id_fkey;

ALTER TABLE public.historical_issues ADD COLUMN tenant_id uuid;

UPDATE public.historical_issues hi
SET tenant_id = pp.tenant_id
FROM public.project_projects pp
WHERE pp.id = hi.project_id;

ALTER TABLE public.historical_issues ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX idx_hist_issues_tenant ON public.historical_issues(tenant_id);


-- ---------------------------------------------------------------------
-- 3) historical_builds
-- ---------------------------------------------------------------------
ALTER TABLE public.historical_builds DROP CONSTRAINT historical_builds_project_id_fkey;

ALTER TABLE public.historical_builds ADD COLUMN tenant_id uuid;

UPDATE public.historical_builds hb
SET tenant_id = pp.tenant_id
FROM public.project_projects pp
WHERE pp.id = hb.project_id;

ALTER TABLE public.historical_builds ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX idx_hist_builds_tenant ON public.historical_builds(tenant_id);


-- ---------------------------------------------------------------------
-- 4) historical_deployments
-- ---------------------------------------------------------------------
ALTER TABLE public.historical_deployments DROP CONSTRAINT historical_deployments_project_id_fkey;

ALTER TABLE public.historical_deployments ADD COLUMN tenant_id uuid;

UPDATE public.historical_deployments hd
SET tenant_id = pp.tenant_id
FROM public.project_projects pp
WHERE pp.id = hd.project_id;

ALTER TABLE public.historical_deployments ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX idx_hist_deployments_tenant ON public.historical_deployments(tenant_id);


-- =====================================================================
-- Checagem: confirma que não sobrou nenhuma FK cross-module no banco
-- (a query só deve listar FKs onde as duas tabelas começam com o
-- mesmo prefixo de módulo, ou nenhuma linha no caso de project_id acima)
-- =====================================================================
-- SELECT
--     tc.table_name AS tabela,
--     kcu.column_name AS coluna,
--     ccu.table_name AS referencia
-- FROM information_schema.table_constraints tc
-- JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
-- JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
-- WHERE tc.constraint_type = 'FOREIGN KEY'
-- ORDER BY tc.table_name;


-- Checagem: confirma que todo tenant_id novo foi preenchido (não deve
-- retornar nenhuma linha)
-- SELECT 'historical_pull_requests' t, count(*) FROM historical_pull_requests WHERE tenant_id IS NULL
-- UNION ALL SELECT 'historical_issues', count(*) FROM historical_issues WHERE tenant_id IS NULL
-- UNION ALL SELECT 'historical_builds', count(*) FROM historical_builds WHERE tenant_id IS NULL
-- UNION ALL SELECT 'historical_deployments', count(*) FROM historical_deployments WHERE tenant_id IS NULL;
