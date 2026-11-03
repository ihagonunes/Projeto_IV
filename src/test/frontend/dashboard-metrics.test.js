const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const source = fs.readFileSync(path.join(__dirname, "../../main/resources/static/public/js/pages/dashboard-metrics.js"), "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));

async function dashboard(role = "COLLABORATOR", respond, personal = false) {
    const elements = new Map();
    const element = () => ({ value: "", textContent: "", hidden: true, disabled: true, children: [], events: {},
        appendChild(child) { this.children.push(child); }, replaceChildren() { this.children = []; },
        setAttribute() {}, addEventListener(event, handler) { this.events[event] = handler; }, checkValidity() { return true; } });
    const get = id => { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); };
    get("metrics-filters").dataset = { scope: personal ? "self" : "authorized" };
    const calls = [];
    const usage = { series: [{ period: "2026-10-01", requests: 3 }], activeUsers: 2 };
    const costs = [{ chave: "model-1", requisicoes: 3, tokensEntrada: 100, tokensSaida: 50, custoEstimado: 0.125 }];
    const client = { async get(url) {
        calls.push(url);
        if (url.includes("permissoes")) return { permissoes: ["USAGE:READ", "USER:READ"], concessoes: [{ papel: role }] };
        if (url.includes("models/dashboard")) return [{ id: "model-1", name: "<Modelo>" }];
        if (url === "/v1/departamentos") return [{ id: "dep-1", name: "Engenharia" }];
        if (url.includes("/usuarios?")) return { content: [{ id: "user-1", name: "Pessoa", email: "p@example.test" }] };
        if (respond) return respond(url);
        return url.includes("/usage?") ? usage : costs;
    } };
    vm.runInNewContext(source, { window: { ApiClient: client, Auth: { requireAuth: () => true } },
        document: { getElementById: get, createElement: element }, URLSearchParams, Date,
        setTimeout: callback => { Promise.resolve().then(callback); return 1; }, clearTimeout() {} });
    await tick();
    return { get: id => get(`metrics-${id}`), raw: get, calls, client };
}

test("default de 30 dias e agregados usam o mesmo recorte e contrato da API", async () => {
    const page = await dashboard();
    const from = new Date(page.get("from").value + "T00:00:00Z"), to = new Date(page.get("to").value + "T00:00:00Z");
    assert.equal((to - from) / 86400000, 30);
    assert.equal(page.get("user-field").hidden, true);
    assert.equal(page.get("requests").textContent, "3");
    assert.equal(page.get("tokens").textContent, "100 / 50");
    assert.equal(page.get("costs").children[0].children[0].textContent, "<Modelo>");
    page.get("model").value = "model-1";
    await page.get("model").events.change();
    const [usage, costs] = page.calls.slice(-2).map(url => new URL(url, "http://test"));
    assert.equal(usage.searchParams.get("modelId"), "model-1");
    assert.equal(costs.searchParams.get("modelId"), "model-1");
    assert.equal(usage.searchParams.get("from"), costs.searchParams.get("from"));
    assert.equal(costs.searchParams.get("groupBy"), "MODEL");
});

test("filtro operacional carrega departamentos autorizados e envia o ID nas duas consultas", async () => {
    const page = await dashboard("MANAGER");
    const department = page.raw("dashboard-area");
    assert.equal(department.disabled, false);
    assert.deepEqual(department.children.map(item => [item.value, item.textContent]), [["dep-1", "Engenharia"]]);
    department.value = "dep-1";
    await department.events.change();
    const [usage, costs] = page.calls.slice(-2).map(url => new URL(url, "http://test"));
    assert.equal(usage.searchParams.get("departmentId"), "dep-1");
    assert.equal(costs.searchParams.get("departmentId"), "dep-1");
});

test("dashboard pessoal usa endpoints me e oculta filtro de terceiros mesmo para admin", async () => {
    const page = await dashboard("EXECUTIVE", null, true);
    assert.equal(page.get("user-field").hidden, true);
    assert.equal(page.raw("dashboard-area").disabled, true);
    assert.ok(!page.calls.includes("/v1/departamentos"));
    assert.equal(page.calls.filter(url => url.includes("/metrics/me/")).length, 2);
    assert.ok(!page.calls.some(url => url.includes("userId=")));
});

test("período inválido não consulta a API nem mantém métricas antigas", async () => {
    const page = await dashboard();
    const count = page.calls.length;
    page.get("from").value = "2026-12-31";
    page.get("to").value = "2026-01-01";
    await page.get("model").events.change();
    assert.equal(page.calls.length, count);
    assert.equal(page.get("requests").textContent, "—");
    assert.match(page.get("message").textContent, /período válido/);
});

test("seleção histórica na sidebar não é enviada à API operacional como UUID", async () => {
    const page = await dashboard();
    const count = page.calls.length;
    page.get("model").value = "historical:M1";
    await page.get("model").events.change();
    assert.equal(page.calls.length, count);
    assert.equal(page.get("requests").textContent, "—");
    assert.match(page.get("message").textContent, /não possui vínculo/);
});

test("autocomplete só para gestor/executivo, seleção envia ID em ambas as consultas", async () => {
    for (const role of ["COLLABORATOR", "LEADER", "TENANT_ADMIN"]) {
        assert.equal((await dashboard(role)).get("user-field").hidden, true);
    }
    for (const role of ["MANAGER", "EXECUTIVE"]) {
        const page = await dashboard(role);
        assert.equal(page.get("user-field").hidden, false);
        page.get("user").value = "Pe";
        page.get("user").events.input();
        await tick();
        assert.ok(page.calls.some(url => url.includes("/usuarios?name=Pe")));
        page.get("user").value = "Pessoa (p@example.test)";
        page.get("user").events.input();
        page.get("user").events.change();
        await tick();
        assert.ok(page.calls.slice(-2).every(url => url.includes("userId=user-1")));
    }
});

test("respostas antigas não sobrescrevem o recorte mais recente e falha limpa dados", async () => {
    const page = await dashboard();
    const pending = [];
    page.client.get = url => new Promise((resolve, reject) => pending.push({ resolve, reject }));
    page.get("model").value = "model-1";
    const old = page.get("model").events.change();
    page.get("model").value = "";
    const latest = page.get("model").events.change();
    pending[2].resolve({ series: [], activeUsers: 0 }); pending[3].resolve([]);
    await latest;
    pending[0].resolve({ series: [{ period: "old", requests: 9 }], activeUsers: 9 }); pending[1].resolve([]);
    await old;
    assert.equal(page.get("requests").textContent, "—");
    assert.equal(page.get("message").textContent, "Sem dados no período.");
    const failure = page.get("model").events.change();
    pending[4].reject({ status: 403 }); pending[5].resolve([]);
    await failure;
    assert.equal(page.get("requests").textContent, "—");
    assert.match(page.get("message").textContent, /Sem permissão/);
});
