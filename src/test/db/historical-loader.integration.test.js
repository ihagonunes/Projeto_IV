const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { spawn } = require("node:child_process");
const loader = require("../../../scripts/db/historical-import");
const { tables } = require("../../../scripts/db/historical-contract");
const literal = value => value == null ? "NULL" : "'" + String(value).replace(/'/g, "''") + "'";

test("carregador real em PostgreSQL: isolamento, atomicidade e retomada", { skip: !process.env.QSOFT_LOADER_TEST_CONFIG }, async t => {
    const settings = JSON.parse(process.env.QSOFT_LOADER_TEST_CONFIG);
    const config = { source: { ...settings, database: "qsoft_loader_source" }, target: { ...settings, database: "qsoft_loader_destination" },
        batch: "teste-loader", workDir: path.join(settings.workDir, "batch") };
    fs.mkdirSync(config.workDir, { recursive: true });
    const mapping = { "C'1": "00000000-0000-0000-0000-000000000001", C2: "00000000-0000-0000-0000-000000000002" };
    const ddl = ["CREATE SCHEMA staging;", ...Object.entries(tables).map(([table, columns]) =>
        `CREATE TABLE staging.${table} (${Object.entries(columns).map(([name, type]) => `"${name}" ${name === "active" ? "text" : type}`).join(",")});`)];
    const fixture = {
        companies: [{ company_id: "C'1", company_name: "Empresa d'Água" }, { company_id: "C2", company_name: "B" }],
        departments: [{ company_id: "C'1", department_id: "DA" }, { company_id: "C2", department_id: "DB" }],
        employees: [{ company_id: "C'1", department_id: "DA", employee_id: "EA", active: "True" }, { company_id: "C2", department_id: "DB", employee_id: "EB", active: "False" }],
        projects: [{ company_id: "C'1", project_id: "PA" }, { company_id: "C2", project_id: "PB" }],
        ai_tools: [{ ai_tool_id: "T" }], ai_models: [{ model_id: "M", ai_tool_id: "T" }],
        ai_usage_logs: [
            { usage_id: "UA", company_id: "C'1", department_id: "DA", employee_id: "EA", model_id: "M", ai_tool_id: "T", project_id: "PA", timestamp: "2026-01-31 23:59:59", request_count: 10, input_tokens: 100, output_tokens: 20 },
            { usage_id: "UB", company_id: "C2", department_id: "DB", employee_id: "EB", model_id: "M", ai_tool_id: "T", project_id: "PB", timestamp: "2026-02-01T00:00:00", request_count: 99, input_tokens: 9900, output_tokens: 99 }
        ],
        builds: [{ build_id: "B1", project_id: "PA", timestamp: "2026-01-01 00:00:00" }],
        deployments: [{ deployment_id: "D1", project_id: "PA" }],
        issues: [{ issue_id: "I1", project_id: "PA", employee_id: "EA" }],
        pull_requests: [{ pull_request_id: "P1", project_id: "PA", employee_id: "EA" }],
        ai_billing: [{ billing_id: "BI1", company_id: "C'1", model_id: "M", ai_tool_id: "T", total_cost: 1.25, currency: "USD" }],
        ai_budgets: [{ company_id: "C'1", year: 2026, month: 1, budget_amount: 100 }],
        ai_licenses: [{ company_id: "C'1", employee_id: "EA", ai_tool_id: "T" }],
        approved_ai_tools: [{ company_id: "C'1", ai_tool_id: "T" }],
        security_events: [{ event_id: "E1", company_id: "C'1", employee_id: "EA", ai_tool_id: "T" }],
        training_records: [{ training_id: "TR1", company_id: "C'1", employee_id: "EA", score: 90 }]
    };
    for (const [table, rows] of Object.entries(fixture)) for (const row of rows) {
        ddl.push(`INSERT INTO staging.${table} (${Object.keys(row).map(name => '"' + name + '"').join(",")}) VALUES (${Object.values(row).map(literal).join(",")});`);
    }
    await loader.psql(config, config.source, ddl.join("\n"), { write: true });
    await loader.psql(config, config.target, `INSERT INTO iam_tenants(id,slug,name,email_domain,status) VALUES
        ('${mapping["C'1"]}','a','A','a.test','ACTIVE'),('${mapping.C2}','b','B','b.test','ACTIVE');`, { write: true });
    const plan = await loader.inspect(config);
    function manifest() {
        loader.saveJson(path.join(config.workDir, "manifest.json"), { batch: config.batch, sourceSignature: plan.sourceSignature,
            source: plan.source, target: plan.target, mapping, state: "PROVISIONED" });
    }
    async function reset() {
        await loader.psql(config, config.target, "TRUNCATE " + Object.keys(tables).map(table => "historical." + table).join(",") + ";", { write: true });
        manifest();
    }
    await reset();

    await t.test("CLI executa o modo sem escrita do loader real", async () => {
        const envFile = database => Object.entries({ DB_HOST: settings.host, DB_PORT: settings.port, DB_USER: settings.user, DB_PASSWORD: settings.password, DB_NAME: database })
            .map(([key, value]) => key + "=" + value).join("\n");
        const sourceFile = path.join(settings.workDir, "source.env"), targetFile = path.join(settings.workDir, "target.env");
        fs.writeFileSync(sourceFile, envFile(config.source.database)); fs.writeFileSync(targetFile, envFile(config.target.database));
        const command = process.execPath;
        const args = ["scripts/db/historical-import.js", "--mode", "plan", "--source-env", sourceFile,
            "--target-env", targetFile, "--work-dir", config.workDir, "--batch", config.batch];
        const result = await new Promise((resolve, reject) => {
            const process = spawn(command, args, { windowsHide: true }); let output = "";
            process.stdout.on("data", data => { output += data; }); process.stderr.on("data", data => { output += data; });
            process.on("error", reject); process.on("close", code => resolve({ code, output }));
        });
        assert.equal(result.code, 0, result.output);
        assert.match(result.output, /"tables":17/);
        assert.equal(await loader.json(config, config.target, "SELECT count(*) FROM historical.companies;"), 0);
    });

    await t.test("carga converte ativos, normaliza tempo, preserva aspas e isola empresas", async () => {
        const result = await loader.importBatch(config, plan);
        assert.equal(result.result, "LOADED");
        const a = await loader.json(config, config.target, `SELECT json_build_object('requests',sum(request_count),'tokens',sum(input_tokens)) FROM historical.ai_usage_logs WHERE company_id='${mapping["C'1"]}';`);
        assert.deepEqual(a, { requests: 10, tokens: 100 });
        assert.equal(await loader.json(config, config.target, "SELECT active FROM historical.employees WHERE employee_id='EA';"), 1);
        assert.equal(await loader.json(config, config.target, "SELECT active FROM historical.employees WHERE employee_id='EB';"), 0);
        const timestamp = await loader.json(config, config.target, "SELECT to_json(timestamp) FROM historical.ai_usage_logs WHERE usage_id='UA';");
        assert.equal(timestamp, "2026-01-31T23:59:59");
        assert.equal(await loader.json(config, config.target, "SELECT count(*) FROM app_user;"), 0);
        assert.equal(await loader.json(config, config.target, "SELECT count(*) FROM observability_usage_records;"), 0);
    });

    await t.test("reexecutar o lote não duplica", async () => {
        assert.equal((await loader.importBatch(config, plan)).result, "ALREADY_LOADED");
        assert.equal(await loader.json(config, config.target, "SELECT count(*) FROM historical.ai_usage_logs;"), 2);
    });

    await t.test("mesma contagem com conteúdo diferente é rejeitada", async () => {
        await loader.psql(config, config.target, "UPDATE historical.ai_usage_logs SET request_count=1 WHERE usage_id='UA';", { write: true });
        await assert.rejects(loader.importBatch(config, plan), /não está vazio/);
        await reset();
    });

    await t.test("erro na última tabela reverte todas as tabelas anteriores", async () => {
        await assert.rejects(loader.importBatch(config, plan, { afterExport(files) {
            fs.appendFileSync(files.training_records, "linha,incompleta\n");
        } }), /psql falhou/);
        assert.equal(await loader.json(config, config.target, "SELECT count(*) FROM historical.companies;"), 0);
        assert.equal(await loader.json(config, config.target, "SELECT count(*) FROM historical.ai_usage_logs;"), 0);
    });

    await t.test("falha depois do commit é retomada sem reinserção", async () => {
        await assert.rejects(loader.importBatch(config, plan, { afterCommit() { throw new Error("falha simulada no manifesto"); } }), /simulada/);
        assert.equal((await loader.importBatch(config, plan)).result, "ALREADY_LOADED");
    });

    await t.test("origem/destino iguais, empresas sem mapa e ativos desconhecidos são bloqueados", async () => {
        await assert.rejects(loader.inspect({ ...config, target: config.source }), /mesmo banco/);
        manifest();
        const file = path.join(config.workDir, "manifest.json"), current = JSON.parse(fs.readFileSync(file));
        delete current.mapping.C2; loader.saveJson(file, current);
        await assert.rejects(loader.importBatch(config, plan), /completo/);
        await loader.psql(config, config.source, "UPDATE staging.employees SET active='desconhecido' WHERE employee_id='EA';", { write: true });
        await assert.rejects(loader.inspect(config), /Conversão não reconhecida/);
        await loader.psql(config, config.source, "UPDATE staging.employees SET active='True' WHERE employee_id='EA';", { write: true });
    });
});
