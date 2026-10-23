"use strict";
// FEAT-DASH-001 / BUGFIX-DB-001: local, repeatable installation; never repairs/drops a database.
const fs = require("node:fs"), path = require("node:path"), crypto = require("node:crypto");
const net = require("node:net");
const { spawn } = require("node:child_process");
const loader = require("./db/historical-import");
const identities = require("./db/import-legacy-identities");
const root = path.resolve(__dirname, "..");
const literal = value => "'" + String(value).replace(/'/g, "''") + "'";
const dbName = value => /^[A-Za-z][A-Za-z0-9_]{0,62}$/.test(value);
function privatePath(file) {
    const relative = path.relative(root, path.resolve(file));
    return relative.startsWith(".." + path.sep) || path.isAbsolute(relative)
        || /^(target|tmp)([\\/]|$)/.test(relative) || /^\.env\.[^\\/]+$/.test(relative) && relative !== ".env.example";
}
function options(argv) {
    const allowed = new Set(["init-env", "env", "confirm-target", "source-env", "source-db", "source-dump", "work-dir", "skip-build", "java", "maven", "psql", "pg-restore", "jar"]);
    const args = {};
    for (let i = 0; i < argv.length; i += 2) {
        const key = argv[i]?.replace(/^--/, "");
        if (!allowed.has(key) || !argv[i + 1] || argv[i + 1].startsWith("--") || Object.hasOwn(args, key)) throw Error("Parâmetro ausente, repetido ou desconhecido.");
        args[key] = argv[i + 1];
    }
    if (args["init-env"]) {
        if (Object.keys(args).length !== 1 || !privatePath(args["init-env"])) throw Error("Use --init-env com um .env.local privado ou caminho fora do Git.");
        return args;
    }
    if (!args.env) throw Error("Informe --env .env.team.local e --confirm-target NOME_DO_BANCO. Para criar a configuração: --init-env .env.team.local");
    if (!privatePath(args.env) || args["source-env"] && !privatePath(args["source-env"])) throw Error("Use arquivos de configuração privados .env.* ou fora do repositório, nunca .env.example.");
    if (args["skip-build"] && args["skip-build"] !== "true") throw Error("--skip-build aceita somente true.");
    const env = loader.readEnv(args.env), target = loader.dbConfig(env);
    if (!dbName(target.database) || target.database !== args["confirm-target"]) throw Error("Nome do destino inválido ou não confirmado.");
    if (!["localhost", "127.0.0.1", "::1"].includes(target.host) || env.NODE_ENV !== "development") throw Error("Setup permitido somente em PostgreSQL local com NODE_ENV=development.");
    if (!env.JWT_SECRET || env.JWT_SECRET.length < 32 || env.JWT_SECRET.includes("<")) throw Error("Configure JWT_SECRET com pelo menos 32 caracteres.");
    if (!env.SETUP_MOCK_PASSWORD || env.SETUP_MOCK_PASSWORD.length < 8 || Buffer.byteLength(env.SETUP_MOCK_PASSWORD, "utf8") > 72) throw Error("Configure SETUP_MOCK_PASSWORD (mínimo 8 caracteres, máximo 72 bytes); apenas dados mockados locais.");
    const port = Number(env.SERVER_PORT || 8080);
    if (!Number.isInteger(port) || port < 1024 || port > 65535) throw Error("SERVER_PORT inválida (1024–65535).");
    const workDir = path.resolve(args["work-dir"] || path.join(root, "tmp", "setup-" + target.database));
    const relativeWork = path.relative(root, workDir);
    if (!(relativeWork.startsWith(".." + path.sep) || path.isAbsolute(relativeWork) || /^(target|tmp)([\\/]|$)/.test(relativeWork))) throw Error("Work-dir deve ficar fora do Git ou em target/tmp.");
    if (args["source-db"] && !args["source-env"] || args["source-dump"] && !args["source-env"]) throw Error("Fonte exige --source-env.");
    const source = args["source-env"] ? loader.dbConfig(loader.readEnv(args["source-env"]), args["source-db"]) : null;
    if (source && (!dbName(source.database) || source.database === target.database)) throw Error("Snapshot de origem deve ter nome válido e diferente do destino.");
    if (args["source-dump"] && !["localhost", "127.0.0.1", "::1"].includes(source.host)) throw Error("Restauração de dump permitida somente em PostgreSQL local; fontes remotas são somente para leitura.");
    if (args["source-dump"] && !fs.existsSync(args["source-dump"])) throw Error("Dump de origem não encontrado.");
    const java = args.java || (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java") : "java");
    const sibling = name => path.isAbsolute(java) ? path.join(path.dirname(java), name + (process.platform === "win32" ? ".exe" : "")) : name;
    return { ...args, env, envFile: path.resolve(args.env), target, source, workDir, port, java, jarTool: sibling("jar"),
        psql: args.psql || "psql", restore: args["pg-restore"] || "pg_restore", maven: args.maven || "mvn",
        jar: path.resolve(args.jar || path.join(root, "target/qsoft-ai-governance-0.0.1-SNAPSHOT.jar")) };
}
function initEnv(file) {
    file = path.resolve(file);
    if (fs.existsSync(file)) throw Error("Configuração já existe; não será sobrescrita.");
    let text = fs.readFileSync(path.join(root, ".env.example"), "utf8");
    text = text.replace(/^DB_NAME=.*$/m, "DB_NAME=qsoft_team_dev")
        .replace(/^JWT_SECRET=.*$/m, "JWT_SECRET=" + crypto.randomBytes(48).toString("base64url"));
    fs.writeFileSync(file, text + "\nNODE_ENV=development\nSETUP_MOCK_PASSWORD=admin123\nQSOFT_TENANT_SLUG=\n", { flag: "wx", mode: 0o600 });
    console.log("Configuração local criada. Ajuste DB_USER, DB_PASSWORD e portas antes de executar o setup.");
}
function execute(command, args, { cwd = root, env = {}, log, capture = false } = {}) {
    return new Promise((resolve, reject) => {
        const fd = log ? fs.openSync(log, "a", 0o600) : null;
        const child = spawn(command, args, { cwd, env: { ...process.env, ...env }, windowsHide: true,
            stdio: ["ignore", capture ? "pipe" : fd ?? "ignore", fd ?? "ignore"] });
        let output = "";
        if (capture) child.stdout.on("data", data => { output += data; });
        let closed = false;
        const close = () => { if (fd !== null && !closed) { closed = true; fs.closeSync(fd); } };
        child.once("error", () => { close(); reject(Error("Não foi possível executar a ferramenta: " + path.basename(command))); });
        child.once("close", code => { close(); code === 0 ? resolve(output) : reject(Error("Ferramenta falhou: " + path.basename(command) + ". Consulte os logs privados do setup.")); });
    });
}
async function ensureDatabase(config, db) {
    const control = { ...db, database: "postgres" };
    const exists = await loader.json(config, control, `SELECT to_json(EXISTS(SELECT 1 FROM pg_database WHERE datname=${literal(db.database)}))`);
    if (!exists) await loader.psql(config, control, `CREATE DATABASE "${db.database}"`, { write: true });
}
async function occupied(config, db) {
    return loader.json(config, db, "SELECT to_json(EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema') AND table_type='BASE TABLE'))");
}
async function freePort(port) {
    await new Promise((resolve, reject) => {
        const server = net.createServer();
        server.once("error", () => reject(Error("Porta do backend ocupada. Escolha outra SERVER_PORT; o setup não usa nem encerra servidores existentes.")));
        server.listen(port, "127.0.0.1", () => server.close(resolve));
    });
}
async function fileHash(file) {
    const hash = crypto.createHash("sha256");
    for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
    return hash.digest("hex");
}
async function mockHash(config) {
    const list = await execute(config.jarTool, ["tf", config.jar], { capture: true });
    const entry = list.split(/\r?\n/).find(name => /^BOOT-INF\/lib\/spring-security-crypto-[^/]+\.jar$/.test(name));
    if (!entry) throw Error("JAR Spring Boot sem a biblioteca BCrypt esperada. Compile o projeto.");
    await execute(config.jarTool, ["xf", config.jar, entry], { cwd: config.workDir });
    const file = path.join(config.workDir, "mock-password.hash");
    await execute(config.java, ["--class-path", path.join(config.workDir, entry), path.join(root, "scripts/db/LocalMockPassword.java"), file], {
        env: { QSOFT_SETUP_MOCK_PASSWORD: config.env.SETUP_MOCK_PASSWORD }, log: path.join(config.workDir, "password-helper.log") });
    return file;
}
async function startBackend(config) {
    const fd = fs.openSync(path.join(config.workDir, "backend.log"), "a", 0o600);
    const backend = spawn(config.java, ["-jar", config.jar], { cwd: root, windowsHide: true,
        env: { ...process.env, ...config.env, SERVER_ADDRESS: "127.0.0.1", SERVER_PORT: String(config.port),
            SPRING_DATASOURCE_URL: `jdbc:postgresql://${config.target.host === "::1" ? "[::1]" : config.target.host}:${config.target.port}/${config.target.database}`,
            SPRING_DATASOURCE_USERNAME: config.target.user, SPRING_DATASOURCE_PASSWORD: config.target.password,
            SPRING_JPA_HIBERNATE_DDL_AUTO: "validate", SPRING_FLYWAY_ENABLED: "true", SPRING_FLYWAY_BASELINE_ON_MIGRATE: "false",
            APP_BOOTSTRAP_DEMO_DATA: "false" }, stdio: ["ignore", fd, fd] });
    fs.closeSync(fd);
    let failed = false; backend.on("error", () => { failed = true; });
    try {
        for (let attempt = 0; attempt < 120; attempt++) {
            if (failed || backend.exitCode !== null) throw Error("Backend não iniciou. Consulte backend.log no work-dir.");
            try {
                const response = await fetch(`http://127.0.0.1:${config.port}/actuator/health`, { signal: AbortSignal.timeout(1500) });
                if (response.ok) return backend;
            } catch { /* bounded startup retry */ }
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
        throw Error("Backend não ficou pronto no prazo; consulte backend.log.");
    } catch (error) { backend.kill(); throw error; }
}
function api(config) {
    return async (uri, method = "GET", body, token) => {
        const response = await fetch(`http://127.0.0.1:${config.port}/api/v1${uri}`, { method,
            signal: AbortSignal.timeout(20000), headers: { "content-type": "application/json", ...(token ? { Authorization: "Bearer " + token } : {}) },
            ...(body ? { body: JSON.stringify(body) } : {}) });
        if (!response.ok) throw Error(`API local retornou ${response.status} em ${uri}; nenhuma senha ou token foi registrado.`);
        return response.status === 204 ? null : response.json();
    };
}
async function seedAccounts(config, tenant, bootstrap, hash, state, save) {
    const call = api(config), password = config.env.SETUP_MOCK_PASSWORD;
    const login = email => call("/auth/login", "POST", { tenantSlug: tenant.slug, email, password });
    const bootstrapLogin = await call("/auth/login", "POST", bootstrap);
    let adminToken = bootstrapLogin.token;
    async function create(role, email, departmentId) {
        const existing = await loader.json(config, config.target, `SELECT coalesce(json_agg(x),'[]') FROM (SELECT id,email FROM app_user WHERE tenant_id=${literal(tenant.id)} AND lower(email)=lower(${literal(email)})) x`);
        if (!existing.length) {
            const invite = await call("/convites", "POST", { email, roleKey: role, ...(departmentId ? { departmentId } : {}) }, adminToken);
            await call("/convites/aceitar", "POST", { token: invite.token, name: role + " local", password });
        }
        const session = await login(email);
        const access = await call("/usuarios/me/permissoes", "GET", null, session.token);
        if (!access.concessoes.some(grant => grant.papel === role) || access.concessoes.some(grant => grant.papel !== role)) throw Error("Conta de teste divergente ou concessão revogada; não sobrescrever automaticamente.");
        if (role === "TENANT_ADMIN" && access.permissoes.includes("USAGE:READ")) throw Error("Administrador ainda tem leitura analítica; confira a V021 e as concessões.");
        return { email, token: session.token };
    }
    const adminEmail = state.accounts?.TENANT_ADMIN || `admin.teste@${tenant.email_domain}`;
    const admin = await create("TENANT_ADMIN", adminEmail);
    adminToken = admin.token;
    state.accounts ||= {}; state.accounts.TENANT_ADMIN = admin.email; save();
    let department = await loader.json(config, config.target, `SELECT to_json(id) FROM department_departments WHERE tenant_id=${literal(tenant.id)} AND status='ACTIVE' ORDER BY id LIMIT 1`);
    if (!department) department = (await call("/departamentos", "POST", { name: "Equipe local" }, adminToken)).id;
    for (const role of ["EXECUTIVE", "MANAGER", "LEADER", "COLLABORATOR"]) {
        let email = state.accounts[role];
        if (!email && config.source && role !== "EXECUTIVE") {
            email = await loader.json(config, config.target, `SELECT to_json(u.email) FROM app_user u
                JOIN iam_legacy_user_links l ON l.tenant_id=u.tenant_id AND l.user_id=u.id
                JOIN iam_role_assignments a ON a.tenant_id=u.tenant_id AND a.user_id=u.id AND a.revoked_at IS NULL
                JOIN iam_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id
                WHERE u.tenant_id=${literal(tenant.id)} AND u.status='ACTIVE' AND r.name=${literal(role)}
                  AND u.password_hash=${literal(hash)} AND EXISTS(SELECT 1 FROM historical.ai_usage_logs h WHERE h.company_id=u.tenant_id::text AND h.employee_id=l.legacy_employee_id)
                ORDER BY u.email LIMIT 1`);
        }
        if (!email && config.source && role === "COLLABORATOR") throw Error("Nenhum colaborador ativo com histórico e senha mock deste lote. Hashes válidos da fonte foram preservados; confira a origem sem redefini-los automaticamente.");
        email ||= `${{ EXECUTIVE: "executivo", MANAGER: "gestor", LEADER: "lider", COLLABORATOR: "colaborador" }[role]}.teste@${tenant.email_domain}`;
        await create(role, email, ["MANAGER", "LEADER"].includes(role) ? department : null);
        state.accounts[role] = email; save();
    }
    const linked = await loader.json(config, config.target, `SELECT coalesce(json_agg(u.email),'[]') FROM app_user u
        JOIN iam_legacy_user_links l ON l.tenant_id=u.tenant_id AND l.user_id=u.id WHERE u.tenant_id=${literal(tenant.id)}`);
    return Object.entries(state.accounts).map(([role, email]) => ({ role, email, historicalIdentity: linked.includes(email),
        password: "Definida em SETUP_MOCK_PASSWORD do arquivo local" }));
}
async function setup(config) {
    await freePort(config.port);
    fs.mkdirSync(config.workDir, { recursive: true, mode: 0o700 });
    const stateFile = path.join(config.workDir, "setup-state.json");
    const state = fs.existsSync(stateFile) ? JSON.parse(fs.readFileSync(stateFile, "utf8")) : {};
    const save = () => loader.saveJson(stateFile, state);
    const sourceSignature = config.source ? { ...config.source, password: undefined } : null;
    if (state.source !== undefined && JSON.stringify(state.source) !== JSON.stringify(sourceSignature)) throw Error("Origem difere do setup anterior. Use o work-dir e a configuração originais.");
    if (state.target && state.target.database !== config.target.database) throw Error("Work-dir pertence a outro banco. Não reutilize manifestos entre máquinas.");
    console.log("Relatórios privados: " + config.workDir);
    await ensureDatabase(config, config.target);
    const targetIdentity = await loader.identity(config, config.target);
    if (state.target && JSON.stringify(state.target) !== JSON.stringify(targetIdentity)) throw Error("Work-dir pertence a outro banco. Não reutilize manifestos entre máquinas.");
    if (!state.target && await occupied(config, config.target)) throw Error("Destino já contém tabelas e não pertence a este setup. Use um banco vazio; nada será apagado.");
    state.target = targetIdentity; state.source = sourceSignature; save();
    if (config.source) {
        if (config["source-dump"]) {
            const digest = await fileHash(config["source-dump"]);
            if (state.dumpDigest && state.dumpDigest !== digest) throw Error("Dump difere da restauração anterior.");
            await ensureDatabase(config, config.source);
            if (!state.dumpDigest) {
                if (await occupied(config, config.source)) throw Error("Snapshot de destino já contém tabelas; restauração não será feita por cima. Use banco vazio.");
                await execute(config.restore, ["--no-owner", "--no-privileges", "--exit-on-error", "--dbname", config.source.database, path.resolve(config["source-dump"])], {
                    env: { PGHOST: config.source.host, PGPORT: String(config.source.port), PGUSER: config.source.user, PGPASSWORD: config.source.password, PGCONNECT_TIMEOUT: "10" }, log: path.join(config.workDir, "restore.log") });
                state.dumpDigest = digest; save();
            }
        }
        await loader.identity(config, config.source);
    }
    if (!config["skip-build"]) {
        console.log("Compilando o backend (os testes são executados separadamente)...");
        const log = path.join(config.workDir, "build.log");
        if (process.platform === "win32") await execute("powershell.exe", ["-NoProfile", "-NonInteractive", "-Command", "& $env:QSOFT_SETUP_MAVEN -B -DskipTests package; exit $LASTEXITCODE"], { env: { QSOFT_SETUP_MAVEN: config.maven }, log });
        else await execute(config.maven, ["-B", "-DskipTests", "package"], { log });
    }
    if (!fs.existsSync(config.jar)) throw Error("JAR não encontrado. Execute sem --skip-build.");
    const hashFile = await mockHash(config), hash = fs.readFileSync(hashFile, "utf8").trim();
    const backend = await startBackend(config);
    try {
        const expected = Math.max(...fs.readdirSync(path.join(root, "src/main/resources/db/migration")).map(name => Number(name.match(/^V(\d+)__/)?.[1] || 0)));
        const actual = await loader.json(config, config.target, "SELECT max(version::int) FROM flyway_schema_history WHERE success");
        if (actual !== expected) throw Error("JAR desatualizado: migrations diferem do código. Compile novamente.");
        let bootstrap, tenant;
        if (config.source) {
            const historical = { ...config, targetEnv: config.env, batch: "team-local", workDir: path.join(config.workDir, "historical"), apiUrl: `http://127.0.0.1:${config.port}` };
            fs.mkdirSync(historical.workDir, { recursive: true, mode: 0o700 });
            console.log("Validando e importando histórico da fonte somente para leitura...");
            const plan = await loader.inspect(historical);
            loader.saveJson(path.join(historical.workDir, "plan.json"), plan);
            const manifest = await loader.provision(historical, plan);
            await loader.importBatch(historical, plan);
            await identities.main(["--mode", "load", "--source-env", config["source-env"], "--target-env", config.envFile,
                "--manifest", path.join(historical.workDir, "manifest.json"), "--report", path.join(config.workDir, "identities-report.json"),
                "--password-hash-file", hashFile, "--confirm-target", config.target.database, "--psql", config.psql]);
            const credentials = loader.credentialsFor(plan.companies[0], fs.readFileSync(path.join(historical.workDir, "credentials.key"), "utf8"));
            bootstrap = { tenantSlug: credentials.slug, email: credentials.email, password: credentials.password };
            tenant = await loader.json(config, config.target, `SELECT row_to_json(t) FROM (SELECT id,slug,email_domain FROM iam_tenants WHERE id=${literal(manifest.mapping[plan.companies[0].company_id])}) t`);
        } else {
            bootstrap = { tenantSlug: "qsoft-local", email: "admin.teste@qsoft.local", password: config.env.SETUP_MOCK_PASSWORD };
            tenant = await loader.json(config, config.target, "SELECT row_to_json(t) FROM (SELECT id,slug,email_domain FROM iam_tenants WHERE slug='qsoft-local') t");
            if (!tenant) {
                const created = await api(config)("/tenants", "POST", { slug: bootstrap.tenantSlug, name: "Empresa local", emailDomain: "qsoft.local",
                    adminName: "Administrador local", adminEmail: bootstrap.email, adminPassword: bootstrap.password });
                tenant = { id: created.id, slug: created.slug, email_domain: "qsoft.local" };
            }
        }
        const accounts = await seedAccounts(config, tenant, bootstrap, hash, state, save);
        loader.saveJson(path.join(config.workDir, "accounts.local.json"), accounts);
        state.completed = true; state.migration = actual; save();
        console.log(JSON.stringify({ completed: true, migration: actual, historicalImported: Boolean(config.source), accounts: path.join(config.workDir, "accounts.local.json"),
            next: "Inicie o backend com scripts/run-local e o frontend com QSOFT_ENV_FILE apontando para o mesmo arquivo local." }));
    } finally {
        backend.kill();
        await Promise.race([new Promise(resolve => backend.once("exit", resolve)), new Promise(resolve => setTimeout(resolve, 5000))]);
    }
}
async function main(argv) {
    process.chdir(root);
    const config = options(argv);
    if (config["init-env"]) return initEnv(config["init-env"]);
    await setup(config);
}
if (require.main === module) main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exitCode = 1; });
module.exports = { options, initEnv, setup, privatePath, main };
