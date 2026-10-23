const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const source = fs.readFileSync(path.join(__dirname, "../../main/resources/static/public/js/pages/dashboard-budget-alerts.js"), "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));
const item = (name, status, pct = null) => ({ departmentName: name, status, pctUsado: pct });
async function page(reply, authenticated = true) {
    const elements = new Map(), calls = [], events = {};
    const element = () => ({ hidden: true, textContent: "", children: [], events: {},
        appendChild(value) { this.children.push(value); }, replaceChildren() { this.children = []; },
        addEventListener(event, handler) { this.events[event] = handler; } });
    const get = id => { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); };
    const client = { async get(url) { calls.push(url); return reply(url); } };
    vm.runInNewContext(source, { document: { getElementById: get, createElement: element }, Date,
        window: { Auth: { isAuthenticated: () => authenticated }, ApiClient: client,
            addEventListener(event, handler) { events[event] = handler; } } });
    await tick();
    return { panel: get("budget-notification"), get: name => get(`budget-notification-${name}`), events, client, calls };
}

test("alerta está no mês UTC corrente, exibe badge e descrição expansível de todas as áreas críticas autorizadas", async () => {
    const p = await page(() => ({ itens: [item("Marketing", "EXCEDIDO", 125.5), item("Vendas", "ALERTA", 85), item("TI", "OK", 20)] }));
    assert.equal(p.calls[0], `/v1/observability/orcamentos?mes=${new Date().toISOString().slice(0, 7)}`);
    assert.equal(p.panel.hidden, false);
    assert.match(p.panel.className, /border-danger/);
    assert.equal(p.get("details").hidden, false);
    assert.equal(p.get("count").textContent, "2 alertas");
    assert.match(p.get("items").children[0].children[1].textContent, /Atenção: O departamento Marketing atingiu 125,5% do orçamento mensal alocado/);
    assert.equal(p.get("items").children.length, 3);
});

test("gestor recebe apenas as áreas que vieram da API, sem inferir escopo pelo perfil da URL", async () => {
    const p = await page(() => ({ itens: [item("Minha área", "ALERTA", 80)] }));
    assert.equal(p.get("items").children.length, 1);
    assert.match(p.get("items").children[0].children[1].textContent, /Minha área/);
    assert.equal(p.get("count").textContent, "1 alerta");
    assert.match(p.panel.className, /border-warning/);
});

test("403 oculta o painel e não expõe dados; usuário sem sessão não consulta a API", async () => {
    const denied = await page(() => { throw { status: 403 }; });
    assert.equal(denied.panel.hidden, true);
    assert.equal(denied.get("items").children.length, 0);
    const anonymous = await page(() => { throw new Error("Não deve consultar"); }, false);
    assert.equal(anonymous.calls.length, 0);
});

test("sem orçamento e orçamento zero com consumo não exibem percentuais inventados", async () => {
    const p = await page(() => ({ itens: [item("Sem limite", "SEM_ORCAMENTO"), item("Zero", "EXCEDIDO")] }));
    const rows = p.get("items").children;
    assert.match(rows[0].children[1].textContent, /orçamento mensal igual a zero/);
    assert.match(rows[1].children[1].textContent, /Sem orçamento/);
    assert.ok(!rows.map(row => row.children[1].textContent).join().includes("null%"));
});

test("vazio não mostra alarme; nomes com HTML são texto e estados saudáveis têm rótulo", async () => {
    const empty = await page(() => ({ itens: [] }));
    assert.equal(empty.panel.hidden, true);
    const healthy = await page(() => ({ itens: [item("<img onerror=alert(1)>", "OK", 20)] }));
    assert.equal(healthy.get("icon").hidden, true);
    assert.equal(healthy.get("count").textContent, "0 alertas");
    assert.equal(healthy.get("items").children[0].children[1].textContent, "<img onerror=alert(1)>: OK.");
});

test("erro não afirma ausência de alertas; nova tentativa e retorno à janela atualizam sem manter dados antigos", async () => {
    const p = await page(() => { throw { status: 503 }; });
    assert.equal(p.panel.hidden, false);
    assert.equal(p.get("retry").hidden, false);
    assert.match(p.get("message").textContent, /Não foi possível/);
    p.client.get = async () => ({ itens: [item("A", "EXCEDIDO", 110)] });
    await p.get("retry").events.click();
    assert.equal(p.get("items").children.length, 1);
    p.client.get = async () => { throw { status: 403 }; };
    await p.events.focus();
    assert.equal(p.panel.hidden, true);
    assert.equal(p.get("items").children.length, 0);
});

test("resposta atrasada de uma consulta anterior não substitui a atualização", async () => {
    const p = await page(() => ({ itens: [] }));
    const pending = [];
    p.client.get = () => new Promise(resolve => pending.push(resolve));
    const first = p.events.focus();
    const last = p.events.focus();
    pending[1]({ itens: [item("Atual", "EXCEDIDO", 101)] }); await last;
    pending[0]({ itens: [item("Antigo", "ALERTA", 80)] }); await first;
    assert.match(p.get("items").children[0].children[1].textContent, /Atual/);
    assert.equal(p.get("items").children.length, 1);
});
