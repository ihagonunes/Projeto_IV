const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const source = fs.readFileSync(path.join(__dirname, "../../main/resources/static/public/js/pages/dashboard-historical.js"), "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));
async function page(respond, personal = false) {
    const elements = new Map(), calls = [];
    const create = () => ({ value: "", hidden: false, textContent: "", children: [], events: {},
        appendChild(child) { this.children.push(child); }, replaceChildren() { this.children = []; },
        setAttribute() {}, checkValidity() { return true; }, addEventListener(name, fn) { this.events[name] = fn; } });
    const get = id => { if (!elements.has(id)) elements.set(id, create()); return elements.get(id); };
    get("metrics-from").value = "2026-07-01";
    get("metrics-to").value = "2026-07-31";
    get("metrics-filters").dataset = { scope: personal ? "self" : "authorized" };
    const data = { from: "2026-07-01", to: "2026-07-31", coverage: { from: "2025-06-01", to: "2026-07-31" },
        activeUsers: 2, departments: [{ id: "D1", name: "<Departamento>" }], models: [{ id: "M1", name: "Modelo" }],
        series: [{ period: "2026-07-01", requests: 20 }], costs: [{ name: "<Modelo>", requests: 20, inputTokens: 1000, outputTokens: 500, estimatedCostUsd: 4, unpricedRecords: 0 }] };
    vm.runInNewContext(source, { document: { getElementById: get, createElement: create }, URLSearchParams,
        window: { Auth: { requireAuth: () => true }, ApiClient: { async get(url) { calls.push(url); return respond ? respond(url, data) : data; } } } });
    await tick();
    return { get: id => get(id === "department" ? "dashboard-area" : `${["from", "to", "model", "user", "filters"].includes(id) ? "metrics" : "historical"}-${id}`), calls };
}
test("histórico segue datas e filtros da sidebar e exibe USD sem interpretar HTML", async () => {
    const p = await page();
    assert.equal(p.get("to").value, "2026-07-31");
    assert.equal(p.get("requests").textContent, "20");
    assert.match(p.get("cost").textContent, /US\$/);
    assert.equal(p.get("costs").children[0].children[0].textContent, "<Modelo>");
    const initial = new URL(p.calls[0], "http://test");
    assert.equal(initial.searchParams.has("from"), false);
    assert.equal(initial.searchParams.has("to"), false);
    p.get("model").value = "historical:M1"; p.get("department").value = "historical:D1";
    p.get("filters").events.submit({ preventDefault() {} }); await tick();
    const url = new URL(p.calls.at(-1), "http://test");
    assert.equal(url.searchParams.get("modelId"), "M1");
    assert.equal(url.searchParams.get("departmentId"), "D1");
});
test("IDs operacionais não são enviados como filtros históricos nem ignorados", async () => {
    const p = await page();
    p.get("model").value = "uuid-operacional";
    p.get("filters").events.submit({ preventDefault() {} }); await tick();
    assert.equal(p.calls.length, 1);
    assert.equal(p.get("requests").textContent, "—");
    assert.match(p.get("message").textContent, /não possui vínculo/);
});
test("preço ausente é indisponível e não zero", async () => {
    const p = await page((url, data) => ({ ...data, costs: [{ ...data.costs[0], estimatedCostUsd: null, unpricedRecords: 1 }] }));
    assert.equal(p.get("cost").textContent, "Indisponível");
    assert.match(p.get("message").textContent, /parcial/);
});
test("403 remove conteúdo e explica o escopo necessário", async () => {
    const p = await page(() => { throw { status: 403 }; });
    assert.equal(p.get("content").hidden, true);
    assert.match(p.get("message").textContent, /Sem permissão/);
    assert.equal(p.get("costs").children.length, 0);
});

test("colaborador usa histórico pessoal e período da própria fonte, sem ID do cliente", async () => {
    const p = await page(null, true);
    assert.match(p.calls[0], /historical\/me\?/);
    assert.equal(new URL(p.calls[0], "http://test").searchParams.has("userId"), false);
    assert.equal(p.get("to").value, "2026-07-31");
});
test("intervalo inválido limpa dados e não consulta", async () => {
    const p = await page();
    p.get("from").value = "2026-08-01";
    p.get("filters").events.submit({ preventDefault() {} }); await tick();
    assert.equal(p.calls.length, 1);
    assert.equal(p.get("requests").textContent, "—");
});
