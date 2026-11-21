"use strict";
const test = require("node:test"), assert = require("node:assert/strict");
const C = require("../../main/resources/static/public/js/pages/dashboard-visual-core");

test("gráficos preservam custo desconhecido, zero e tokens incompletos", () => {
    assert.equal(C.value({ cost: null }, "cost"), null);
    assert.equal(C.value({ cost: 0 }, "cost"), 0);
    assert.equal(C.value({ input: 10, output: null }, "tokens"), null);
    assert.equal(C.value({ input: 10, output: 3 }, "tokens"), 13);
    const series = C.normalizeSeries("HISTORICO", { series: [{ period: "2026-06-01", estimatedCostUsd: null, unpricedRecords: 2 }] });
    assert.equal(series[0].cost, null);
    assert.equal(series[0].unpriced, 2);
});

test("resumo monetário preserva custos pequenos e detalhes mantêm precisão", () => {
    const panels = require("../../main/resources/static/public/js/pages/dashboard-panels-core");
    assert.match(panels.money(0.003, "HISTORICO", 2), /^< .*0,01/);
    assert.match(panels.money(0.003, "HISTORICO"), /0,003/);
    assert.doesNotMatch(panels.money(0, "HISTORICO", 2), /^</);
    assert.equal(panels.money(null, "HISTORICO", 2), "Sem dado");
    assert.match(panels.money(0.003, "OPERACIONAL", 2), /^< .*moeda não informada/);
});

test("tempo é proporcional e lacunas não viram zero nem linhas contínuas", () => {
    const points = C.points([
        { period: "2026-06-04", cost: 4 }, { period: "2026-06-01", cost: 1 },
        { period: "2026-06-05", cost: null }, { period: "2026-06-06", cost: 0 }
    ], "cost");
    const geometry = C.geometry([{ points }], "2026-06-01", "2026-06-06");
    assert.equal(Math.round(geometry.x(Date.parse("2026-06-04")) - geometry.x(Date.parse("2026-06-01"))), Math.round(558 * 3 / 5));
    assert.deepEqual(C.segments(points, geometry).map(group => group.length), [1, 1, 1]);
    assert.equal(points.length, 4);
    const negative = C.geometry([{ points: [{ time: Date.parse("2026-06-01"), value: -2 }, { time: Date.parse("2026-06-02"), value: 3 }] }], "2026-06-01", "2026-06-02");
    assert.ok(negative.y(-2) > negative.y(3));
});

test("consulta por modelo mantém escopos originais e usa endpoint pessoal quando necessário", () => {
    const entry = { from: "2026-06-01", to: "2026-06-30", params: "departmentId=dep-a&userId=user-a&modelId=old" };
    const endpoint = C.endpoint("HISTORICO", true, entry, "model-a");
    assert.ok(endpoint.startsWith("/v1/observability/metrics/historical/me?"));
    const params = new URLSearchParams(endpoint.split("?")[1]);
    assert.equal(params.get("departmentId"), "dep-a");
    assert.equal(params.get("userId"), "user-a");
    assert.equal(params.get("modelId"), "model-a");
    assert.equal(params.get("from"), entry.from);
    assert.equal(params.has("tenantId"), false);
    assert.ok(C.endpoint("OPERACIONAL", true, entry, "m").startsWith("/v1/observability/metrics/me/usage?"));
    assert.ok(C.endpoint("OPERACIONAL", false, entry, "m").startsWith("/v1/observability/metrics/usage?"));
});

test("catálogo vem da fonte autorizada, inclui modelos sem uso e não mistura identidades", () => {
    const entry = { data: { models: [{ id: "legacy-m", name: "Histórico" }] }, names: new Map([["current-m", "Operacional"], ["no-use", "Sem uso"]]) };
    assert.deepEqual(C.catalog("HISTORICO", entry).map(m => m.id), ["legacy-m"]);
    assert.deepEqual(C.catalog("OPERACIONAL", entry).map(m => m.id), ["current-m", "no-use"]);
    assert.deepEqual(C.catalog("HISTORICO", {}), []);
});

test("comparação limita consultas concorrentes e resposta antiga não repinta outro recorte", async () => {
    const vm = require("node:vm"), fs = require("node:fs");
    class Element {
        constructor(tag = "div") { this.tag = tag; this.children = []; this.dataset = {}; this.listeners = {}; this.attrs = {}; this.style = {}; this.value = ""; this._text = ""; this.classList = { add() {}, remove() {} }; }
        append(...children) { this.children.push(...children); if (this.tag === "select" && !this.value && children[0]) this.value = children[0].value; }
        replaceChildren(...children) { this.children = children; this._text = ""; if (this.tag === "select") this.value = ""; }
        setAttribute(key, value) { this.attrs[key] = String(value); }
        addEventListener(name, fn) { this.listeners[name] = fn; }
        dispatchEvent(event) { this.dispatched = event; return true; }
        set textContent(value) { this._text = String(value); this.children = []; }
        get textContent() { return this._text + this.children.map(c => c.textContent || "").join(""); }
    }
    const ids = new Map();
    for (const id of ["metrics-filters", "metrics-from", "metrics-to", "dashboard-source", "dashboard-timeline-metric", "dashboard-timeline-model", "dashboard-timeline-plot", "dashboard-timeline-message", "dashboard-timeline-context", "dashboard-timeline-model-field"]) ids.set(id, new Element(id.includes("metric") || id.endsWith("model") ? "select" : "div"));
    ids.get("metrics-filters").dataset.scope = "self";
    ids.get("dashboard-timeline-metric").value = "cost";
    const buttons = ["general", "models", "single"].map(mode => { const e = new Element("button"); e.dataset.timelineMode = mode; return e; });
    const shortcut = new Element("button"); shortcut.dataset.dashboardDays = "7";
    const calls = [], document = { getElementById: id => ids.get(id), querySelectorAll: selector => selector === "[data-timeline-mode]" ? buttons : selector === "[data-dashboard-days]" ? [shortcut] : [],
        createElement: tag => new Element(tag), createElementNS: (ns, tag) => new Element(tag) };
    const window = { AtlasVisualCore: C, AtlasPanels: require("../../main/resources/static/public/js/pages/dashboard-panels-core"),
        ApiClient: { request: (endpoint, { signal }) => new Promise(resolve => calls.push({ endpoint, signal, resolve })) } };
    vm.runInNewContext(fs.readFileSync("src/main/resources/static/public/js/pages/dashboard-visuals.js", "utf8"), { window, document, AbortController, URLSearchParams, Event });
    const flush = () => new Promise(resolve => setImmediate(resolve));
    const models = Array.from({ length: 5 }, (_, i) => ({ id: "m" + i, name: "Modelo " + i }));
    const entry = { from: "2026-06-01", to: "2026-06-30", params: "departmentId=dep-a&userId=actor-a",
        data: { models, costs: [], series: [{ period: "2026-06-01", requests: 1, estimatedCostUsd: 1 }] } };
    window.AtlasDashboardVisuals.selectSource("HISTORICO", entry);
    ids.get("metrics-to").value = "2024-03-01";
    shortcut.listeners.click();
    assert.equal(ids.get("metrics-from").value, "2024-02-24");
    assert.equal(ids.get("metrics-filters").dispatched.type, "submit");
    buttons[1].listeners.click();
    assert.equal(calls.length, 3);
    for (const call of calls) {
        assert.ok(call.endpoint.startsWith("/v1/observability/metrics/historical/me?"));
        assert.ok(call.endpoint.includes("departmentId=dep-a"));
        assert.ok(call.endpoint.includes("userId=actor-a"));
    }
    window.AtlasDashboardVisuals.selectSource("OPERACIONAL", { error: "Carregando novo recorte" });
    assert.ok(calls.every(call => call.signal.aborted));
    for (const call of calls) call.resolve({ series: [{ period: "2026-06-01", estimatedCostUsd: 999 }] });
    await flush(); await flush();
    assert.equal(ids.get("dashboard-timeline-plot").children.length, 0);
    assert.equal(ids.get("dashboard-timeline-message").textContent, "Carregando novo recorte");
    assert.equal(calls.length, 3);
    const second = { ...entry, params: "departmentId=dep-b", data: { ...entry.data, models: models.slice(0, 1) } };
    window.AtlasDashboardVisuals.selectSource("HISTORICO", second);
    assert.equal(calls.length, 4);
    assert.ok(calls[3].endpoint.includes("departmentId=dep-b"));
    calls[3].resolve({ series: [{ period: "2026-06-01", estimatedCostUsd: 2 }] });
    await flush(); await flush();
    assert.ok(ids.get("dashboard-timeline-plot").textContent.includes("2,00"));
    assert.ok(!ids.get("dashboard-timeline-plot").textContent.includes("999"));
});
