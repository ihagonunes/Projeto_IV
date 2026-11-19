"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const P = require("../../main/resources/static/public/js/pages/agent-plaza");

test("praça: catálogo aponta para os 18 PNGs corretos e os quatro frames do fundo estão completos", () => {
    const fs = require("node:fs"), path = require("node:path");
    const publicRoot = path.resolve(__dirname, "../../main/resources/static/public");
    assert.equal(P.catalog.length, 18);
    assert.equal(new Set(P.catalog.map(a => a.image)).size, 18);
    for (const agent of P.catalog) {
        const png = fs.readFileSync(path.join(publicRoot, agent.image.replace('/public/', '')));
        assert.equal(png.subarray(1, 4).toString(), "PNG");
        assert.equal(png.readUInt32BE(16), 48, agent.name);
        assert.equal(png.readUInt32BE(20), 48, agent.name);
    }
    for (const file of ["19_tarefa-do-usuario.png", "20_resultado.png"]) {
        const png = fs.readFileSync(path.join(publicRoot, "img/agents", file));
        assert.equal(png.readUInt32BE(16), 48); assert.equal(png.readUInt32BE(20), 48);
    }
    const frames = [1, 2, 3, 4].map(i => fs.readFileSync(path.join(publicRoot, `img/agent-plaza/fundo_0${i}.png`)));
    for (const frame of frames) {
        assert.equal(frame.readUInt32BE(16), 1536); assert.equal(frame.readUInt32BE(20), 640);
    }
    assert.equal(new Set(frames.map(frame => require("node:crypto").createHash("sha256").update(frame).digest("hex"))).size, 4);
});
function parallel(withResult = true) {
    const graph = P.createGraph();
    const a = P.addNode(graph, "policies"), b = P.addNode(graph, "data");
    P.connect(graph, "task", a.id); P.connect(graph, "task", b.id);
    const result = withResult ? P.addNode(graph, "result") : null;
    return { graph, a, b, result };
}

test("praça: confirmar alerta sem resultado e só fecha após validar todas as conexões", t => {
    // DOM mínimo para exercitar os cliques reais do editor, sem dependência nova.
    class Element {
        constructor(tag) {
            this.tag = tag; this.children = []; this.attrs = {}; this.events = {};
            this.style = { setProperty() {} }; this.value = tag === "select" ? "Todos" : "";
            this.clientWidth = 800; this.clientHeight = 400;
        }
        append(...nodes) { this.children.push(...nodes); }
        replaceChildren(...nodes) { this.children = nodes; }
        insertBefore(node, reference) { this.children.splice(this.children.indexOf(reference), 0, node); }
        setAttribute(key, value) { this.attrs[key] = value; }
        addEventListener(name, handler) { const before = this.events[name]; this.events[name] = event => { before?.(event); handler(event); }; }
        cloneNode() { return new Element(this.tag); }
        getBoundingClientRect() { return { left: 200, top: 200, width: 48, height: 48 }; }
        click() { if (!this.disabled) this.events.click?.({ target: this, detail: 1 }); }
        dataset = {};
    }
    const previousDocument = globalThis.document, previousObserver = globalThis.ResizeObserver, previousWindow = globalThis.window;
    const timers = new Map(); let timerId = 0;
    const flushClicks = () => { for (const [id, callback] of [...timers]) { timers.delete(id); callback(); } };
    globalThis.window = { innerWidth: 800, setTimeout: callback => { timers.set(++timerId, callback); return timerId; }, clearTimeout: id => timers.delete(id) };
    globalThis.document = { createElement: tag => new Element(tag), createElementNS: (_ns, tag) => new Element(tag) };
    globalThis.ResizeObserver = class { observe() {} };
    t.after(() => { globalThis.document = previousDocument; globalThis.ResizeObserver = previousObserver; globalThis.window = previousWindow; });
    let closed = 0;
    const root = P.mount({ onConfirm: () => closed++ });
    const all = node => [node, ...node.children.flatMap(all)];
    const labelled = label => all(root).find(node => node.attrs['aria-label'] === label);
    const taskRobot = labelled("Tarefa do usuário. Use as setas para mover.");
    assert.equal(taskRobot.children[0].children[0].src, "/public/img/agents/19_tarefa-do-usuario.png");
    assert.equal(taskRobot.children[0].children[1].textContent, "Tarefa do usuário");
    assert.match(taskRobot.attrs['aria-description'], /inicia a esteira/);
    assert.equal(labelled("Adicionar Tarefa do usuário"), undefined);
    const confirm = all(root).find(node => node.textContent === "Confirmar esteira");
    const notice = all(root).find(node => node.className?.includes("plaza-result-notice"));
    assert.equal(notice.hidden, true);
    confirm.click();
    assert.equal(notice.hidden, false);
    assert.match(notice.children[0].textContent, /Falta adicionar o Resultado/); assert.equal(closed, 0);
    const description = all(root).find(node => node.attrs.role === "tooltip");
    const catalogCode = labelled("Adicionar Código");
    catalogCode.events.mouseenter();
    assert.equal(description.hidden, false); assert.match(description.textContent, /Produz, explica e revisa código/);
    catalogCode.events.mouseleave(); assert.equal(description.hidden, true);
    catalogCode.click();
    const code = labelled("Código. Use as setas para mover.");
    assert.equal(code.children[0].children[0].src, P.catalog.find(a => a.kind === "code").image);
    assert.equal(code.children[0].children[1].textContent, "Código");
    code.events.focusin(); assert.equal(description.hidden, false);
    assert.match(description.textContent, /Produz, explica e revisa código/);
    code.events.focusout(); assert.equal(description.hidden, true);
    assert.equal(all(root).some(node => node.className === "plaza-ports" || node.className === "plaza-remove"), false);
    assert.equal(root.clearSelection(), false);
    labelled("Tarefa do usuário. Use as setas para mover.").click(); flushClicks();
    assert.match(labelled("Tarefa do usuário. Use as setas para mover.").className, /is-connecting/);
    assert.equal(root.clearSelection(), true);
    assert.doesNotMatch(labelled("Tarefa do usuário. Use as setas para mover.").className, /is-connecting/);
    labelled("Código. Use as setas para mover.").click();
    assert.equal(root.clearSelection(), true); flushClicks();
    assert.doesNotMatch(labelled("Código. Use as setas para mover.").className, /is-connecting/);
    assert.equal(root.clearSelection(), false);
    labelled("Tarefa do usuário. Use as setas para mover.").click();
    labelled("Código. Use as setas para mover.").click(); flushClicks();
    assert.ok(labelled("Remover conexão de Tarefa do usuário para Código"));
    all(root).find(node => node.textContent === "Adicionar Resultado").click();
    const resultRobot = labelled("Resultado. Use as setas para mover.");
    assert.equal(resultRobot.children[0].children[0].src, "/public/img/agents/20_resultado.png");
    assert.match(resultRobot.attrs['aria-description'], /entrega final/);
    assert.equal(labelled("Adicionar Resultado"), undefined);
    assert.equal(notice.hidden, true);
    confirm.click(); assert.equal(closed, 0);
    labelled("Código. Use as setas para mover.").click();
    labelled("Resultado. Use as setas para mover.").click();
    // Confirmar também processa cliques pendentes sem exigir uma espera manual.
    confirm.click(); assert.equal(closed, 1); assert.equal(root.dataset.confirmed, "true");
    const resultCard = labelled("Resultado. Use as setas para mover.");
    resultCard.click(); resultCard.events.click({ detail: 2 }); resultCard.events.dblclick(); flushClicks();
    assert.equal(root.dataset.confirmed, "false");
    confirm.click(); assert.equal(notice.hidden, false); assert.equal(closed, 1);
    // Clique duplo cancela a conexão pendente para o destino removido.
    labelled("Tarefa do usuário. Use as setas para mover.").click();
    const codeCard = labelled("Código. Use as setas para mover.");
    codeCard.click(); codeCard.events.click({ detail: 2 }); codeCard.events.dblclick(); flushClicks();
    assert.equal(labelled("Código. Use as setas para mover."), undefined);
    assert.match(all(root).find(node => node.className === "plaza-summary").textContent, /0 agentes · 0 conexões/);
    const task = labelled("Tarefa do usuário. Use as setas para mover.");
    task.events.dblclick(); assert.ok(labelled("Tarefa do usuário. Use as setas para mover."));
});

test("praça: confirmação exige agentes conectados e resultado", () => {
    const g = P.createGraph(); assert.equal(P.validateGraph(g).valid, false);
    const a = P.addNode(g, "code");
    assert.match(P.validateGraph(g).message, /Conecte todos/);
    P.connect(g, "task", a.id);
    assert.match(P.validateGraph(g).message, /resultado/);
    const r = P.addNode(g, "result"); P.connect(g, a.id, r.id);
    assert.equal(P.validateGraph(g).valid, true);
    P.removeNode(g, a.id); assert.equal(P.validateGraph(g).valid, false);
});

test("praça: ramos abertos recebem consolidador sem inventar resultado", () => {
    const { graph: g, a, b } = parallel(false);
    assert.equal(P.validateGraph(g).needsMerge, true);
    assert.equal(P.consolidate(g), true);
    const merge = g.nodes.find(n => n.kind === "merge");
    assert.ok(g.edges.some(e => e.from === a.id && e.to === merge.id));
    assert.ok(g.edges.some(e => e.from === b.id && e.to === merge.id));
    assert.equal(P.validateGraph(g).needsMerge, false);
    assert.equal(P.validateGraph(g).valid, false);
    const r = P.addNode(g, "result"); P.connect(g, merge.id, r.id);
    assert.equal(P.validateGraph(g).valid, true);
});

test("praça: consolidação automática liga ramos abertos ao resultado existente", () => {
    const { graph: g, result } = parallel();
    P.consolidate(g);
    const merge = g.nodes.find(n => n.kind === "merge");
    assert.ok(g.edges.some(e => e.from === merge.id && e.to === result.id));
    assert.equal(P.validateGraph(g).valid, true);
    const before = JSON.stringify(g);
    assert.equal(P.consolidate(g), false); assert.equal(JSON.stringify(g), before);
});

test("praça: ramos ligados diretamente ao resultado são religados pelo consolidador", () => {
    const { graph: g, a, b, result } = parallel();
    P.connect(g, a.id, result.id); P.connect(g, b.id, result.id);
    assert.equal(P.validateGraph(g).needsMerge, true);
    P.consolidate(g);
    assert.equal(P.validateGraph(g).valid, true);
    assert.equal(g.nodes.filter(n => n.kind === "merge").length, 1);
    assert.equal(g.edges.filter(e => e.to === result.id).length, 1);
});

test("praça: agente comum não substitui consolidador e etapa posterior é preservada", () => {
    const { graph: g, a, b, result } = parallel();
    const writing = P.addNode(g, "writing");
    P.connect(g, a.id, writing.id); P.connect(g, b.id, writing.id); P.connect(g, writing.id, result.id);
    assert.equal(P.validateGraph(g).needsMerge, true);
    P.consolidate(g);
    assert.equal(P.validateGraph(g).valid, true);
    assert.ok(g.edges.some(e => e.from === writing.id && e.to === result.id));
    assert.ok(g.edges.some(e => e.from === g.nodes.find(n => n.kind === "merge").id && e.to === writing.id));
});

test("praça: ramo que desvia de um consolidador ainda exige consolidação", () => {
    const { graph: g, a, b, result } = parallel();
    const merge = P.addNode(g, "merge"), c = P.addNode(g, "research");
    P.connect(g, a.id, merge.id); P.connect(g, b.id, merge.id); P.connect(g, merge.id, result.id);
    P.connect(g, b.id, c.id);
    assert.equal(P.validateGraph(g).needsMerge, true);
    P.consolidate(g);
    assert.equal(P.validateGraph(g).valid, true);
});

test("praça: consolidador isolado não autoriza confirmação nem modifica agentes desconectados", () => {
    const { graph: g } = parallel(); P.addNode(g, "merge");
    assert.equal(P.validateGraph(g).valid, false);
    const before = JSON.stringify(g);
    assert.equal(P.consolidate(g), false); assert.equal(JSON.stringify(g), before);
});

test("praça: ramos aninhados podem compartilhar um consolidador final", () => {
    const { graph: g, a, b, result } = parallel();
    const c = P.addNode(g, "code"), d = P.addNode(g, "tests"), merge = P.addNode(g, "merge");
    for (const [from, to] of [[a.id,c.id],[a.id,d.id],[b.id,merge.id],[c.id,merge.id],[d.id,merge.id],[merge.id,result.id]]) P.connect(g, from, to);
    assert.equal(P.validateGraph(g).valid, true);
    assert.equal(P.consolidate(g), false);
});
test("praça: inicia só com a tarefa e permite ramos paralelos e consolidação", () => {
    const g = P.createGraph(); assert.equal(g.nodes.length, 1);
    const a = P.addNode(g, "policies"), b = P.addNode(g, "data"), merge = P.addNode(g, "merge"), result = P.addNode(g, "result");
    for (const [from, to] of [["task", a.id], ["task", b.id], [a.id, merge.id], [b.id, merge.id], [merge.id, result.id]]) assert.equal(P.connect(g, from, to), null);
    assert.equal(g.edges.length, 5);
    assert.equal(P.addNode(g, "result"), null);
});
test("praça: rejeita ciclos, duplicatas, destinos inválidos e saída do resultado", () => {
    const g = P.createGraph(), a = P.addNode(g, "code"), b = P.addNode(g, "tests"), r = P.addNode(g, "result");
    assert.equal(P.connect(g, a.id, b.id), null);
    for (const [from, to] of [[b.id, a.id], [a.id, b.id], [a.id, a.id], [a.id, "task"], [r.id, b.id], ["unknown", b.id]]) assert.ok(P.connect(g, from, to));
    assert.equal(g.edges.length, 1);
});
test("praça: remover etapa elimina conexões, preserva tarefa e permite recriar resultado", () => {
    const g = P.createGraph(), a = P.addNode(g, "merge"), r = P.addNode(g, "result");
    P.connect(g, "task", a.id); P.connect(g, a.id, r.id); P.removeNode(g, a.id);
    assert.equal(g.edges.length, 0); P.removeNode(g, "task"); assert.ok(g.nodes.some(n => n.id === "task"));
    P.removeNode(g, r.id); assert.ok(P.addNode(g, "result")); assert.equal(P.addNode(g, "unknown"), null);
});
