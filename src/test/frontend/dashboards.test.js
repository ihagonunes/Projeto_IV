"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const express = require("express");
const layouts = require("express-ejs-layouts");
const base = path.resolve(__dirname, "../../main/resources/static");
test("render dos cinco layouts com ações de sessão e filtros aguardando autenticação", async () => {
    const app = express();
    app.set("views", [path.join(base, "views"), path.join(base, "modules")]);
    app.set("view engine", "ejs");
    app.use(layouts);
    app.use(require(path.join(base, "routes/dashboard.routes")));
    const server = app.listen(0, "127.0.0.1");
    await new Promise(resolve => server.once("listening", resolve));
    const url = "http://127.0.0.1:" + server.address().port;
    try {
        for (const profile of ["colaborador", "lider", "gestor", "executivo", "administrador"]) {
            const response = await fetch(url + "/dashboard/" + profile);
            assert.equal(response.status, 200);
            const html = await response.text();
            assert.ok(!/prévia|preview/i.test(html));
            assert.match(html, /ATLAS/);
            assert.match(html, /<aside[\s\S]*id="metrics-from"[\s\S]*id="metrics-to"[\s\S]*<\/aside>/);
            assert.ok(!/href="\/dashboard(?:s)?\//.test(html));
            assert.match(html, /<body[^>]*>[\s\S]*<main/);
            assert.ok(!html.includes('chart.umd'));
            assert.equal(html.includes('/js/pages/dashboard-metrics.js'), profile !== 'colaborador');
            assert.ok(!html.includes('dashboard-data'));
            if (profile === 'gestor' || profile === 'executivo') {
                assert.match(html, /Benefício líquido projetado/);
                assert.match(html, /Ganho potencial estimado/);
                assert.match(html, /Cobertura das estimativas/);
            }
            if (profile === 'colaborador') {
                assert.match(html, /Meu retorno estimado/);
                assert.ok(!html.includes('Benefício líquido projetado'));
            }
            if (profile === 'lider') assert.match(html, /Retorno estimado da equipe/);
            if (profile === 'administrador') {
                assert.match(html, /Requisições com custo válido/);
                assert.ok(!html.includes('Benefício líquido projetado'));
            }
            assert.ok(!html.includes('LucroRealizado'));
            const controls = html.match(/<(?:button|select)\b[^>]*>/g) || [];
            assert.ok(controls.every(control => control.includes('disabled') || control.includes('id="dashboardLogout"') || control.includes('id="budget-notification-retry"') || /id="historical-(?:department|model|apply|retry)"/.test(control)));
            if (profile === 'colaborador') {
                assert.match(html, /data-scope="self"/);
                assert.ok(html.includes('dashboard-historical.js'));
                assert.ok(!html.includes('dashboard-budget-alerts.js'));
                assert.ok(html.includes('id="historical-content"'));
                assert.ok(!html.includes('id="budget-notification"'));
            } else {
                assert.match(html, /dashboard-historical.js/);
                assert.match(html, /id="historical-content" hidden/);
                assert.match(html, /id="budget-notification"[^>]*hidden/);
                assert.ok(html.indexOf('id="budget-notification"') < html.indexOf('id="metrics-requests"'));
            }
            assert.ok(!html.includes('id="historical-filters"'));
            assert.ok(!html.includes('id="historical-from"'));
            assert.ok(!html.includes('id="historical-model"'));
            assert.equal((html.match(/<form\b/g) || []).length, 1);
            assert.match(html, /id="metrics-user-field" hidden/);
            assert.match(html, /href="\/onboarding"/);
            assert.match(html, /id="dashboardLogout"/);
            assert.equal((await fetch(url + "/dashboard/" + profile)).status, 200);
            assert.match(html, /<html/);
            assert.ok(!html.includes("undefined"));
        }
        assert.equal((await fetch(url + "/dashboard")).status, 200);
        assert.equal((await fetch(url + "/dashboards")).status, 404);
        assert.equal((await fetch(url + "/dashboards/colaborador")).status, 404);
        const invalid = await fetch(url + "/dashboard/colaborador?periodo=%3Cscript%3E");
        assert.equal(invalid.status, 200);
        assert.ok(!(await invalid.text()).includes('value="<script>"'));
        assert.equal((await fetch(url + "/dashboard/desconhecido")).status, 404);
        const original = process.env.NODE_ENV;
        try {
            process.env.NODE_ENV = "production";
            assert.equal((await fetch(url + "/dashboards/agentes")).status, 404);
            const production = await (await fetch(url + "/dashboard/executivo")).text();
            assert.ok(!production.includes('aria-label="Layouts por perfil"'));
            assert.match(production, /<aside[\s\S]*id="dashboard-area"[\s\S]*<\/aside>/);
        } finally {
            if (original === undefined) delete process.env.NODE_ENV;
            else process.env.NODE_ENV = original;
        }
    } finally {
        await new Promise(resolve => server.close(resolve));
    }
});

test("botão Sair expira cookie no BFF, limpa storages e volta ao login", async () => {
    const vm = require("node:vm");
    const fs = require("node:fs");
    const storage = () => {
        const values = new Map([['authToken', 'token-de-teste'], ['qsoft.user', '{}'], ['preferencia', 'manter']]);
        return { getItem: key => values.get(key) || null, removeItem: key => values.delete(key) };
    };
    const local = storage();
    const session = storage();
    let click;
    const context = vm.createContext({
        window: { location: { href: '/dashboard' } }, localStorage: local, sessionStorage: session,
        fetch: async (url, config) => {
            assert.equal(url, '/api/v1/auth/logout');
            assert.equal(config.headers['X-Qsoft-Request'], '1');
            return { ok: true, text: async () => '' };
        },
        document: { getElementById: () => ({ addEventListener: (event, handler) => { if (event === 'click') click = handler; } }) }
    });
    for (const file of ['api-client.js', 'auth.js', 'session-actions.js']) {
        vm.runInContext(fs.readFileSync(path.join(base, 'public/js/core', file), 'utf8'), context);
    }
    await click();
    for (const store of [local, session]) {
        assert.equal(store.getItem('authToken'), null);
        assert.equal(store.getItem('qsoft.user'), null);
        assert.equal(store.getItem('preferencia'), 'manter');
    }
    assert.equal(context.window.location.href, '/login');
});
