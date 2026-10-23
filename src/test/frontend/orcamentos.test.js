const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const source = fs.readFileSync(path.join(__dirname, "../../main/resources/static/public/js/pages/orcamentos.js"), "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));
async function page(edit = true, items, put = async () => ({})) {
    const elements = new Map(), calls = [];
    const element = () => ({ value: "", textContent: "", className: "", dataset: {}, children: [], events: {},
        set innerHTML(value) { this.html = value; this.children = []; }, get innerHTML() { return this.html || ""; },
        appendChild(child) { this.children.push(child); }, addEventListener(event, handler) { this.events[event] = handler; },
        showModal() { this.open = true; }, close() { this.open = false; } });
    const get = id => { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); };
    const row = (pct, status, budget = 100) => ({ departmentId: `dept-${pct}`, departmentName: "<Área>",
        orcamento: budget, custoReal: pct, pctUsado: pct, status, limiarAlerta: 80 });
    let ready;
    const client = { async get(url) {
        calls.push(url);
        if (url.includes("permissoes")) return { permissoes: edit ? ["DEPARTMENT:MANAGE"] : [] };
        if (url.includes("observability")) return { itens: items || [row(79.99, "OK"), row(80, "ALERTA"), row(99.99, "ALERTA"), row(120, "EXCEDIDO"), row(null, "SEM_ORCAMENTO", null)] };
        return [];
    }, async put(url, body) { calls.push({ url, body }); return put(url, body); } };
    vm.runInNewContext(source, { window: { ApiClient: client }, document: { getElementById: get,
        createElement: element, addEventListener(event, handler) { ready = handler; } }, Date });
    await ready();
    async function click(index, action) {
        const tr = get("orcamentosTableBody").children[index];
        const button = { dataset: { acao: action }, closest: () => tr };
        await get("orcamentosTableBody").events.click({ target: { closest: () => button } });
    }
    return { get, calls, client, click, submit: () => get("orcamentoEditorForm").events.submit({ preventDefault() {} }) };
}

test("matriz mostra barras acessíveis nos limites de 80% e 100%, sem orçamento e nomes escapados", async () => {
    const p = await page();
    const rows = p.get("orcamentosTableBody").children;
    assert.match(rows[0].innerHTML, /progress-bar bg-success/);
    assert.match(rows[1].innerHTML, /progress-bar bg-warning/);
    assert.match(rows[2].innerHTML, /progress-bar bg-warning/);
    assert.match(rows[3].innerHTML, /progress-bar bg-danger/);
    assert.match(rows[3].innerHTML, /aria-valuenow="100"/);
    assert.match(rows[3].innerHTML, /120,00%/);
    assert.match(rows[4].innerHTML, /Sem orçamento/);
    assert.match(rows[0].innerHTML, /&lt;Área&gt;/);
    assert.match(rows[0].innerHTML, /role="progressbar"/);
});

test("modal envia orçamento ao departamento e mês corretos, atualiza a matriz e evita duplo envio", async () => {
    let finish;
    const p = await page(true, undefined, () => new Promise(resolve => { finish = resolve; }));
    const month = p.get("orcamentoMes").value;
    await p.click(0, "editar");
    assert.equal(p.get("orcamentoEditor").open, true);
    assert.match(p.get("orcamentoEditorDepartamento").textContent, /<Área>/);
    p.get("orcamentoEditorValor").value = "1250.50";
    p.get("orcamentoEditorLimiar").value = "80";
    const saving = p.submit();
    await p.submit();
    const writes = p.calls.filter(call => typeof call === "object");
    assert.equal(writes.length, 1);
    assert.equal(writes[0].url, `/v1/departamentos/dept-79.99/orcamentos/${month}`);
    assert.equal(writes[0].body.amount, "1250.50");
    assert.equal(p.get("orcamentoEditorSalvar").disabled, true);
    finish({}); await saving;
    assert.equal(p.get("orcamentoEditor").open, false);
    assert.equal(p.get("orcamentoEditorSalvar").disabled, false);
});

test("validação rejeita vazio, negativo, precisão excessiva, notação exponencial e limiar inválido", async () => {
    const p = await page(); await p.click(0, "editar");
    for (const value of ["", "-1", "1.001", "1e3", "10000000000000.00"]) {
        p.get("orcamentoEditorValor").value = value;
        await p.submit();
    }
    p.get("orcamentoEditorValor").value = "10.00";
    for (const value of ["0", "101", "1.5"]) {
        p.get("orcamentoEditorLimiar").value = value; await p.submit();
    }
    assert.equal(p.calls.filter(call => typeof call === "object").length, 0);
    p.get("orcamentoEditorLimiar").value = "80";
    p.get("orcamentoEditorValor").value = "0";
    await p.submit();
    assert.equal(p.calls.filter(call => typeof call === "object").length, 1);
});

test("consulta sem permissão não oferece edição; erro ao salvar mantém modal para corrigir", async () => {
    const read = await page(false);
    assert.ok(read.get("orcamentosTableBody").children.every(row => !row.innerHTML.includes('data-acao="editar"')));
    await read.click(0, "editar");
    assert.ok(!read.get("orcamentoEditor").open);
    const p = await page(true, undefined, async () => { throw { status: 403 }; });
    await p.click(0, "editar"); await p.submit();
    assert.equal(p.get("orcamentoEditor").open, true);
    assert.match(p.get("orcamentoEditorMensagem").textContent, /permissão/);
    assert.equal(p.get("orcamentoEditorSalvar").disabled, false);
});

test("histórico consulta 12 meses e troca de mês limpa histórico e ignora respostas antigas", async () => {
    const p = await page();
    p.get("orcamentoMes").value = "2026-01";
    await p.click(0, "historico");
    assert.ok(p.calls.includes("/v1/departamentos/dept-79.99/orcamentos?de=2025-02&ate=2026-01"));
    const pending = [];
    p.client.get = () => new Promise(resolve => pending.push(resolve));
    p.get("orcamentoMes").value = "2026-02"; p.get("orcamentoMes").events.change();
    p.get("orcamentoMes").value = "2026-03"; p.get("orcamentoMes").events.change();
    pending[1]({ itens: [] }); await tick();
    pending[0]({ itens: [{ departmentId: "old", departmentName: "Antigo" }] }); await tick();
    assert.equal(p.get("orcamentosTableBody").children.length, 0);
    assert.match(p.get("orcamentosTableBody").innerHTML, /Nenhum departamento/);
    assert.equal(p.get("historicoTableBody").innerHTML, "");
});
