"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const source = fs.readFileSync(path.join(__dirname,
    "../../main/resources/static/public/js/pages/dashboard-budget-projection.js"), "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));

const status = {
    itens: [
        { departmentId: "dep-1", departmentName: "Marketing", status: "ALERTA" },
        { departmentId: "dep-2", departmentName: "Vendas", status: "OK" }
    ]
};
const projection = {
    gastoAteHoje: 640, projetado: 754.29, diaEsgotamento: null,
    recomendacao: "DENTRO_DO_ORCAMENTO", pctProjetado: 75.43, mediaDiaria: 28.571429,
    janelaDias: 14, diasRestantes: 4, orcamento: 1000, orcamentoPadrao: false
};

async function page(reply, authenticated = true) {
    const elements = new Map(), calls = [];
    const element = () => ({
        hidden: true, textContent: "", value: "", disabled: true, children: [], events: {},
        appendChild(child) { this.children.push(child); },
        replaceChildren() { this.children = []; },
        addEventListener(event, handler) { this.events[event] = handler; }
    });
    const get = id => {
        if (!elements.has(id)) elements.set(id, element());
        return elements.get(id);
    };
    const client = { async get(url) { calls.push(url); return reply(url); } };
    vm.runInNewContext(source, {
        document: { getElementById: get, createElement: element }, Date,
        window: { Auth: { isAuthenticated: () => authenticated }, ApiClient: client }
    });
    await tick();
    return { get, calls, panel: get("budget-projection-panel"),
        department: get("budget-projection-department") };
}

test("projeção: usa o mês corrente, prioriza área em alerta e mostra valores e recomendação em texto", async () => {
    const p = await page(url => url.includes("/projecao") ? projection : status);
    assert.match(p.calls[0], /^\/v1\/observability\/orcamentos\?mes=\d{4}-\d{2}$/);
    assert.match(p.calls[1], /\/orcamentos\/projecao\?departmentId=dep-1&mes=\d{4}-\d{2}$/);
    assert.equal(p.panel.hidden, false);
    assert.equal(p.department.value, "dep-1");
    assert.equal(p.department.disabled, false);
    assert.deepEqual(p.department.children.map(item => [item.value, item.textContent]),
        [["dep-1", "Marketing"], ["dep-2", "Vendas"]]);
    assert.match(p.get("budget-projection-spent").textContent, /640,00/);
    assert.match(p.get("budget-projection-projected").textContent, /754,29/);
    assert.equal(p.get("budget-projection-exhaustion").textContent, "Não esgota neste mês");
    assert.equal(p.get("budget-projection-recommendation").textContent, "Dentro do orçamento");
    assert.match(p.get("budget-projection-status").textContent, /75,43% do orçamento projetado/);
    assert.match(p.get("budget-projection-detail").textContent, /janela de 14 dias/);
    assert.match(p.get("budget-projection-detail").textContent, /4 dias restantes/);
    assert.equal(p.get("budget-projection-content").hidden, false);
});

test("projeção: trocar o departamento refaz a consulta e exibe o dia de esgotamento", async () => {
    const p = await page(url => {
        if (!url.includes("/projecao")) return status;
        return url.includes("departmentId=dep-2")
            ? { gastoAteHoje: 900, projetado: 1200, diaEsgotamento: "2026-10-29",
                recomendacao: "ESGOTA_NO_MES", pctProjetado: 120, mediaDiaria: 100,
                janelaDias: 14, diasRestantes: 7, orcamento: 1000, orcamentoPadrao: false }
            : projection;
    });
    p.department.value = "dep-2";
    await p.department.events.change();
    assert.match(p.calls[p.calls.length - 1], /departmentId=dep-2/);
    assert.equal(p.get("budget-projection-exhaustion").textContent, "Dia 29/10/2026");
    assert.equal(p.get("budget-projection-recommendation").textContent, "Esgota no mês");
    assert.match(p.get("budget-projection-status").textContent, /120% do orçamento projetado/);
});

test("projeção: 403 oculta o painel sem expor nomes ou valores", async () => {
    const p = await page(() => { throw Object.assign(new Error("forbidden"), { status: 403 }); });
    assert.equal(p.panel.hidden, true);
    assert.equal(p.calls.length, 1);
    assert.equal(p.get("budget-projection-content").hidden, true);
});

test("projeção: 404 explica sem esconder o cartão e oferece nova tentativa", async () => {
    const p = await page(url => {
        if (!url.includes("/projecao")) return status;
        throw Object.assign(new Error("not found"), { status: 404 });
    });
    assert.equal(p.panel.hidden, false);
    assert.equal(p.get("budget-projection-message").textContent, "Departamento não encontrado.");
    assert.equal(p.get("budget-projection-retry").hidden, false);
    assert.equal(p.get("budget-projection-content").hidden, true);
});

test("projeção: sem orçamento não inventa percentual nem dia de esgotamento", async () => {
    const p = await page(url => url.includes("/projecao")
        ? { gastoAteHoje: 12.34, projetado: 12.34, diaEsgotamento: null,
            recomendacao: "SEM_ORCAMENTO", pctProjetado: null, mediaDiaria: 0,
            janelaDias: 14, diasRestantes: 0, orcamento: null, orcamentoPadrao: false }
        : status);
    assert.equal(p.get("budget-projection-recommendation").textContent, "Sem orçamento");
    assert.equal(p.get("budget-projection-status").textContent, "Sem percentual aplicável");
    assert.match(p.get("budget-projection-detail").textContent, /sem orçamento definido/);
    assert.ok(!p.get("budget-projection-status").textContent.includes("null%"));
});

test("projeção: sem departamentos autorizados mantém o painel oculto", async () => {
    const p = await page(() => ({ itens: [] }));
    assert.equal(p.panel.hidden, true);
    assert.equal(p.calls.length, 1);
    assert.equal(p.get("budget-projection-content").hidden, true);
});

test("projeção: sessão não autenticada não consulta a API", async () => {
    const p = await page(() => { throw new Error("não deve consultar"); }, false);
    assert.equal(p.calls.length, 0);
    assert.equal(p.panel.hidden, true);
});
