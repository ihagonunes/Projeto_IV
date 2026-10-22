"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const express = require("express");
const layouts = require("express-ejs-layouts");
const base = path.resolve(__dirname, "../../main/resources/static");
const mocks = require(path.join(base, "modules/dashboard/mocks/dashboards.mock"));
const { executions } = require(path.join(base, "modules/dashboard/mocks/agentes.mock"));
const route = require(path.join(base, "routes/dash.routes"));
const selection = { profile: "agentes", period: "mensal", state: "normal", agent: "" };

test("90 execuções coerentes, com falhas, diferentes modelos e etapas distintas", () => {
    assert.equal(executions.length, 90);
    assert.equal(new Set(executions.map(row => row.sessionId)).size, 90);
    const failures = executions.filter(row => row.status === "FAILED").length / executions.length;
    assert.ok(failures >= 0.05 && failures <= 0.10);
    assert.ok(new Set(executions.map(row => row.model)).size > 1);
    for (const row of executions) {
        assert.ok(row.totalMs >= 80 && row.totalMs <= 2000);
        assert.ok(row.ragMs + row.toolMs + row.llmMs <= row.totalMs);
        assert.ok(row.costUsd >= 0.01 && row.costUsd <= 0.05);
        assert.ok(Math.abs(row.costBrl - row.costUsd * 5.15) < 0.00001);
    }
});
test("semanal começa domingo; mensal e filtros mudam a amostra sem alterar custo diário", () => {
    const month = mocks.getDashboard(selection);
    const week = mocks.getDashboard({ ...selection, period: "semanal" });
    assert.equal(new Date(week.period.start + "T12:00:00Z").getUTCDay(), 0);
    assert.equal(new Date(week.period.end + "T12:00:00Z").getUTCDay(), 6);
    assert.equal(week.data.timeline.length, 7);
    assert.equal(month.data.timeline.length, 31);
    assert.equal(month.data.kpis.total, 90);
    assert.equal(week.data.kpis.total, 60);
    assert.equal(month.data.kpis.dailyCostBrl, week.data.kpis.dailyCostBrl);
    const filtered = mocks.getDashboard({ ...selection, agent: "politicas-demo" });
    assert.equal(filtered.data.kpis.total, 45);
    assert.ok(new Set(month.data.timeline.slice(0, 9).map(row => row.executions)).size > 1);
    assert.ok(filtered.data.executions.every(row => row.agentId === "politicas-demo"));
    assert.equal(mocks.dayOf("2026-10-04T01:00:00Z"), "2026-10-03");
});
test("KPIs conferem com registros e percentis não são a média", () => {
    const { data } = mocks.getDashboard(selection);
    const sorted = executions.map(row => row.totalMs).sort((a, b) => a - b);
    assert.equal(data.kpis.p50Ms, sorted[44]);
    assert.equal(data.kpis.p95Ms, sorted[85]);
    assert.equal(data.kpis.latencyMeanMs, Math.round(sorted.reduce((a, b) => a + b, 0) / 90));
    assert.ok(Math.abs(data.kpis.successPercent - 83 / 90 * 100) < 0.00001);
    assert.equal(data.timeline.reduce((total, row) => total + row.executions, 0), 90);
});
test("colaborador não recebe execuções gerenciais ou identidades de terceiros", () => {
    const result = mocks.getDashboard({ ...selection, profile: "colaborador" });
    assert.deepEqual(result.agents, []);
    assert.equal(result.data.executions, undefined);
    assert.equal(result.data.stages, undefined);
    const json = JSON.stringify(result);
    assert.ok(!json.includes("DEMO-"));
    assert.ok(!json.includes("userId"));
    assert.ok(result.data.history.every(row => row.id.startsWith("minha-demo")));
    for (const profile of ["lider", "gestor", "executivo"]) {
        const pending = mocks.getDashboard({ ...selection, profile });
        assert.equal(pending.data, null);
        assert.equal(pending.pending, true);
    }
});
test("vazio usa zeros ou ausência e erro não retorna dados antigos", () => {
    const { data } = mocks.getDashboard({ ...selection, state: "vazio" });
    assert.equal(data.kpis.total, 0);
    assert.equal(data.kpis.latencyMeanMs, null);
    assert.equal(data.kpis.successPercent, null);
    assert.equal(data.kpis.dailyCostBrl, 0);
    assert.deepEqual(data.executions, []);
    assert.throws(() => mocks.getDashboard({ ...selection, state: "erro" }));
});
test("render HTTP de todas as views, erro, filtros inválidos e bloqueio em produção", async () => {
    const app = express();
    app.set("views", [path.join(base, "views"), path.join(base, "modules")]);
    app.set("view engine", "ejs");
    app.use(layouts);
    app.use(route);
    const server = app.listen(0, "127.0.0.1");
    await new Promise(resolve => server.once("listening", resolve));
    const url = "http://127.0.0.1:" + server.address().port;
    try {
        for (const profile of ["colaborador", "lider", "gestor", "executivo", "agentes"]) {
            const response = await fetch(url + "/dashboards/" + profile);
            assert.equal(response.status, 200);
            const html = await response.text();
            assert.match(html, /Dados simulados/);
            assert.match(html, /<html/);
            assert.ok(!html.includes("undefined"));
        }
        const error = await fetch(url + "/dashboards/agentes?estado=erro");
        assert.equal(error.status, 503);
        assert.match(await error.text(), /Tentar novamente/);
        const empty = await fetch(url + "/dashboards/agentes?estado=vazio");
        assert.match(await empty.text(), /Nenhuma execução neste cenário/);
        const invalid = await fetch(url + "/dashboards/colaborador?periodo=%3Cscript%3E");
        assert.equal(invalid.status, 200);
        assert.ok(!(await invalid.text()).includes('value="<script>"'));
        assert.equal((await fetch(url + "/dashboards/administrador")).status, 404);
        const original = process.env.NODE_ENV;
        try {
            process.env.NODE_ENV = "production";
            assert.equal((await fetch(url + "/dashboards/agentes")).status, 404);
        } finally {
            if (original === undefined) delete process.env.NODE_ENV;
            else process.env.NODE_ENV = original;
        }
    } finally {
        await new Promise(resolve => server.close(resolve));
    }
});

test("dados do mesmo dia do colaborador são consistentes entre semana e mês", () => {
    const week = mocks.getDashboard({ ...selection, profile: "colaborador", period: "semanal" });
    const month = mocks.getDashboard({ ...selection, profile: "colaborador", period: "mensal" });
    for (const row of week.data.timeline) assert.deepEqual(row, month.data.timeline.find(item => item.label === row.label));
});
