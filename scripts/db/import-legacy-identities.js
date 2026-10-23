"use strict";
// FEAT-DASH-001: operador local; preserva hashes/status e nunca altera a origem.
const fs = require("node:fs");
const path = require("node:path");
const loader = require("./historical-import");
const literal = value => "'" + String(value).replace(/'/g, "''") + "'";
const uuid = value => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const roles = { COLLABORATOR: "SELF", LEADER: "DEPARTMENT", MANAGER: "DEPARTMENT", EXECUTIVE: "TENANT" };
const validHash = value => /^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$/.test(value);

function prepare(users, departments, mapping, replacementHash) {
    if (replacementHash !== undefined && !validHash(replacementHash)) throw new Error("Hash substituto inválido.");
    const seenUsers = new Set(), seenEmployees = new Set(), seenEmails = new Set(), domains = new Map(), domainOwners = new Map();
    const mapped = new Set();
    for (const tenant of Object.values(mapping)) {
        if (!uuid(tenant) || mapped.has(tenant)) throw new Error("Mapa de empresas inválido ou não injetivo.");
        mapped.add(tenant);
    }
    const deptById = new Map();
    const preparedDepartments = departments.map(d => {
        if (!mapping[d.company_id] || !uuid(d.id) || deptById.has(d.id) || !d.legacy_department_id
                || !d.name || d.name.length > 150 || !["ACTIVE", "INACTIVE"].includes(d.status)) throw new Error("Departamento legado inválido.");
        const value = { ...d, tenant_id: mapping[d.company_id] };
        deptById.set(d.id, value); return value;
    });
    const preparedUsers = users.map(u => {
        const tenant = mapping[u.company_id], dept = deptById.get(u.department_id);
        if (!tenant || !uuid(u.id) || seenUsers.has(u.id) || u.is_admin !== false || !roles[u.hierarchy_level]
                || !["ACTIVE", "INACTIVE"].includes(u.status) || !u.legacy_employee_id
                || (!validHash(u.password_hash) && !replacementHash)
                || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(u.email) || u.email.length > 255
                || !dept || dept.tenant_id !== tenant || dept.old_tenant_id !== u.old_tenant_id
                || dept.legacy_department_id !== u.employee_department_id) throw new Error("Identidade legada inválida, ambígua ou fora do departamento/empresa.");
        const employeeKey = tenant + ":" + u.legacy_employee_id, emailKey = tenant + ":" + u.email.toLowerCase();
        if (seenEmployees.has(employeeKey) || seenEmails.has(emailKey)) throw new Error("Identidade legada duplicada.");
        seenUsers.add(u.id); seenEmployees.add(employeeKey); seenEmails.add(emailKey);
        const domain = u.email.split("@")[1].toLowerCase();
        if ((domains.has(tenant) && domains.get(tenant) !== domain) || (domainOwners.has(domain) && domainOwners.get(domain) !== tenant)) throw new Error("Domínios de empresas ambíguos.");
        domains.set(tenant, domain); domainOwners.set(domain, tenant);
        return { ...u, tenant_id: tenant, name: u.email.slice(0, 200), scope_type: roles[u.hierarchy_level],
            password_hash: validHash(u.password_hash) ? u.password_hash : replacementHash,
            password_reset: !validHash(u.password_hash) };
    });
    return { users: preparedUsers, departments: preparedDepartments, domains: [...domains].map(([tenant_id, domain]) => ({ tenant_id, domain })) };
}

function script(data) {
    return `BEGIN;
SELECT pg_advisory_xact_lock(hashtext('FEAT-DASH-001-identities'));
CREATE TEMP TABLE legacy_import_users ON COMMIT DROP AS SELECT * FROM jsonb_to_recordset(${literal(JSON.stringify(data.users))}::jsonb)
 AS x(id uuid,tenant_id uuid,legacy_employee_id text,email text,name text,password_hash text,hierarchy_level text,department_id uuid,status text,scope_type text);
CREATE TEMP TABLE legacy_import_depts ON COMMIT DROP AS SELECT * FROM jsonb_to_recordset(${literal(JSON.stringify(data.departments))}::jsonb)
 AS x(id uuid,tenant_id uuid,legacy_department_id text,name text,status text);
CREATE TEMP TABLE legacy_import_domains ON COMMIT DROP AS SELECT * FROM jsonb_to_recordset(${literal(JSON.stringify(data.domains))}::jsonb)
 AS x(tenant_id uuid,domain text);
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM legacy_import_domains s LEFT JOIN iam_tenants t ON t.id=s.tenant_id WHERE t.id IS NULL OR t.status<>'ACTIVE') THEN RAISE EXCEPTION 'Tenant de destino ausente ou inativo'; END IF;
 IF EXISTS (SELECT 1 FROM legacy_import_domains s JOIN iam_tenants t ON t.email_domain=s.domain AND t.id<>s.tenant_id) THEN RAISE EXCEPTION 'Domínio já pertence a outro tenant'; END IF;
 IF EXISTS (SELECT 1 FROM legacy_import_users s JOIN app_user u ON u.id=s.id
   LEFT JOIN iam_legacy_user_links l ON l.user_id=u.id AND l.tenant_id=u.tenant_id
   WHERE l.user_id IS NULL OR ROW(u.tenant_id,u.email,u.name,u.password_hash,u.hierarchy_level,u.department_id,u.status,l.legacy_employee_id)
   IS DISTINCT FROM ROW(s.tenant_id,s.email,s.name,s.password_hash,s.hierarchy_level,s.department_id,s.status,s.legacy_employee_id)) THEN RAISE EXCEPTION 'Conta existente diverge do lote; nenhuma sobrescrita automática'; END IF;
 IF EXISTS (SELECT 1 FROM legacy_import_users s JOIN app_user u ON u.tenant_id=s.tenant_id AND lower(u.email)=lower(s.email) WHERE u.id<>s.id) THEN RAISE EXCEPTION 'Email já pertence a outra conta'; END IF;
 IF EXISTS (SELECT 1 FROM legacy_import_depts s JOIN department_departments d ON d.id=s.id
   LEFT JOIN department_legacy_links l ON l.tenant_id=d.tenant_id AND l.department_id=d.id
   WHERE l.department_id IS NULL OR ROW(d.tenant_id,d.name,d.status,l.legacy_department_id)
   IS DISTINCT FROM ROW(s.tenant_id,s.name,s.status,s.legacy_department_id)) THEN RAISE EXCEPTION 'Departamento existente diverge do lote'; END IF;
 IF EXISTS (SELECT 1 FROM legacy_import_users s LEFT JOIN iam_roles r ON r.tenant_id=s.tenant_id AND r.name=s.hierarchy_level WHERE r.id IS NULL) THEN RAISE EXCEPTION 'Papel atual ausente; importador não cria permissões'; END IF;
 IF EXISTS (SELECT 1 FROM legacy_import_users s JOIN iam_legacy_user_links l ON l.tenant_id=s.tenant_id AND l.user_id=s.id
   WHERE NOT EXISTS (SELECT 1 FROM iam_role_assignments a JOIN iam_roles r ON r.id=a.role_id AND r.tenant_id=a.tenant_id
     WHERE a.tenant_id=s.tenant_id AND a.user_id=s.id AND a.revoked_at IS NULL AND r.name=s.hierarchy_level
       AND a.scope_type=s.scope_type AND a.department_id IS NOT DISTINCT FROM CASE WHEN s.scope_type='DEPARTMENT' THEN s.department_id END)) THEN RAISE EXCEPTION 'Concessão importada alterada/revogada; não restaurar automaticamente'; END IF;
 IF EXISTS (SELECT 1 FROM legacy_import_users s WHERE NOT EXISTS (SELECT 1 FROM historical.employees e WHERE e.company_id=s.tenant_id::text AND e.employee_id=s.legacy_employee_id)) THEN RAISE EXCEPTION 'Funcionário ausente no histórico do tenant'; END IF;
 IF EXISTS (SELECT 1 FROM legacy_import_depts s WHERE NOT EXISTS (SELECT 1 FROM historical.departments d WHERE d.company_id=s.tenant_id::text AND d.department_id=s.legacy_department_id)) THEN RAISE EXCEPTION 'Departamento ausente no histórico do tenant'; END IF;
END $$;
UPDATE iam_tenants t SET email_domain=s.domain,updated_at=now() FROM legacy_import_domains s WHERE t.id=s.tenant_id AND t.email_domain IS DISTINCT FROM s.domain;
INSERT INTO department_departments(id,tenant_id,name,status,created_at,updated_at)
 SELECT id,tenant_id,name,status,now(),now() FROM legacy_import_depts ON CONFLICT (id) DO NOTHING;
INSERT INTO department_legacy_links SELECT tenant_id,id,legacy_department_id FROM legacy_import_depts ON CONFLICT (tenant_id,department_id) DO NOTHING;
INSERT INTO app_user(id,tenant_id,email,name,password_hash,hierarchy_level,department_id,status,created_at,updated_at)
 SELECT id,tenant_id,email,name,password_hash,hierarchy_level,department_id,status,now(),now() FROM legacy_import_users ON CONFLICT (id) DO NOTHING;
INSERT INTO iam_legacy_user_links SELECT tenant_id,id,legacy_employee_id FROM legacy_import_users ON CONFLICT (tenant_id,user_id) DO NOTHING;
INSERT INTO iam_role_assignments(id,tenant_id,user_id,role_id,department_id,scope_type,origin,granted_at)
 SELECT gen_random_uuid(),s.tenant_id,s.id,r.id,CASE WHEN s.scope_type='DEPARTMENT' THEN s.department_id END,s.scope_type,'MIGRATION',now()
 FROM legacy_import_users s JOIN iam_roles r ON r.tenant_id=s.tenant_id AND r.name=s.hierarchy_level
 WHERE NOT EXISTS (SELECT 1 FROM iam_role_assignments a WHERE a.tenant_id=s.tenant_id AND a.user_id=s.id AND a.role_id=r.id AND a.revoked_at IS NULL);
SELECT json_build_object('users',(SELECT count(*) FROM legacy_import_users),'departments',(SELECT count(*) FROM legacy_import_depts),'domains',(SELECT count(*) FROM legacy_import_domains));
COMMIT;`;
}

async function main(argv) {
    const args = {};
    for (let n = 0; n < argv.length; n += 2) {
        if (!argv[n].startsWith("--") || !argv[n + 1]) throw new Error("Parâmetros inválidos.");
        args[argv[n].slice(2)] = argv[n + 1];
    }
    for (const key of ["source-env", "target-env", "manifest", "report", "mode"]) if (!args[key]) throw new Error(`Informe --${key}.`);
    if (!["plan", "load"].includes(args.mode)) throw new Error("Modo inválido.");
    const file = path.resolve(args.report), root = path.resolve(__dirname, "../..");
    if (!path.relative(root, file).startsWith("..") && !/^(target|tmp)[\\/]/.test(path.relative(root, file))) throw new Error("Relatório deve ficar fora do Git.");
    const manifest = JSON.parse(fs.readFileSync(args.manifest, "utf8").replace(/^\uFEFF/, ""));
    if (manifest.state !== "COMPLETED") throw new Error("Histórico precisa estar reconciliado antes das identidades.");
    const source = loader.dbConfig(loader.readEnv(args["source-env"]), manifest.source.database);
    const target = loader.dbConfig(loader.readEnv(args["target-env"]));
    if (source.database === target.database || target.database !== manifest.target.database) throw new Error("Origem/destino incompatíveis com o manifesto.");
    if (args.mode === "load" && args["confirm-target"] !== target.database) throw new Error("Confirme explicitamente o destino.");
    const config = { psql: args.psql };
    const [sourceIdentity, targetIdentity] = await Promise.all([loader.identity(config, source), loader.identity(config, target)]);
    if (JSON.stringify(sourceIdentity) !== JSON.stringify(manifest.source) || JSON.stringify(targetIdentity) !== JSON.stringify(manifest.target)) throw new Error("Instâncias diferem do manifesto.");
    const users = await loader.json(config, source, `SELECT coalesce(json_agg(x),'[]') FROM (
      SELECT u.id,u.tenant_id old_tenant_id,u.department_id,u.email,u.password_hash,u.hierarchy_level,u.is_admin,u.status,u.legacy_employee_id,e.company_id,e.department_id employee_department_id
      FROM public.iam_users u LEFT JOIN staging.employees e ON e.employee_id=u.legacy_employee_id) x;`);
    const departments = await loader.json(config, source, `SELECT coalesce(json_agg(x),'[]') FROM (
      SELECT d.id,d.tenant_id old_tenant_id,d.name,d.status,d.legacy_department_id,s.company_id
      FROM public.department_departments d LEFT JOIN staging.departments s ON s.department_id=d.legacy_department_id) x;`);
    const replacementHash = args["password-hash-file"] ? fs.readFileSync(args["password-hash-file"], "utf8").trim() : undefined;
    const data = prepare(users, departments, manifest.mapping, replacementHash);
    const report = { mode: args.mode, users: data.users.length, departments: data.departments.length, domains: data.domains.length,
        active: data.users.filter(u => u.status === "ACTIVE").length,
        passwordResets: data.users.filter(u => u.password_reset).length,
        roles: Object.fromEntries(Object.keys(roles).map(role => [role, data.users.filter(u => u.hierarchy_level === role).length])) };
    if (args.mode === "load") {
        // Não repassar diagnóstico SQL, que poderia conter hashes ou dados da conta.
        try { await loader.psql(config, target, script(data), { write: true }); }
        catch { throw new Error("Importação recusada ou falhou; transação revertida. Verifique colisões, vínculos e concessões no destino."); }
    }
    fs.mkdirSync(path.dirname(file), { recursive: true }); loader.saveJson(file, report);
    console.log(JSON.stringify(report));
}
if (require.main === module) main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
module.exports = { prepare, script, main };
