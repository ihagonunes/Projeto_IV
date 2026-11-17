"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const P = require("../../main/resources/static/public/js/pages/agent-plaza");
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
