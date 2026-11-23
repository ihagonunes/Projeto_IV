"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const P = require("../../main/resources/static/public/js/pages/agent-plaza");

const plazaPath = path.resolve(__dirname, "../../main/resources/static/public/js/pages/agent-plaza.js");
const chatPath = path.resolve(__dirname, "../../main/resources/static/public/js/pages/chat.js");

function fakeDom(t) {
    class Element {
        constructor(tag) {
            this.tag = tag; this.children = []; this.attrs = {}; this.events = {};
            this.style = { setProperty() {} }; this.value = tag === "select" ? "Todos" : "";
            this.clientWidth = 800; this.clientHeight = 400; this.textContent = "";
        }
        append(...nodes) { this.children.push(...nodes); }
        replaceChildren(...nodes) { this.children = nodes; }
        insertBefore(node, reference) { this.children.splice(this.children.indexOf(reference), 0, node); }
        setAttribute(key, value) { this.attrs[key] = value; }
        addEventListener(name, handler) { const before = this.events[name]; this.events[name] = event => { before?.(event); handler(event); }; }
        cloneNode() { return new Element(this.tag); }
        getBoundingClientRect() { return { left: 200, top: 200, width: 48, height: 48 }; }
        focus() { globalThis.document.activeElement = this; }
        click() { if (!this.disabled) this.events.click?.({ target: this, detail: 1 }); }
        dataset = {};
    }
    const previousDocument = globalThis.document;
    const previousObserver = globalThis.ResizeObserver;
    const previousWindow = globalThis.window;
    let timerId = 0;
    globalThis.window = {
        innerWidth: 800,
        setTimeout: () => ++timerId,
        clearTimeout: () => {}
    };
    globalThis.document = {
        activeElement: null,
        createElement: tag => new Element(tag),
        createElementNS: (_ns, tag) => new Element(tag)
    };
    globalThis.ResizeObserver = class { observe() {} };
    t.after(() => {
        globalThis.document = previousDocument;
        globalThis.ResizeObserver = previousObserver;
        globalThis.window = previousWindow;
    });
    const all = node => [node, ...node.children.flatMap(child => all(child))];
    return { all };
}

function catalogEntry(agentKey, status, executable) {
    return { agentKey, name: agentKey === "policies" ? "Políticas internas" : "Código",
        description: "Descrição", category: "Gerais", status, executable };
}

const tick = () => new Promise(resolve => setImmediate(resolve));

async function mountPublished(t, executionView, failures) {
    const calls = { execute: [] };
    let confirmed = 0;
    const client = {
        async get() {
            return { agentes: [catalogEntry("policies", "ACTIVE", true)] };
        },
        async post(path, body, options) {
            if (path.endsWith("/validar")) return { valid: true, needsMerge: false, errors: [] };
            if (path.endsWith("/publicar")) return { rowVersion: 2, status: "PUBLISHED" };
            if (path.endsWith("/execucoes")) {
                calls.execute.push({ body, options });
                return executionView;
            }
            return { id: "flow-1", rowVersion: 1, status: "DRAFT" };
        },
        async put() { throw new Error("put inesperado"); }
    };
    const dom = fakeDom(t);
    const root = P.mount({ client, graph: validGraphFixture(),
        onConfirm: () => { confirmed += 1; } });
    await tick(); await tick();
    dom.all(root).find(node => node.textContent === "Confirmar esteira").click();
    await tick(); await tick(); await tick();
    return { dom, root, calls, confirmed: () => confirmed };
}

test("praça: grafo do servidor usa agentKey e ids opacos; a UI converte nos dois sentidos", () => {
    const graph = P.createGraph();
    const agent = P.addNode(graph, "policies");
    assert.notEqual(agent.id, "step-1");
    assert.match(agent.id, /[0-9a-f-]{20,}/i);
    const result = P.addNode(graph, "result");
    P.connect(graph, "task", agent.id); P.connect(graph, agent.id, result.id);

    const server = P.toServerGraph(graph);
    assert.equal(server.schemaVersion, 1);
    assert.deepEqual(server.nodes.find(node => node.kind === "agent"),
        { id: agent.id, kind: "agent", agentKey: "policies", x: agent.x, y: agent.y });
    assert.ok(server.nodes.some(node => node.kind === "task"));
    assert.ok(server.nodes.some(node => node.kind === "result"));

    const back = P.fromServerGraph(server);
    assert.equal(back.nodes.find(node => node.id === agent.id).kind, "policies");
    assert.equal(back.edges.length, 2);
});

test("praça: serviço usa a API real, If-Match e detecta conflito 409/412", async () => {
    const calls = [];
    const client = {
        async get(path) {
            calls.push(["GET", path]);
            if (path === "/agentes/catalogo") {
                return { agentes: [catalogEntry("policies", "ACTIVE", true),
                    catalogEntry("code", "DRAFT", false)] };
            }
            return { id: "flow-1", name: "Fluxo", rowVersion: 4, status: "DRAFT",
                currentVersion: { graph: P.toServerGraph(P.createGraph()) } };
        },
        async post(path, body, options) {
            calls.push(["POST", path, body, options]);
            if (path.endsWith("/publicar")) return { rowVersion: 6, status: "PUBLISHED" };
            if (path.endsWith("/validar")) return { valid: true, needsMerge: false, errors: [] };
            if (path.endsWith("/execucoes")) {
                return { id: "exec-1", status: "SUCCEEDED", result: "ok", steps: [],
                    cost: { status: "KNOWN", estimatedCost: 0.01 } };
            }
            return { id: "flow-1", rowVersion: 1 };
        },
        async put(path, body, options) {
            calls.push(["PUT", path, body, options]);
            return { rowVersion: 5 };
        }
    };
    const service = P.createService(client);
    const catalog = await service.catalog();
    assert.equal(catalog.length, 2);
    assert.equal(catalog[1].executable, false);

    const loaded = await service.load("flow-1");
    assert.equal(loaded.rowVersion, 4);
    assert.equal(loaded.graph.nodes[0].kind, "task");

    await service.create("Novo", P.createGraph());
    assert.deepEqual(calls.at(-1).slice(0, 2), ["POST", "/agentes/fluxos"]);

    await service.save("flow-1", 4, "Fluxo", P.createGraph());
    assert.equal(calls.at(-1)[3].headers["If-Match"], "\"4\"");

    await service.validate("flow-1", P.createGraph());
    assert.deepEqual(calls.at(-1).slice(0, 2), ["POST", "/agentes/fluxos/flow-1/validar"]);

    await service.publish("flow-1", 5);
    assert.equal(calls.at(-1)[3].headers["If-Match"], "\"5\"");

    const execution = await service.execute("flow-1", "idem-1234", "Tarefa");
    assert.deepEqual(calls.at(-1).slice(0, 2), ["POST", "/agentes/fluxos/flow-1/execucoes"]);
    assert.equal(calls.at(-1)[2].input, "Tarefa");
    assert.equal(calls.at(-1)[3].headers["Idempotency-Key"], "idem-1234");
    assert.equal(execution.status, "SUCCEEDED");

    assert.equal(service.isConflict({ status: 412 }), true);
    assert.equal(service.isConflict({ status: 409 }), true);
    assert.equal(service.isConflict({ status: 400 }), false);
});

test("praça: catálogo vem da API e DRAFT não pode ser adicionado", async t => {
    const dom = fakeDom(t);
    const client = {
        async get(path) {
            if (path === "/agentes/catalogo") {
                return { agentes: [catalogEntry("policies", "ACTIVE", true),
                    catalogEntry("code", "DRAFT", false)] };
            }
            throw new Error("flow inesperado");
        },
        async post() { throw new Error("post inesperado"); },
        async put() { throw new Error("put inesperado"); }
    };
    const root = P.mount({ client, onConfirm: () => {} });
    await new Promise(resolve => setImmediate(resolve));
    await new Promise(resolve => setImmediate(resolve));

    const nodes = dom.all(root);
    const policies = nodes.find(node => node.attrs["aria-label"] === "Adicionar Políticas internas (ACTIVE)");
    const code = nodes.find(node => node.attrs["aria-label"] === "Código (DRAFT, não executável)");
    const merge = nodes.filter(node => node.attrs["aria-label"] === "Adicionar Consolidador");
    assert.ok(policies);
    assert.ok(code);
    assert.equal(code.disabled, true);
    assert.equal(merge.length, 1);
    assert.ok(nodes.some(node => node.textContent === "Salvar rascunho"));
    assert.ok(nodes.some(node => node.attrs["aria-label"] === "Nome do fluxo"));
});

test("praça: foco do teclado é preservado após novo render", t => {
    const dom = fakeDom(t);
    const root = P.mount({ onConfirm: () => {} });
    const task = dom.all(root).find(node =>
        node.attrs["aria-label"] === "Tarefa do usuário. Use as setas para mover.");
    task.focus();
    const taskId = task.dataset.nodeId;
    assert.ok(taskId);

    dom.all(root).find(node => node.attrs["aria-label"] === "Adicionar Código").click();

    assert.equal(globalThis.document.activeElement.dataset.nodeId, taskId);
});

test("praça: após publicar, executa na tela e mostra resultado/etapas/custo", async t => {
    const view = {
        id: "exec-1", status: "SUCCEEDED", result: "[stub] resultado da esteira",
        errorCode: null,
        steps: [
            { nodeId: "t", nodeKind: "task", status: "SUCCEEDED", requestId: null, receipt: null },
            { nodeId: "a", nodeKind: "agent", status: "SUCCEEDED", requestId: "req-1",
                receipt: { costStatus: "KNOWN", estimatedCost: 0.03 } }
        ],
        cost: { status: "KNOWN", estimatedCost: 0.03 }
    };
    const { dom, root, calls, confirmed } = await mountPublished(t, view, 1);

    assert.equal(root.dataset.flowStatus, "PUBLISHED");
    assert.equal(root.dataset.flowId, "flow-1");
    assert.equal(confirmed(), 0, "publicar não fecha o diálogo nem confirma a execução");
    assert.ok(dom.all(root).some(node => node.textContent === "Executar esteira"));

    const input = dom.all(root).find(node => node.attrs["aria-label"] === "Entrada da execução");
    input.value = "Tarefa E2E";
    dom.all(root).find(node => node.textContent === "Executar esteira").click();
    await tick(); await tick(); await tick();

    const status = dom.all(root).find(node => node.className === "plaza-execution-status");
    assert.match(status.textContent, /SUCCEEDED/);
    const result = dom.all(root).find(node => node.className === "plaza-execution-result");
    assert.equal(result.textContent, "[stub] resultado da esteira");
    assert.equal(result.hidden, false);
    const steps = dom.all(root).find(node => node.className === "plaza-execution-steps");
    assert.equal(steps.children.length, 2);
    const cost = dom.all(root).find(node => node.className === "plaza-execution-cost");
    assert.match(cost.textContent, /KNOWN/);
    assert.equal(calls.execute.length, 1);
    assert.equal(calls.execute[0].body.input, "Tarefa E2E");
    assert.ok(calls.execute[0].options.headers["Idempotency-Key"].length >= 8);
});

test("praça: falha de execução mostra status e libera nova tentativa com outra chave", async t => {
    const failed = {
        id: "exec-2", status: "FAILED", errorCode: "AGENT_COMPLETION_FAILED", result: null,
        steps: [{ nodeId: "a", nodeKind: "agent", status: "FAILED", requestId: null,
            receipt: null }],
        cost: { status: "INCONCLUSIVE", estimatedCost: null }
    };
    const { dom, root, calls } = await mountPublished(t, failed, 2);

    const input = dom.all(root).find(node => node.attrs["aria-label"] === "Entrada da execução");
    input.value = "Tarefa";
    dom.all(root).find(node => node.textContent === "Executar esteira").click();
    await tick(); await tick(); await tick();

    const status = dom.all(root).find(node => node.className === "plaza-execution-status");
    assert.match(status.textContent, /FAILED/);
    const retry = dom.all(root).find(node => node.textContent === "Nova tentativa");
    assert.equal(retry.hidden, false);

    retry.click();
    await tick(); await tick(); await tick();
    assert.equal(calls.execute.length, 2);
    assert.notEqual(calls.execute[0].options.headers["Idempotency-Key"],
        calls.execute[1].options.headers["Idempotency-Key"]);
});

test("praça: prévia não monta a seção de execução", t => {
    const dom = fakeDom(t);
    const root = P.mount({ onConfirm: () => {} });

    assert.equal(dom.all(root).some(node => node.textContent === "Executar esteira"), false);
    assert.equal(root.dataset.flowStatus, "");
    assert.equal(root.dataset.flowId, "");
});

test("praça: conflito 412 na publicação mostra o aviso e não publica", async t => {
    const client = {
        async get() { return { agentes: [catalogEntry("policies", "ACTIVE", true)] }; },
        async post(path) {
            if (path.endsWith("/validar")) return { valid: true, needsMerge: false, errors: [] };
            if (path.endsWith("/publicar")) {
                const error = new Error("conflito");
                error.status = 412;
                error.data = { code: "AGENT_FLOW_PRECONDITION_FAILED" };
                throw error;
            }
            return { id: "flow-1", rowVersion: 1, status: "DRAFT" };
        },
        async put() { throw new Error("put inesperado"); }
    };
    const dom = fakeDom(t);
    const root = P.mount({ client, graph: validGraphFixture(), onConfirm: () => {} });
    await tick(); await tick();

    dom.all(root).find(node => node.textContent === "Confirmar esteira").click();
    await tick(); await tick(); await tick();

    assert.notEqual(root.dataset.flowStatus, "PUBLISHED", "não deve ficar PUBLISHED");
    const message = dom.all(root).find(node =>
        node.textContent?.includes("Recarregue ou sobrescreva"));
    assert.ok(message);
    const container = root.children.find(child => child.children.includes(message));
    assert.equal(container.hidden, false);
    const section = dom.all(root).find(node => node.className === "plaza-execution");
    assert.equal(section.hidden, true, "execução não aparece sem publicar");
});

test("praça: clique duplo em executar não duplica a chamada", async t => {
    const view = {
        id: "exec-dup", status: "SUCCEEDED", result: "ok", errorCode: null,
        steps: [], cost: { status: "KNOWN", estimatedCost: 0.01 }
    };
    const { dom, root, calls } = await mountPublished(t, view, 3);
    const input = dom.all(root).find(node => node.attrs["aria-label"] === "Entrada da execução");
    input.value = "Tarefa";
    const execute = dom.all(root).find(node => node.textContent === "Executar esteira");
    execute.click();
    execute.click();
    await tick(); await tick(); await tick();

    assert.equal(calls.execute.length, 1);
});

test("praça: fluxo de outro tenant (404) não carrega dados nem executa", async t => {
    const client = {
        async get(path) {
            if (path === "/agentes/catalogo") {
                return { agentes: [catalogEntry("policies", "ACTIVE", true)] };
            }
            const error = new Error("não encontrado");
            error.status = 404;
            error.data = { code: "AGENT_FLOW_NOT_FOUND" };
            throw error;
        },
        async post() { throw new Error("post inesperado"); },
        async put() { throw new Error("put inesperado"); }
    };
    const dom = fakeDom(t);
    const root = P.mount({ client, flowId: "flow-de-outro-tenant" });
    await tick(); await tick(); await tick();

    const live = dom.all(root).find(node => node.className === "plaza-status");
    assert.match(live.textContent, /AGENT_FLOW_NOT_FOUND/);
    assert.equal(root.dataset.flowStatus, "");
    const section = dom.all(root).find(node => node.className === "plaza-execution");
    assert.equal(section.hidden, true, "seção de execução oculta sem fluxo carregado");
    const articles = dom.all(root).filter(node => node.tag === "article");
    assert.equal(articles.length, 1, "apenas o nó task inicial");
});

test("praça: arrastar do catálogo e soltar no canvas adiciona o agente", t => {
    const dom = fakeDom(t);
    const root = P.mount({ onConfirm: () => {} });
    const card = dom.all(root).find(node => node.attrs["aria-label"] === "Adicionar Políticas internas");
    const canvas = dom.all(root).find(node => node.className === "plaza-canvas");
    const dataTransfer = {
        data: {}, types: ["application/x-atlas-agent"], effectAllowed: "",
        setData(type, value) { this.data[type] = value; },
        getData(type) { return this.data[type] || ""; }
    };
    card.events.dragstart({ dataTransfer });
    canvas.events.drop({ dataTransfer, preventDefault() {}, clientX: 300, clientY: 260 });

    const articles = dom.all(root).filter(node => node.tag === "article");
    assert.equal(articles.length, 2, "tarefa + agente adicionado");
    const summary = dom.all(root).find(node => node.className === "plaza-summary");
    assert.match(summary.textContent, /1 agentes · 0 conexões/);
});

test("praça/chat: sem innerHTML, sem fetch direto na praça e sem agentFlowId no chat", () => {
    const plaza = fs.readFileSync(plazaPath, "utf8");
    const chat = fs.readFileSync(chatPath, "utf8");

    assert.ok(!plaza.includes("innerHTML"));
    assert.ok(!plaza.includes("fetch("));
    assert.ok(!chat.includes("agentFlowId"));
    assert.ok(!chat.includes("innerHTML"));
    assert.match(chat, /if \(preview\) options\.catalog = window\.AtlasAgentPlaza\.catalog/);
    assert.match(chat, /else options\.client = window\.API/);
});

function validGraphFixture() {
    const graph = P.createGraph();
    const agent = P.addNode(graph, "policies");
    const result = P.addNode(graph, "result");
    P.connect(graph, "task", agent.id);
    P.connect(graph, agent.id, result.id);
    return graph;
}

test("praça: erros do servidor aparecem por nó e impedem a publicação", async t => {
    const dom = fakeDom(t);
    let published = 0;
    let confirmed = 0;
    const client = {
        async get(path) {
            return { agentes: [catalogEntry("policies", "ACTIVE", true)] };
        },
        async post(path) {
            if (path.endsWith("/validar")) {
                return { valid: false, needsMerge: false, errors: [
                    { code: "AGENT_KEY_UNKNOWN", message: "agentKey inválido.", path: "/nodes/1" }] };
            }
            if (path.endsWith("/publicar")) { published += 1; return { rowVersion: 2, status: "PUBLISHED" }; }
            return { id: "flow-1", rowVersion: 1 };
        },
        async put() { throw new Error("put inesperado"); }
    };
    const root = P.mount({ client, graph: validGraphFixture(), onConfirm: () => { confirmed += 1; } });
    await new Promise(resolve => setImmediate(resolve));
    await new Promise(resolve => setImmediate(resolve));

    const nodes = dom.all(root);
    nodes.find(node => node.textContent === "Confirmar esteira").click();
    await new Promise(resolve => setImmediate(resolve));
    await new Promise(resolve => setImmediate(resolve));
    await new Promise(resolve => setImmediate(resolve));

    const after = dom.all(root);
    const live = after.find(node => node.className === "plaza-status");
    assert.match(live.textContent, /O servidor recusou o fluxo: agentKey inválido/);
    const agentCard = after.find(node => node.attrs["aria-label"] === "Políticas internas. Use as setas para mover.");
    assert.equal(agentCard.attrs["aria-invalid"], "true");
    assert.equal(published, 0);
    assert.equal(confirmed, 0);
});

test("praça: conflito 412 mostra recarregar/sobrescrever sem confirmar sucesso", async t => {
    const dom = fakeDom(t);
    let confirmed = 0;
    const client = {
        async get() { return { agentes: [catalogEntry("policies", "ACTIVE", true)] }; },
        async post(path) {
            if (path.endsWith("/validar")) return { valid: true, needsMerge: false, errors: [] };
            if (path.endsWith("/publicar")) {
                const error = new Error("conflito");
                error.status = 412;
                throw error;
            }
            return { id: "flow-1", rowVersion: 1 };
        },
        async put() { throw new Error("put inesperado"); }
    };
    const root = P.mount({ client, graph: validGraphFixture(), onConfirm: () => { confirmed += 1; } });
    await new Promise(resolve => setImmediate(resolve));
    await new Promise(resolve => setImmediate(resolve));

    const nodes = dom.all(root);
    nodes.find(node => node.textContent === "Confirmar esteira").click();
    await new Promise(resolve => setImmediate(resolve));
    await new Promise(resolve => setImmediate(resolve));
    await new Promise(resolve => setImmediate(resolve));

    const after = dom.all(root);
    const message = after.find(node => node.textContent?.includes("Recarregue ou sobrescreva"));
    assert.ok(message);
    const container = root.children.find(child => child.children.includes(message));
    assert.equal(container.hidden, false);
    assert.ok(after.some(node => node.textContent === "Recarregar"));
    assert.ok(after.some(node => node.textContent === "Sobrescrever"));
    assert.equal(confirmed, 0);
});
