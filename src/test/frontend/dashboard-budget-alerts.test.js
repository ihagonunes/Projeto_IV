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
    const element = tag => ({ tag, hidden: true, textContent: "", children: [], events: {}, attrs: {},
        setAttribute(key, value) { this.attrs[key] = value; },
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
    const row = p.get("items").children[0];
    assert.equal(row.tag, "tr");
    assert.equal(row.children[0].textContent, "Marketing");
    assert.equal(row.children[0].attrs.scope, "row");
    assert.equal(row.children[1].children[0].textContent, "Excedido");
    assert.equal(row.children[5].children[0].textContent, "125,5%");
    assert.equal(row.children[5].children[1].value, 100);
    assert.equal(p.get("total").textContent, "3");
    assert.equal(p.get("alerts").textContent, "2");
    assert.equal(p.get("items").children.length, 3);
});

test("gestor recebe apenas as áreas que vieram da API, sem inferir escopo pelo perfil da URL", async () => {
    const p = await page(() => ({ itens: [{
        ...item("Minha área", "ALERTA", 80), orcamento: 100, custoReal: 80
    }] }));
    assert.equal(p.get("items").children.length, 1);
    assert.equal(p.get("details").open, true);
    const cells = p.get("items").children[0].children;
    assert.equal(cells[0].textContent, "Minha área");
    assert.deepEqual(cells.slice(2, 5).map(c => c.textContent), ["100,00", "80,00", "20,00"]);
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
    assert.equal(rows[0].children[1].children[0].textContent, "Excedido");
    assert.equal(rows[1].children[1].children[0].textContent, "Sem orçamento");
    assert.equal(rows[0].children[5].textContent, "—");
    assert.equal(rows[1].children[5].textContent, "—");
    assert.equal(rows[1].children[2].textContent, "—");
});

test("vazio não mostra alarme; nomes com HTML são texto e estados saudáveis têm rótulo", async () => {
    const empty = await page(() => ({ itens: [] }));
    assert.equal(empty.panel.hidden, true);
    const healthy = await page(() => ({ itens: [item("<img onerror=alert(1)>", "OK", 20)] }));
    assert.equal(healthy.get("icon").hidden, true);
    assert.equal(healthy.get("count").textContent, "0 alertas");
    assert.equal(healthy.get("items").children[0].children[0].textContent, "<img onerror=alert(1)>");
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
    assert.equal(p.get("items").children[0].children[0].textContent, "Atual");
    assert.equal(p.get("items").children.length, 1);
});

test("departamentos sem orçamento têm resumo próprio e não aparecem como situação regular", async () => {
    const p = await page(() => ({ itens: [{ ...item("Dados", "SEM_ORCAMENTO"), orcamento: null, custoReal: 0 }] }));
    assert.equal(p.get("missing").textContent, "1");
    assert.equal(p.get("alerts").textContent, "0");
    assert.match(p.get("message").textContent, /sem orçamento informado/);
    assert.doesNotMatch(p.get("message").textContent, /Nenhum departamento em alerta/);
    assert.doesNotMatch(p.get("count").className, /success/);
    const cells = p.get("items").children[0].children;
    assert.equal(cells[2].textContent, "—");
    assert.equal(cells[3].textContent, "0,00");
    assert.equal(cells[4].textContent, "—");
});

test("valores e percentuais inválidos não produzem NaN ou porcentagem inventada", async () => {
    const p = await page(() => ({ itens: [{ ...item("TI", "OK", "inválido"), orcamento: "inválido", custoReal: 0 }] }));
    const cells = p.get("items").children[0].children;
    assert.equal(cells[2].textContent, "—");
    assert.equal(cells[4].textContent, "—");
    assert.equal(cells[5].textContent, "—");
});
