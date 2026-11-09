"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const C = require("../../main/resources/static/public/js/pages/dashboard-panels-core");
const E = require("../../main/resources/static/public/js/pages/dashboard-engagement-core");

test("fontes separadas: preço ausente não vira zero; USD histórico e moeda operacional não presumida", () => {
    const data = C.normalize("HISTORICO", { activeUsers: 1, series: [{ period: "2026-07-01", requests: 10, inputTokens: 20, outputTokens: 30 }],
        costs: [{ modelId: "m", name: "Modelo", requests: 10, inputTokens: 20, outputTokens: 30, estimatedCostUsd: null, unpricedRecords: 1 }] });
    assert.equal(data.cost, null);
    assert.equal(data.tokens, 50);
    assert.equal(data.unpriced, 1);
    assert.equal(C.money(null, "HISTORICO"), "Sem dado");
    assert.match(C.money(1, "HISTORICO"), /US\$/);
    assert.match(C.money(1, "OPERACIONAL"), /moeda não informada/);
    const zero = C.normalize("OPERACIONAL", { usage: { activeUsers: 1, series: [] }, costs: [{ custoEstimado: 0, tokensEntrada: 0, tokensSaida: 0 }] });
    assert.equal(zero.cost, 0);
});

test("comparação usa duas janelas completas de sete dias, preserva custo desconhecido e ignora datas externas", () => {
    const result = C.recentComparison([
        { period: "2026-07-18", cost: 999 }, { period: "2026-07-19", cost: 2 },
        { period: "2026-07-25", cost: 3 }, { period: "2026-07-26", cost: 4 },
        { period: "2026-08-02", cost: 999 }
    ], "2026-07-01", "2026-08-01");
    assert.deepEqual(result.map(row => row.cost), [5, 4]);
    assert.equal(C.recentComparison([], "2026-07-30", "2026-08-01"), null);
    assert.equal(C.recentComparison([], "2026-07-01", "2026-08-01")[0].cost, null);
});

class Element {
    constructor(dataset = {}) { this.dataset = dataset; this.children = []; this.listeners = {}; this.hidden = false; this.textContent = ""; }
    append(...items) { this.children.push(...items); }
    replaceChildren(...items) { this.children = items; this.textContent = ""; }
    addEventListener(name, fn) { this.listeners[name] = fn; }
    setAttribute() {} removeAttribute() {}
    get classList() { return { add() {}, remove() {} }; }
    querySelector(selector) { return this.parts?.[selector] || null; }
}
function page(profile = "colaborador") {
    const elements = new Map();
    for (const id of ["metrics-filters", "dashboard-main", "dashboard-source", "dashboard-source-status", "engagement-source", "metrics-from", "metrics-to", "metrics-model", "metrics-user"])
        elements.set(id, new Element());
    elements.get("dashboard-main").dataset.profile = profile;
    elements.get("dashboard-source").value = "HISTORICO";
    elements.get("engagement-source").value = "HISTORICO";
    elements.get("metrics-from").value = "2026-07-01";
    elements.get("metrics-to").value = "2026-07-31";
    elements.get("metrics-user").value = "";
    const cards = ["Meu consumo de tokens", "Meus AI Credits", "Meu custo registrado", "Gastos da empresa"].map(label => {
        const element = new Element({ dashboardKpi: label }); element.parts = { strong: new Element(), p: new Element() }; return element;
    });
    const window = { AtlasPanels: C, AtlasEngagement: E, Auth: { requireAuth: () => false }, ApiClient: { get: async () => { throw Error("Consulta inesperada"); } } };
    const document = { getElementById: id => elements.get(id), createElement: () => new Element(),
        querySelectorAll: selector => selector === "[data-dashboard-kpi]" ? cards : [] };
    vm.runInNewContext(fs.readFileSync("src/main/resources/static/public/js/pages/dashboard-panels.js", "utf8"), { window, document, URLSearchParams, Map, Date });
    const value = label => cards.find(item => item.dataset.dashboardKpi === label).parts.strong.textContent;
    return { window, elements, cards, value };
}
const historical = { from: "2026-07-01", to: "2026-07-31", data: { activeUsers: 1, series: [],
    costs: [{ inputTokens: 30, outputTokens: 20, estimatedCostUsd: 2, unpricedRecords: 0 }] } };

test("cards conectados limpam valores durante recarga/403 e nunca usam histórico como fallback operacional", () => {
    const p = page();
    p.window.AtlasDashboard.publish("HISTORICO", historical);
    assert.equal(p.value("Meu consumo de tokens"), "50");
    p.elements.get("dashboard-source").value = "OPERACIONAL";
    p.elements.get("dashboard-source").listeners.change();
    assert.equal(p.value("Meu consumo de tokens"), "Sem dado");
    p.window.AtlasDashboard.publish("OPERACIONAL", { error: "Sem permissão" });
    assert.equal(p.value("Meu consumo de tokens"), "Sem dado");
    assert.equal(p.elements.get("dashboard-source-status").textContent, "Sem permissão");
    p.elements.get("dashboard-source").value = "HISTORICO";
    p.window.AtlasDashboard.publish("HISTORICO", { error: "Carregando" });
    assert.equal(p.value("Meu custo registrado"), "Sem dado");
});

test("Credits mantêm a fonte e mês próprios após outra fonte de consumo carregar", () => {
    const p = page();
    p.window.AtlasDashboard.publish("engagement", { credits: { sourceKind: "HISTORICO", mes: "2026-07", status: "FECHADO", itens: [{ score: 80, dataStatus: "COMPLETE" }] } });
    p.window.AtlasDashboard.publish("HISTORICO", historical);
    assert.equal(p.value("Meus AI Credits"), "80,00%");
    p.window.AtlasDashboard.publish("engagement-error", "Sem permissão");
    assert.equal(p.value("Meus AI Credits"), "Sem dado");
});

test("gestor não ganha total corporativo a partir de seu consumo departamental", () => {
    const p = page("gestor");
    p.window.AtlasDashboard.publish("HISTORICO", historical);
    assert.equal(p.value("Gastos da empresa"), "Sem dado");
    assert.match(p.cards.find(item => item.dataset.dashboardKpi === "Gastos da empresa").parts.p.textContent, /limitado à área/);
});
