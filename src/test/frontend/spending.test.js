"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const ejs = require("ejs");
const C = require("../../main/resources/static/public/js/pages/chat-core");

test("orçamento: página separada removida e acesso legado leva ao dashboard", async t => {
    const app = require("express")();
    app.use(require("../../main/resources/static/routes/departamentos.routes"));
    const server = app.listen(0, "127.0.0.1");
    await new Promise(resolve => server.once("listening", resolve));
    t.after(() => new Promise(resolve => server.close(resolve)));
    const base = `http://127.0.0.1:${server.address().port}`;
    assert.equal((await fetch(`${base}/orcamento-ia`)).status, 404);
    const legacy = await fetch(`${base}/onboarding/departamentos/orcamentos`, { redirect: "manual" });
    assert.equal(legacy.status, 302);
    assert.equal(legacy.headers.get("location"), "/dashboard");
});

test("chat: consumo pessoal não oferece configuração de orçamento", () => {
    const fs = require("node:fs");
    const source = fs.readFileSync(path.resolve(__dirname, "../../main/resources/static/public/js/pages/chat.js"), "utf8");
    assert.doesNotMatch(source, /Configurar orçamento e alerta|href = "\/orcamento-ia"/);
    assert.match(source, /Saldo disponível/);
});

test("chat: erros financeiros distinguem saldo, atribuição e preço", () => {
    assert.match(C.errorMessage({ status: 409, data: { code: "SPENDING_INSUFFICIENT" } }), /Saldo insuficiente/);
    assert.match(C.errorMessage({ status: 409, data: { code: "SPENDING_NOT_CONFIGURED" } }), /executivo/);
    assert.match(C.errorMessage({ status: 409, data: { code: "SPENDING_PRICE_MISSING" } }), /moeda e a taxa/);
});

test("orçamento: página distingue distribuição do executivo e alerta do gestor", async () => {
    const file = path.resolve(__dirname, "../../main/resources/static/modules/departamentos/views/spending-panel.ejs");
    const executive = await ejs.renderFile(file, { embedded: true, permissions: new Set(["SPENDING_BUDGET:MANAGE"]), contentFor: () => "" });
    assert.match(executive, /data-executive="true"/);
    assert.match(executive, /data-manager="false"/);
    const manager = await ejs.renderFile(file, { embedded: true, permissions: new Set(["SPENDING_ALERT:MANAGE"]), contentFor: () => "" });
    assert.match(manager, /data-executive="false"/);
    assert.match(manager, /data-manager="true"/);
});

test("dashboards: modal financeiro aparece somente para executivo e gestor autorizados", async () => {
    const layouts = require("../../main/resources/static/modules/dashboard/dashboards.layouts");
    const file = path.resolve(__dirname, "../../main/resources/static/modules/dashboard/views/index.ejs");
    for (const profile of ["executivo", "gestor", "lider", "colaborador", "administrador"]) {
        const html = await ejs.renderFile(file, {
            profile, panel: layouts[profile], indicatorReason: () => "", contentFor: () => "", demoReturn: false,
            canManageBudget: profile === "executivo", canManageSpending: profile === "executivo",
            canManageSpendingAlert: profile === "gestor"
        });
        if (["executivo", "gestor"].includes(profile)) {
            assert.match(html, /data-spending-open/);
            assert.match(html, /<dialog id="spending-modal"/);
            assert.match(html, /data-spending-close/);
            assert.ok(html.indexOf('data-spending-open') < html.indexOf('aria-label="Fonte dos indicadores"'),
                'a configuração financeira deve aparecer antes dos indicadores');
            assert.equal((html.match(/data-spending-open/g) || []).length, 1);
        } else assert.doesNotMatch(html, /<dialog id="spending-modal"/);
    }
});

test("dashboards: controller exige permissão financeira mesmo no perfil executivo", () => {
    const controller = require("../../main/resources/static/modules/dashboard/dashboards.controller");
    for (const authorized of [false, true]) {
        let rendered;
        controller.render({ params: {} }, {
            locals: { dashboardProfile: "executivo", permissions: new Set(authorized ? ["SPENDING_BUDGET:MANAGE"] : []) },
            render: (_view, data) => { rendered = data; }
        });
        assert.equal(rendered.canManageSpending, authorized);
        assert.equal(rendered.canManageSpendingAlert, false);
    }
});

test("orçamento: limites são consulta por grupo, sem campos de atribuição individual", async () => {
    const file = path.resolve(__dirname, "../../main/resources/static/modules/departamentos/views/spending-panel.ejs");
    const html = await ejs.renderFile(file, { embedded: true, permissions: new Set(["SPENDING_BUDGET:MANAGE"]), contentFor: () => "" });
    assert.match(html, /Limites calculados por hierarquia e departamento/);
    assert.match(html, /Limite por pessoa/);
    const fs = require("node:fs");
    const source = fs.readFileSync(path.resolve(__dirname, "../../main/resources/static/public/js/pages/spending.js"), "utf8");
    assert.match(source, /allocations: \[\]/);
    assert.doesNotMatch(source, /employeeInputs/);
});
