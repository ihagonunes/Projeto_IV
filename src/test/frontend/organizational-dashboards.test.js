"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const express = require("express");
const layouts = require("express-ejs-layouts");
const base = path.resolve(__dirname, "../../main/resources/static");
const fixtures = require(path.join(base, "modules/dashboard/mocks/organizational.mock"));
const route = require(path.join(base, "routes/dash.routes"));
const select = (profile, period = "semanal", state = "normal") => fixtures.getDashboard({ profile, period, state });
const currency = value => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);

test("indicadores de Líder e Executivo conciliam com a mesma amostra", () => {
    const rows = fixtures.records.filter(row => row.date >= "2026-10-04" && row.date <= "2026-10-10");
    const team = rows.filter(row => row.department === "produto");
    const total = rows.reduce((sum, row) => sum + row.cost, 0);
    const teamTotal = team.reduce((sum, row) => sum + row.cost, 0);
    const leader = select("lider").data;
    const executive = select("executivo").data;
    assert.equal(leader.kpis[1].value, currency(teamTotal));
    assert.equal(executive.kpis[0].value, currency(total));
    assert.equal(leader.tables[1].rows.reduce((sum, row) => sum + Number(row[1].replace(/\D/g, "")), 0), team.reduce((sum, row) => sum + row.requests, 0));
    assert.equal(executive.charts[0].datasets[0].values.reduce((sum, value) => sum + value, 0).toFixed(2), total.toFixed(2));
    assert.equal(executive.tables[1].rows[0][1], executive.kpis[0].value);
});

test("período e cenário vazio afetam todos os painéis sem reaproveitar dados anteriores", () => {
    for (const profile of ["lider", "gestor", "executivo", "governanca"]) {
        const weekly = select(profile);
        const monthly = select(profile, "mensal");
        const empty = select(profile, "semanal", "vazio");
        assert.equal(weekly.management, true);
        assert.equal(weekly.meta.simulated, true);
        assert.ok(weekly.data.charts.length >= 2);
        assert.ok(weekly.data.tables.length >= 2);
        assert.ok(monthly.data.kpis.length >= 4);
        assert.deepEqual(empty.data, { kpis: [], charts: [], tables: [], notes: [], links: [] });
        assert.throws(() => select(profile, "semanal", "erro"), { demo: true });
    }
});

test("logs de Governança conciliam estados e orçamento só muda na prévia", () => {
    const admin = select("governanca", "mensal").data;
    assert.equal(admin.tables[0].rows.length, fixtures.attempts.length);
    assert.equal(admin.charts[0].datasets[0].values.reduce((sum, value) => sum + value, 0), fixtures.attempts.length);
    assert.equal(admin.links.length, 3);
    const manager = select("gestor").data;
    assert.equal(manager.tables[0].rows[0][2], manager.kpis[1].value);
    assert.match(manager.notes[0], /não será salvo/);
    const preview = fixtures.getDashboard({ profile: "gestor", period: "semanal", state: "normal", budgetPreview: 1600 }).data;
    assert.equal(preview.tables[0].rows[0][1], currency(1600));
    assert.equal(select("gestor").data.tables[0].rows[0][1], currency(1250));
});

test("quatro rotas novas renderizam e navegam para todos os painéis", async () => {
    const app = express();
    app.set("views", [path.join(base, "views"), path.join(base, "modules")]);
    app.set("view engine", "ejs");
    app.use(layouts);
    app.use(route);
    const server = app.listen(0, "127.0.0.1");
    await new Promise(resolve => server.once("listening", resolve));
    const url = `http://127.0.0.1:${server.address().port}`;
    try {
        const profiles = ["colaborador", "lider", "gestor", "executivo", "agentes", "governanca"];
        for (const profile of profiles) {
            const response = await fetch(`${url}/dashboards/${profile}`);
            assert.equal(response.status, 200);
            const html = await response.text();
            for (const destination of profiles) assert.ok(html.includes(`href="/dashboards/${destination}"`));
            if (["lider", "gestor", "executivo", "governanca"].includes(profile)) {
                assert.match(html, /management-chart-0/);
                assert.match(html, /Dados simulados/);
                assert.doesNotMatch(html, /Visibilidade em definição/);
            }
        }
        const empty = await fetch(`${url}/dashboards/governanca?estado=vazio`);
        assert.match(await empty.text(), /Nenhum registro neste cenário/);
        const error = await fetch(`${url}/dashboards/gestor?estado=erro`);
        assert.equal(error.status, 503);
        const preview = await fetch(`${url}/dashboards/gestor?limite=1600&periodo=semanal`);
        assert.match(await preview.text(), /R\$\s*1\.600,00/);
    } finally {
        await new Promise(resolve => server.close(resolve));
    }
});
