const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const loader = require("../../../scripts/db/historical-import");
const http = require("node:http");

test("ambiente de migração é lido sem executar shell e não depende do .env padrão", () => {
    const folder = fs.mkdtempSync(path.join(os.tmpdir(), "qsoft-env-"));
    const file = path.join(folder, "env.local");
    try {
        fs.writeFileSync(file, '\ufeffDB_NAME="novo"\nDB_PASSWORD=\'$(não executar)\'\n# comentário\n');
        assert.deepEqual(loader.readEnv(file), { DB_NAME: "novo", DB_PASSWORD: "$(não executar)" });
    } finally { fs.unlinkSync(file); fs.rmdirSync(folder); }
});

test("mapa deve ser completo e não pode fundir duas empresas num tenant", () => {
    const companies = [{ company_id: "A" }, { company_id: "B" }];
    const a = "00000000-0000-0000-0000-000000000001", b = "00000000-0000-0000-0000-000000000002";
    assert.throws(() => loader.validateMapping(companies, { A: a }), /completo/);
    assert.throws(() => loader.validateMapping(companies, { A: a, B: a }), /um-para-um/);
    assert.throws(() => loader.validateMapping(companies, { A: a, B: "não-uuid" }), /UUID/);
    loader.validateMapping(companies, { A: a, B: b });
});

test("conversão mantém IDs históricos e protege literais de empresa com aspas", () => {
    const query = loader.normalizedQuery("employees", { "EMP'1": "00000000-0000-0000-0000-000000000001" });
    assert.match(query, /WHEN 'EMP''1'/);
    assert.match(query, /WHEN 'true' THEN 1 WHEN 'false' THEN 0/);
    assert.match(query, /"employee_id"::text/);
    assert.match(loader.normalizedQuery("ai_usage_logs"), /replace\("timestamp"::text,' ','T'\)/);
});

test("credenciais de bootstrap são retomáveis por empresa e não entram no manifesto", () => {
    const company = { company_id: "A" };
    const a = loader.credentialsFor(company, "chave-local-de-teste");
    assert.deepEqual(a, loader.credentialsFor(company, "chave-local-de-teste"));
    assert.notDeepEqual(a, loader.credentialsFor({ company_id: "B" }, "chave-local-de-teste"));
    assert.ok(a.password.length >= 32);
    assert.ok(a.email.endsWith("@" + a.domain));
});

test("carga CLI exige confirmação do banco antes de consultar ou escrever", () => {
    const folder = fs.mkdtempSync(path.join(os.tmpdir(), "qsoft-args-"));
    const file = path.join(folder, "env.local");
    fs.writeFileSync(file, "DB_NAME=destino\nDB_USER=fixture\nDB_PASSWORD=fixture\n");
    try {
        const args = ["--mode", "load", "--source-env", file, "--target-env", file, "--work-dir", folder, "--batch", "teste"];
        assert.throws(() => loader.cliConfig(args), /Confirme explicitamente/);
        assert.equal(loader.cliConfig([...args, "--confirm-target", "destino"]).mode, "load");
    } finally { fs.unlinkSync(file); fs.rmdirSync(folder); }
});

test("configuração incompleta não pode recorrer ao banco padrão do psql", () => {
    assert.throws(() => loader.dbConfig({ DB_NAME: "destino" }), /Configuração incompleta/);
    assert.throws(() => loader.dbConfig({ DB_USER: "fixture", DB_PASSWORD: "fixture" }), /Configuração incompleta/);
});

test("manifesto de outra origem não é reutilizado mesmo com conteúdo idêntico", () => {
    const folder = fs.mkdtempSync(path.join(os.tmpdir(), "qsoft-origin-"));
    const file = path.join(folder, "manifest.json");
    const plan = { sourceSignature: "mesmo-conteudo", source: { database: "snapshot-novo" }, target: { database: "destino" } };
    fs.writeFileSync(file, JSON.stringify({ batch: "lote", sourceSignature: plan.sourceSignature,
        source: { database: "outra-origem" }, target: plan.target }));
    try { assert.throws(() => loader.loadManifest({ batch: "lote", workDir: folder }, plan), /outro lote, fonte ou destino/); }
    finally { fs.unlinkSync(file); fs.rmdirSync(folder); }
});

test("provisionamento não aceita identidade autenticada sem concessão administrativa", async () => {
    const folder = fs.mkdtempSync(path.join(os.tmpdir(), "qsoft-grant-"));
    const company = { company_id: "A", company_name: "A" };
    const seed = "chave-de-teste";
    fs.writeFileSync(path.join(folder, "credentials.key"), seed);
    const credential = loader.credentialsFor(company, seed);
    const server = http.createServer((request, response) => {
        response.setHeader("Content-Type", "application/json");
        if (request.url === "/api/v1/tenants") { response.statusCode = 201; response.end("{}"); }
        else if (request.url === "/api/v1/auth/login") response.end(JSON.stringify({ token: "fixture-token" }));
        else if (request.url === "/api/v1/tenants/me") response.end(JSON.stringify({ id: "00000000-0000-0000-0000-000000000001", slug: credential.slug }));
        else response.end(JSON.stringify({ permissoes: [], concessoes: [] }));
    });
    await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
    const port = server.address().port;
    try {
        await assert.rejects(loader.provision({ workDir: folder, apiUrl: `http://127.0.0.1:${port}`, targetEnv: { SERVER_PORT: String(port) }, batch: "test" },
            { companies: [company], sourceSignature: "test", target: {} }), /sem concessão/);
        assert.equal(fs.existsSync(path.join(folder, "manifest.json")), false);
    } finally {
        await new Promise(resolve => server.close(resolve));
        fs.unlinkSync(path.join(folder, "credentials.key")); fs.rmdirSync(folder);
    }
});
