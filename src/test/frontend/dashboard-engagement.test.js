"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const E = require("../../main/resources/static/public/js/pages/dashboard-engagement-core");
const page = fs.readFileSync(path.join(__dirname,
    "../../main/resources/static/public/js/pages/dashboard-engagement.js"), "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));

test("E6 adoção: ausência de base vira sem dado e zero legítimo é preservado", () => {
    const empty = E.mapAdoption({
        sourceKind: "OPERACIONAL", ate: "2026-10-31", janelaDias: 30,
        elegiveis: 0, ativos: 0, inativos: 0, taxaAdocao: null, licencasOciosas: 0,
        dataStatus: "INSUFFICIENT_DATA", porDepartamento: [],
        cobertura: { eligibleTotal: 0, eligibleLinked: 0, unmappedLicenseStatus: 0, unmappedTrainingStatus: 0 }
    });
    assert.equal(empty.rateLabel, "Sem dado");
    assert.equal(empty.activeLabel, "0");
    assert.equal(empty.idleLicensesLabel, "0");
    assert.equal(empty.insufficient, true);
    assert.equal(empty.donut, null);
    assert.match(empty.note, /não representam zero/);
    assert.equal(empty.statusLabel, "Dados insuficientes");

    const normal = E.mapAdoption({
        sourceKind: "HISTORICO", ate: "2026-07-31", janelaDias: 30,
        elegiveis: 279, ativos: 201, inativos: 78, taxaAdocao: 72.04, licencasOciosas: 12,
        dataStatus: "COMPLETE",
        porDepartamento: [{ departmentId: "dep-1", elegiveis: 30, ativos: 25, taxaAdocao: 83.33 }],
        cobertura: { eligibleTotal: 300, eligibleLinked: 279, unmappedLicenseStatus: 2, unmappedTrainingStatus: 1 }
    });
    assert.equal(normal.sourceLabel, "Histórico");
    assert.equal(normal.rateLabel, "72,04%");
    assert.equal(normal.activeLabel, "201");
    assert.equal(normal.eligibleLabel, "279");
    assert.equal(normal.idleLicensesLabel, "12");
    assert.equal(normal.donut.rate, 72.04);
    assert.equal(normal.departments[0].rateLabel, "83,33%");
    assert.match(normal.coverageLabel, /279 de 300 elegíveis/);
    assert.match(normal.coverageLabel, /2 de licença e 1 de treinamento/);
});

test("E6 treinamento: contagens por quadrante e lista nominal só quando a API autoriza", () => {
    const report = {
        sourceKind: "OPERACIONAL", ate: "2026-10-31", listaNominal: false,
        quadrantes: {
            TREINADO_ATIVO: { total: 120, usuarios: [{ userId: "u1", nome: "Pessoa", departmentId: "dep-1" }], truncado: false },
            TREINADO_INATIVO: { total: 30, usuarios: [], truncado: false },
            NAO_TREINADO_ATIVO: { total: 45, usuarios: [], truncado: false },
            NAO_TREINADO_INATIVO: { total: 84, usuarios: [], truncado: true }
        },
        cobertura: { eligibleTotal: 300, eligibleLinked: 279, unmappedLicenseStatus: 0, unmappedTrainingStatus: 0 }
    };
    const closed = E.mapTraining(report);
    assert.equal(closed.nominal, false);
    assert.match(closed.nominalNote, /não está disponível/);
    assert.deepEqual(closed.quadrants.map(item => item.label),
        ["Treinado e ativo", "Treinado e inativo", "Não treinado e ativo", "Não treinado e inativo"]);
    assert.deepEqual(closed.quadrants.map(item => item.totalLabel), ["120", "30", "45", "84"]);
    assert.equal(closed.quadrants[3].truncated, true);

    const open = E.mapTraining(Object.assign({}, report, { listaNominal: true }));
    assert.equal(open.nominal, true);
    assert.match(open.nominalNote, /USER:READ/);
    assert.equal(open.quadrants[0].users[0].name, "Pessoa");
});

test("E6 credits: colaborador vê o próprio score e NOT_APPLICABLE não vira zero", () => {
    const data = E.mapCredits({
        sourceKind: "OPERACIONAL", mes: "2026-10", status: "PROVISORIO",
        calculationVersion: "credits-v1",
        itens: [{
            userId: "u1", nome: "Pessoa", departmentId: "dep-1", score: 78.5,
            usage: 100, training: 100, license: null,
            licenseApplicability: "NOT_APPLICABLE", dataStatus: "COMPLETE"
        }],
        cobertura: { eligibleTotal: 1, eligibleLinked: 1, unmappedLicenseStatus: 0, unmappedTrainingStatus: 0 }
    }, true);
    assert.equal(data.monthLabel, "10/2026");
    assert.equal(data.statusLabel, "Provisório (mês corrente)");
    assert.equal(data.item.scoreLabel, "78,50%");
    assert.equal(data.item.usageLabel, "100,00%");
    assert.equal(data.item.licenseLabel, "Não aplicável");
    assert.equal(data.item.dataStatusLabel, "Dados completos");
    assert.equal(data.insufficient, false);
    assert.equal(data.note, "");

    const missing = E.mapCredits({
        sourceKind: "OPERACIONAL", mes: "2026-09", status: "FECHADO", calculationVersion: "credits-v1",
        itens: [], cobertura: { eligibleTotal: 1, eligibleLinked: 0, unmappedLicenseStatus: 0, unmappedTrainingStatus: 0 }
    }, true);
    assert.equal(missing.item, null);
    assert.equal(missing.insufficient, true);
    assert.match(missing.note, /Sem dado/);
});

test("E6 credits: gestor vê distribuição por faixa sem ranking nominal", () => {
    const data = E.mapCredits({
        sourceKind: "OPERACIONAL", mes: "2026-10", status: "FECHADO", calculationVersion: "credits-v1",
        itens: [
            { userId: "u1", nome: "A", departmentId: "dep-1", score: 10, usage: 0, training: 0, license: null, licenseApplicability: "NOT_APPLICABLE", dataStatus: "COMPLETE" },
            { userId: "u2", nome: "B", departmentId: "dep-1", score: 40, usage: 50, training: 100, license: null, licenseApplicability: "NOT_APPLICABLE", dataStatus: "COMPLETE" },
            { userId: "u3", nome: "C", departmentId: "dep-1", score: 60, usage: 100, training: 100, license: 25, licenseApplicability: "APPLICABLE", dataStatus: "COMPLETE" },
            { userId: "u4", nome: "D", departmentId: "dep-1", score: 90, usage: 100, training: 100, license: 75, licenseApplicability: "APPLICABLE", dataStatus: "PARTIAL" }
        ],
        cobertura: { eligibleTotal: 4, eligibleLinked: 4, unmappedLicenseStatus: 0, unmappedTrainingStatus: 0 }
    }, false);
    assert.equal(data.personal, false);
    assert.equal(data.totalLabel, "4");
    assert.equal(data.averageLabel, "50,00%");
    assert.deepEqual(data.buckets.map(item => item.countLabel), ["1", "1", "1", "1"]);
    assert.deepEqual(data.buckets.map(item => item.shareLabel), ["25%", "25%", "25%", "25%"]);
    assert.equal(data.components.usage, "62,50%");
    assert.equal(data.components.license, "50,00%");
    assert.equal(data.partial, true);
    assert.equal(data.items, undefined);
    assert.equal(data.itens, undefined);
    assert.equal(data.buckets.some(item => "name" in item || "nome" in item), false);
});

test("E6 filtros: fonte, corte e departamento válido; seleção histórica não vira UUID", () => {
    assert.equal(E.engagementQuery({ fonte: "HISTORICO", ate: "2026-07-31", departmentId: "dep-1" }),
        "fonte=HISTORICO&ate=2026-07-31&departmentId=dep-1");
    assert.equal(E.engagementQuery({ fonte: "OPERACIONAL", ate: "2026-07-31", departmentId: "historical:legacy" }),
        "fonte=OPERACIONAL&ate=2026-07-31");
    assert.equal(E.creditsQuery({ fonte: "OPERACIONAL", mes: "2026-07", departmentId: "dep-1" }),
        "fonte=OPERACIONAL&mes=2026-07&departmentId=dep-1");
    assert.equal(E.monthFromDate("2026-07-31"), "2026-07");
    assert.equal(E.monthLabel("2026-07"), "07/2026");
    assert.equal(E.monthLabel(null), "Sem dado");
});

function createDocument() {
    const elements = new Map();
    const element = () => ({
        value: "", textContent: "", hidden: false, disabled: true, children: [], events: {}, dataset: {}, style: {},
        append(...nodes) { this.children.push(...nodes); },
        appendChild(child) { this.children.push(child); },
        replaceChildren() { this.children = []; },
        setAttribute(name, value) { this[name] = value; },
        removeAttribute(name) { delete this[name]; },
        addEventListener(event, handler) { this.events[event] = handler; },
        checkValidity() { return true; }
    });
    const get = id => {
        if (!elements.has(id)) elements.set(id, element());
        return elements.get(id);
    };
    get("engagement-panel").dataset.scope = "authorized";
    get("metrics-filters").dataset = { scope: "authorized" };
    get("metrics-to").value = "2026-07-31";
    get("engagement-source").value = "HISTORICO";
    get("dashboard-area").value = "dep-1";
    return { elements, get };
}

async function runPage(document, client) {
    vm.runInNewContext(page, {
        window: { ApiClient: client, Auth: { requireAuth: () => true }, AtlasEngagement: E },
        document, URLSearchParams, Date, Number, Math, console
    });
    await tick();
}

test("E6 página: consulta os três endpoints com fonte, corte e departamento", async () => {
    const { get } = createDocument();
    const calls = [];
    const client = { async get(url) {
        calls.push(url);
        if (url.includes("/adocao")) return { sourceKind: "HISTORICO", ate: "2026-07-31", janelaDias: 30,
            elegiveis: 1, ativos: 1, inativos: 0, taxaAdocao: 100, licencasOciosas: 0,
            dataStatus: "COMPLETE", porDepartamento: [], cobertura: { eligibleTotal: 1, eligibleLinked: 1, unmappedLicenseStatus: 0, unmappedTrainingStatus: 0 } };
        if (url.includes("/cruzamento")) return { sourceKind: "HISTORICO", ate: "2026-07-31", listaNominal: false,
            quadrantes: {}, cobertura: { eligibleTotal: 1, eligibleLinked: 1, unmappedLicenseStatus: 0, unmappedTrainingStatus: 0 } };
        return { sourceKind: "HISTORICO", mes: "2026-07", status: "FECHADO", calculationVersion: "credits-v1",
            itens: [], cobertura: { eligibleTotal: 1, eligibleLinked: 1, unmappedLicenseStatus: 0, unmappedTrainingStatus: 0 } };
    } };
    await runPage({ getElementById: get, createElement: () => createDocument().get("unused") }, client);
    assert.equal(calls.length, 3);
    assert.match(calls[0], /^\/v1\/engajamento\/adocao\?fonte=HISTORICO&ate=2026-07-31&departmentId=dep-1$/);
    assert.match(calls[1], /^\/v1\/engajamento\/treinamentos\/cruzamento\?fonte=HISTORICO&ate=2026-07-31&departmentId=dep-1$/);
    assert.match(calls[2], /^\/v1\/engajamento\/creditos\?fonte=HISTORICO&mes=2026-07&departmentId=dep-1$/);
    assert.equal(get("engagement-adoption-rate").textContent, "100,00%");
    assert.equal(get("engagement-content").hidden, false);
});

test("E6 página: colaborador consulta apenas os próprios credits; 403 é explicado", async () => {
    const { get } = createDocument();
    get("engagement-panel").dataset.scope = "self";
    get("metrics-filters").dataset = { scope: "self" };
    const calls = [];
    let failure = null;
    const client = { async get(url) {
        calls.push(url);
        if (failure) throw failure;
        return { sourceKind: "OPERACIONAL", mes: "2026-07", status: "PROVISORIO", calculationVersion: "credits-v1",
            itens: [{ userId: "u1", nome: "Pessoa", departmentId: "dep-1", score: 50, usage: 50, training: 50,
                license: null, licenseApplicability: "NOT_APPLICABLE", dataStatus: "PARTIAL" }],
            cobertura: { eligibleTotal: 1, eligibleLinked: 1, unmappedLicenseStatus: 0, unmappedTrainingStatus: 0 } };
    } };
    await runPage({ getElementById: get, createElement: () => createDocument().get("unused") }, client);
    assert.equal(calls.length, 1);
    assert.match(calls[0], /^\/v1\/engajamento\/creditos\?fonte=HISTORICO&mes=2026-07&departmentId=dep-1$/);
    assert.equal(get("engagement-credits-score").textContent, "50,00%");
    assert.equal(get("engagement-credits-license").textContent, "Não aplicável");
    assert.equal(get("engagement-credits-personal").hidden, false);

    failure = Object.assign(new Error("forbidden"), { status: 403 });
    await get("engagement-source").events.change();
    assert.match(get("engagement-message").textContent, /Sem permissão/);
    assert.equal(get("engagement-content").hidden, true);
});
