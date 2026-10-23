"use strict";

// Operador local de carga, sem endpoint público, sem dependências adicionais.
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { spawn } = require("node:child_process");
const { pipeline } = require("node:stream/promises");
const { tables, dateTimes, dates } = require("./historical-contract");
const names = Object.keys(tables);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const identifier = value => {
    if (!/^[a-z_][a-z0-9_]*$/.test(value)) throw new Error("Identificador SQL inválido.");
    return `"${value}"`;
};
const literal = value => "'" + String(value).replace(/'/g, "''") + "'";
const sha = value => crypto.createHash("sha256").update(value).digest("hex");

function readEnv(file) {
    const result = {};
    for (const line of fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "").split(/\r?\n/)) {
        const match = line.match(/^\s*(?:export\s+)?([A-Z_][A-Z_0-9]*)\s*=(.*)$/);
        if (!match) continue;
        let value = match[2].trim();
        if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
        result[match[1]] = value;
    }
    return result;
}
function dbConfig(env, database) {
    if (!(database || env.DB_NAME) || !env.DB_USER || env.DB_PASSWORD === undefined) {
        throw new Error("Configuração incompleta: DB_NAME, DB_USER e DB_PASSWORD devem ser explícitos.");
    }
    return { host: env.DB_HOST || "localhost", port: env.DB_PORT || "5432", database: database || env.DB_NAME,
        user: env.DB_USER, password: env.DB_PASSWORD };
}
function saveJson(file, value) {
    const temporary = file + ".tmp-" + process.pid;
    fs.writeFileSync(temporary, JSON.stringify(value, null, 2) + "\n", { mode: 0o600 });
    for (let attempt = 0; ; attempt++) {
        try { fs.renameSync(temporary, file); return; }
        catch (error) {
            if (attempt >= 7 || !["EPERM", "EACCES", "EBUSY"].includes(error.code)) throw error;
            // Windows pode manter um handle de leitura/antivírus por alguns milissegundos.
            Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 50 * (attempt + 1));
        }
    }
}
function readJson(file) { return JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, "")); }
function psql(config, db, sql, { write = false, output } = {}) {
    return new Promise((resolve, reject) => {
        const child = spawn(config.psql || "psql", ["-X", "-w", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1"], {
            windowsHide: true,
            env: { ...process.env, PGHOST: db.host, PGPORT: String(db.port), PGDATABASE: db.database,
                PGUSER: db.user, PGPASSWORD: db.password, PGCLIENTENCODING: "UTF8", PGCONNECT_TIMEOUT: "10",
                PGOPTIONS: write ? "" : "-c default_transaction_read_only=on" }
        });
        let stdout = "", stderr = "";
        const pipe = output ? pipeline(child.stdout, fs.createWriteStream(output, { mode: 0o600 })) : null;
        if (!output) child.stdout.on("data", chunk => { stdout += chunk; });
        child.stderr.on("data", chunk => { stderr += chunk; });
        child.on("error", reject);
        child.on("close", async code => {
            try {
                if (pipe) await pipe;
                if (code !== 0) {
                    // Não imprimir linhas de COPY, consultas, credenciais ou registros do dataset.
                    const diagnostic = stderr.match(/(?:ERROR|FATAL|ERRO):[^\r\n]*/)?.[0];
                    throw new Error(`psql falhou no banco ${db.database} (código ${code}). ${diagnostic || "Verifique conexão e ferramentas."}`);
                }
                resolve(stdout.trim());
            } catch (error) { reject(error); }
        });
        child.stdin.on("error", () => {}); // Processo pode rejeitar antes de consumir o script inteiro.
        child.stdin.end(sql + "\n");
    });
}
async function json(config, db, sql) {
    const text = await psql(config, db, sql);
    return text ? JSON.parse(text) : null;
}
async function identity(config, db) {
    return json(config, db, `SELECT json_build_object('database',current_database(),'address',inet_server_addr()::text,'port',inet_server_port());`);
}
function normalizedQuery(table, mapping) {
    const columns = Object.entries(tables[table]).map(([column, type]) => {
        const field = identifier(column);
        let value = field;
        if (column === "company_id" && mapping) {
            value = `CASE ${field} ${Object.entries(mapping).map(([original, tenant]) => `WHEN ${literal(original)} THEN ${literal(tenant)}`).join(" ")} END`;
        } else if (column === "active") {
            value = `CASE lower(${field}::text) WHEN 'true' THEN 1 WHEN 'false' THEN 0 WHEN '1' THEN 1 WHEN '0' THEN 0 END`;
        } else if (dateTimes.has(column)) value = `replace(${field}::text,' ','T')`;
        return `${value}::${type} AS ${field}`;
    });
    return `SELECT ${columns.join(",")} FROM staging.${identifier(table)}`;
}
function targetQuery(table) {
    return `SELECT ${Object.keys(tables[table]).map(identifier).join(",")} FROM historical.${identifier(table)}`;
}
function fingerprint(query) {
    return `SELECT json_build_object('count',count(*),'digest',md5(coalesce(string_agg(md5(row_to_json(r)::text),'' ORDER BY md5(row_to_json(r)::text)),''))) FROM (${query}) r`;
}
async function inspect(config) {
    const source = await identity(config, config.source), target = await identity(config, config.target);
    // Política conservadora: nomes de banco também devem diferir, inclusive com aliases IPv4/IPv6.
    if (source.database === target.database) {
        throw new Error("Origem e destino são o mesmo banco. Nenhuma carga permitida.");
    }
    const actual = await json(config, config.target, `SELECT coalesce(json_agg(row_to_json(c)),'[]') FROM
        (SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema='historical') c;`);
    for (const [table, columns] of Object.entries(tables)) {
        const found = actual.filter(column => column.table_name === table);
        if (found.length !== Object.keys(columns).length || Object.entries(columns).some(([name, type]) =>
            !found.some(column => column.column_name === name && column.data_type === type))) {
            throw new Error(`Schema historical incompatível: ${table}. Execute as migrations no banco novo.`);
        }
    }
    const sourceColumns = await json(config, config.source, `SELECT coalesce(json_agg(row_to_json(c)),'[]') FROM
        (SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='staging') c;`);
    for (const [table, columns] of Object.entries(tables)) {
        if (Object.keys(columns).some(name => !sourceColumns.some(column => column.table_name === table && column.column_name === name))) {
            throw new Error(`Fonte staging incompatível: ${table}.`);
        }
        // Valida casts e conteúdo antes de abrir uma transação de escrita no destino.
        await psql(config, config.source, `SELECT count(*) FROM (${normalizedQuery(table)}) r WHERE row_to_json(r) IS NOT NULL;`);
        for (const column of Object.keys(columns)) {
            let invalid;
            if (column === "active") invalid = `lower(${identifier(column)}::text) NOT IN ('true','false','1','0')`;
            if (dateTimes.has(column)) invalid = `${identifier(column)} !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}$'`;
            if (dates.has(column)) invalid = `${identifier(column)} !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'`;
            if (columns[column] === "double precision") invalid = `${identifier(column)}::text IN ('NaN','Infinity','-Infinity')`;
            if (invalid) {
                const count = await json(config, config.source, `SELECT count(*) FROM staging.${identifier(table)} WHERE ${identifier(column)} IS NOT NULL AND (${invalid});`);
                if (count) throw new Error(`Conversão não reconhecida: ${table}.${column} (${count} registros).`);
            }
            if (dateTimes.has(column) || dates.has(column)) {
                await psql(config, config.source, `SELECT count(*) FROM staging.${identifier(table)} WHERE ${identifier(column)}::timestamp IS NOT NULL;`);
            }
        }
    }
    const companies = await json(config, config.source, `SELECT json_agg(row_to_json(c) ORDER BY company_id) FROM (SELECT company_id,company_name FROM staging.companies) c;`);
    if (!companies?.length || companies.some(company => !company.company_id || !company.company_name) || new Set(companies.map(c => c.company_id)).size !== companies.length) {
        throw new Error("Empresas ausentes, duplicadas ou incompletas.");
    }
    const uniqueKeys = { companies: ["company_id"], departments: ["company_id", "department_id"],
        employees: ["company_id", "employee_id"], projects: ["project_id"], ai_tools: ["ai_tool_id"], ai_models: ["model_id"] };
    for (const [table, columns] of Object.entries(uniqueKeys)) {
        const keys = columns.map(identifier).join(",");
        const duplicates = await json(config, config.source, `SELECT count(*) FROM (SELECT ${keys} FROM staging.${identifier(table)} GROUP BY ${keys} HAVING count(*)>1) d;`);
        if (duplicates) throw new Error(`Identificadores históricos ambíguos em ${table}.`);
    }
    const references = [
        ["departments", "company_id", "companies", "company_id"],
        ["employees", "department_id", "departments", "department_id", "company_id"],
        ["projects", "company_id", "companies", "company_id"],
        ["ai_models", "ai_tool_id", "ai_tools", "ai_tool_id"],
        ...["builds", "deployments", "issues", "pull_requests"].map(table => [table, "project_id", "projects", "project_id"]),
        ...["ai_usage_logs", "ai_licenses", "security_events", "training_records"].map(table => [table, "employee_id", "employees", "employee_id", "company_id"]),
        ["ai_usage_logs", "department_id", "departments", "department_id", "company_id"],
        ["ai_usage_logs", "project_id", "projects", "project_id", "company_id"],
        ...["ai_usage_logs", "ai_billing"].map(table => [table, "model_id", "ai_models", "model_id"]),
        ...["ai_usage_logs", "ai_billing", "ai_licenses", "approved_ai_tools", "security_events"].map(table => [table, "ai_tool_id", "ai_tools", "ai_tool_id"])
    ];
    for (const [table, column, parent, key, owner] of references) {
        const count = await json(config, config.source, `SELECT count(*) FROM staging.${identifier(table)} c WHERE c.${identifier(column)} IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM staging.${identifier(parent)} p WHERE p.${identifier(key)}=c.${identifier(column)}
            ${owner ? `AND p.${identifier(owner)}=c.${identifier(owner)}` : ""});`);
        if (count) throw new Error(`Referências não resolvidas: ${table}.${column} → ${parent} (${count}).`);
    }
    for (const [table, columns] of Object.entries(tables)) {
        if (columns.company_id) {
            const count = await json(config, config.source, `SELECT count(*) FROM staging.${identifier(table)} c WHERE company_id IS NULL
                OR NOT EXISTS (SELECT 1 FROM staging.companies p WHERE p.company_id=c.company_id);`);
            if (count) throw new Error(`Empresa não resolvida em ${table}: ${count}.`);
        }
    }
    const sourceFingerprints = {};
    for (const table of names) sourceFingerprints[table] = await json(config, config.source, fingerprint(normalizedQuery(table)));
    return { source, target, companies, sourceFingerprints, sourceSignature: sha(JSON.stringify(sourceFingerprints)),
        conversion: { active: "True/False → 1/0; nulos preservados", timestamp: "espaço → T; sem conversão de fuso" } };
}
function validateMapping(companies, mapping) {
    const ids = companies.map(company => company.company_id);
    if (Object.keys(mapping).length !== ids.length || ids.some(id => !UUID.test(mapping[id] || "")) || new Set(Object.values(mapping)).size !== ids.length) {
        throw new Error("Mapeamento de empresas deve ser completo, UUID e um-para-um.");
    }
}
async function validateTenants(config, mapping) {
    const count = await json(config, config.target, `SELECT count(*) FROM iam_tenants WHERE id IN (${Object.values(mapping).map(literal).join(",")}) AND status='ACTIVE';`);
    if (count !== Object.keys(mapping).length) throw new Error("Tenant mapeado inexistente ou inativo no destino.");
}
function manifestPath(config) { return path.join(config.workDir, "manifest.json"); }
function loadManifest(config, plan) {
    const file = manifestPath(config);
    if (!fs.existsSync(file)) return { batch: config.batch, sourceSignature: plan.sourceSignature, source: plan.source, target: plan.target, mapping: {}, state: "PLANNED" };
    const manifest = readJson(file);
    if (manifest.batch !== config.batch || manifest.sourceSignature !== plan.sourceSignature
        || JSON.stringify(manifest.source) !== JSON.stringify(plan.source)
        || JSON.stringify(manifest.target) !== JSON.stringify(plan.target)) {
        throw new Error("Manifesto pertence a outro lote, fonte ou destino.");
    }
    return manifest;
}
async function provision(config, plan) {
    const url = new URL(config.apiUrl);
    if (!["localhost", "127.0.0.1", "[::1]"].includes(url.hostname) || String(url.port || "80") !== String(config.targetEnv.SERVER_PORT || "8080")) {
        throw new Error("API deve ser local e usar a porta explícita do ambiente de destino.");
    }
    const manifest = loadManifest(config, plan);
    const seedFile = path.join(config.workDir, "credentials.key");
    if (!fs.existsSync(seedFile)) {
        if (Object.keys(manifest.mapping).length) throw new Error("Chave local de credenciais ausente; não recriar contas.");
        fs.writeFileSync(seedFile, crypto.randomBytes(32).toString("hex"), { mode: 0o600, flag: "wx" });
    }
    const seed = fs.readFileSync(seedFile, "utf8");
    for (const company of plan.companies) {
        const credentials = credentialsFor(company, seed);
        if (!manifest.mapping[company.company_id]) {
            const created = await fetch(new URL("/api/v1/tenants", url), { signal: AbortSignal.timeout(15000), method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({
                slug: credentials.slug, name: company.company_name, emailDomain: credentials.domain,
                adminName: "Administrador histórico", adminEmail: credentials.email, adminPassword: credentials.password
            }) });
            if (created.status !== 201 && created.status !== 409) throw new Error(`Provisionamento de tenant falhou: HTTP ${created.status}.`);
        }
        const login = await fetch(new URL("/api/v1/auth/login", url), { signal: AbortSignal.timeout(15000), method: "POST", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ tenantSlug: credentials.slug, email: credentials.email, password: credentials.password }) });
        if (!login.ok) throw new Error(`Não foi possível verificar o administrador: HTTP ${login.status}.`);
        const session = await login.json();
        const current = await fetch(new URL("/api/v1/tenants/me", url), { signal: AbortSignal.timeout(15000), headers: { Authorization: `Bearer ${session.token}` } });
        if (!current.ok) throw new Error(`Não foi possível verificar o tenant: HTTP ${current.status}.`);
        const tenant = await current.json();
        if (!UUID.test(tenant.id) || tenant.slug !== credentials.slug || (manifest.mapping[company.company_id] && manifest.mapping[company.company_id] !== tenant.id)) {
            throw new Error("Identidade de tenant divergente do manifesto.");
        }
        const accessResponse = await fetch(new URL("/api/v1/usuarios/me/permissoes", url), {
            signal: AbortSignal.timeout(15000), headers: { Authorization: `Bearer ${session.token}` }
        });
        if (!accessResponse.ok) throw new Error(`Não foi possível verificar concessões: HTTP ${accessResponse.status}.`);
        const access = await accessResponse.json();
        if (!access.permissoes?.includes("TENANT:MANAGE") || !access.concessoes?.some(grant => grant.papel === "TENANT_ADMIN" && grant.escopo === "TENANT")) {
            throw new Error("Administrador sem concessão TENANT_ADMIN de escopo TENANT. Não aceitar o mapeamento.");
        }
        manifest.mapping[company.company_id] = tenant.id;
        await validateTenants(config, manifest.mapping);
        saveJson(manifestPath(config), manifest);
    }
    validateMapping(plan.companies, manifest.mapping);
    if (manifest.state !== "COMPLETED") manifest.state = "PROVISIONED";
    saveJson(manifestPath(config), manifest);
    return manifest;
}
function credentialsFor(company, seed) {
    const slug = "hist-" + sha(company.company_id).slice(0, 16), domain = slug + ".example.test";
    return { slug, domain, email: "admin@" + domain, password: crypto.createHmac("sha256", seed).update(company.company_id).digest("base64url") };
}
function loadScript(files, expected, mapping) {
    const digest = table => `(SELECT md5(coalesce(string_agg(md5(row_to_json(r)::text),'' ORDER BY md5(row_to_json(r)::text)),'')) FROM (${targetQuery(table)}) r)`;
    const assertions = names.map(table => `IF (SELECT count(*) FROM historical.${identifier(table)}) <> ${expected[table].count}
        OR ${digest(table)} <> ${literal(expected[table].digest)}
        THEN RAISE EXCEPTION 'Reconciliação falhou: ${table}'; END IF;`).join("\n");
    // Lock é transacional e só afeta o destino; a segunda execução verifica estado após o lock.
    const match = names.map(table => `(SELECT count(*) FROM historical.${identifier(table)})=${expected[table].count}
        AND ${digest(table)}=${literal(expected[table].digest)}`).join(" AND ");
    const occupied = names.map(table => `EXISTS(SELECT 1 FROM historical.${identifier(table)})`).join(" OR ");
    return String.raw`BEGIN;
SELECT pg_advisory_xact_lock(hashtext('qsoft-historical-import'));
SELECT (${match}) AS already_loaded \gset
\if :already_loaded
\echo ALREADY_LOADED
\else
DO $guard$ BEGIN IF ${occupied} THEN RAISE EXCEPTION 'Destino histórico não está vazio e não corresponde ao lote'; END IF; END $guard$;
${names.map(table => `\\copy historical.${identifier(table)} (${Object.keys(tables[table]).map(identifier).join(",")}) FROM ${literal(files[table].replace(/\\/g, "/"))} WITH (FORMAT csv, HEADER true)`).join("\n").replace(/\\\\copy/g, "\\copy")}
DO $verify$ BEGIN
${assertions}
IF EXISTS(SELECT 1 FROM historical.companies WHERE company_id NOT IN (${Object.values(mapping).map(literal).join(",")})) THEN RAISE EXCEPTION 'Empresa fora do mapeamento'; END IF;
END $verify$;
\echo LOADED
\endif
COMMIT;`;
}
async function importBatch(config, plan, hooks = {}) {
    const manifest = loadManifest(config, plan);
    validateMapping(plan.companies, manifest.mapping);
    await validateTenants(config, manifest.mapping);
    const expected = {}, files = {}, fileHashes = {};
    for (const table of names) {
        expected[table] = await json(config, config.source, fingerprint(normalizedQuery(table, manifest.mapping)));
        files[table] = path.join(config.workDir, table + ".csv");
        await psql(config, config.source, `COPY (${normalizedQuery(table, manifest.mapping)}) TO STDOUT WITH (FORMAT csv, HEADER true);`, { output: files[table] });
        const digest = crypto.createHash("sha256");
        for await (const chunk of fs.createReadStream(files[table])) digest.update(chunk);
        fileHashes[table] = digest.digest("hex");
    }
    // A fonte deve ser snapshot restaurado, sem escritores; detectar mudanças durante extração.
    for (const table of names) {
        const current = await json(config, config.source, fingerprint(normalizedQuery(table)));
        if (JSON.stringify(current) !== JSON.stringify(plan.sourceFingerprints[table])) throw new Error("Fonte mudou durante extração. Refaça o snapshot.");
    }
    if (hooks.afterExport) await hooks.afterExport(files); // Testes de rollback exercitam a mesma implementação.
    const result = await psql(config, config.target, loadScript(files, expected, manifest.mapping), { write: true });
    manifest.state = "COMPLETED";
    manifest.expected = expected;
    manifest.csvSha256 = fileHashes;
    manifest.completedAt = new Date().toISOString();
    manifest.result = result.includes("ALREADY_LOADED") ? "ALREADY_LOADED" : "LOADED";
    if (hooks.afterCommit) await hooks.afterCommit();
    saveJson(manifestPath(config), manifest);
    return manifest;
}
function cliConfig(argv) {
    const allowed = new Set(["mode", "source-env", "source-db", "target-env", "work-dir", "batch", "confirm-target", "api-url", "psql"]);
    const args = {};
    for (let i = 0; i < argv.length; i += 2) {
        const key = argv[i]?.replace(/^--/, "");
        if (!allowed.has(key) || !argv[i + 1] || argv[i + 1].startsWith("--")) throw new Error("Parâmetro ausente ou desconhecido.");
        args[key] = argv[i + 1];
    }
    for (const key of ["mode", "source-env", "target-env", "work-dir", "batch"]) if (!args[key]) throw new Error(`Parâmetro obrigatório: --${key}`);
    if (!["plan", "provision", "load"].includes(args.mode)) throw new Error("Modo inválido.");
    const targetEnv = readEnv(args["target-env"]);
    const config = { mode: args.mode, source: dbConfig(readEnv(args["source-env"]), args["source-db"]), target: dbConfig(targetEnv),
        targetEnv, workDir: path.resolve(args["work-dir"]), batch: args.batch, psql: args.psql || "psql", apiUrl: args["api-url"] };
    const repo = path.resolve(__dirname, "../..");
    const relative = path.relative(repo, config.workDir);
    if (!relative.startsWith(".." + path.sep) && !path.isAbsolute(relative) && !/^(target|tmp)([\\/]|$)/.test(relative)) {
        throw new Error("WorkDir deve ficar fora do repositório ou em target/tmp ignorados pelo Git.");
    }
    if (config.mode !== "plan" && args["confirm-target"] !== config.target.database) throw new Error("Confirme explicitamente o destino com --confirm-target.");
    if (config.mode === "provision" && !config.apiUrl) throw new Error("Informe --api-url do backend novo.");
    return config;
}
async function main(argv) {
    const config = cliConfig(argv);
    fs.mkdirSync(config.workDir, { recursive: true, mode: 0o700 });
    const plan = await inspect(config);
    saveJson(path.join(config.workDir, "plan.json"), plan);
    if (config.mode === "provision") await provision(config, plan);
    if (config.mode === "load") await importBatch(config, plan);
    console.log(JSON.stringify({ mode: config.mode, source: plan.source, target: plan.target,
        companies: plan.companies.length, tables: names.length, report: path.join(config.workDir, "plan.json") }));
}
if (require.main === module) main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
module.exports = { readEnv, dbConfig, psql, json, identity, normalizedQuery, targetQuery, fingerprint, inspect,
    saveJson, loadManifest, validateMapping, credentialsFor, provision, importBatch, loadScript, cliConfig, main };
