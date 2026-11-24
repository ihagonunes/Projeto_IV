"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const A = require("../../main/resources/static/public/js/pages/agent-chat");
const flow = { id: "flow-1", status: "PUBLISHED", graph: { nodes: [{ id: "a", name: "Projetos" }] } };

test("agentes no chat: criar runner e selecionar fluxo não chama nenhum modelo", () => {
    let calls = 0;
    A.createRunner({ post() { calls++; } });
    assert.equal(calls, 0);
});
test("agentes no chat: envio explícito usa execução existente, com chave e sem alterar o contrato de conversas", async () => {
    const calls = [];
    const runner = A.createRunner({ async post(...args) { calls.push(args); return { status: "SUCCEEDED", result: "Exemplo local" }; } });
    const result = await runner.run(flow, "Tarefa local", "key-0001");
    assert.equal(result.status, "SUCCEEDED");
    assert.deepEqual(calls, [["/agentes/fluxos/flow-1/execucoes", { input: "Tarefa local" }, { headers: { "Idempotency-Key": "key-0001" } }]]);
});
test("agentes no chat: fluxo não publicado ou entrada inválida não chegam à API", async () => {
    let calls = 0;
    const runner = A.createRunner({ post() { calls++; } });
    await assert.rejects(runner.run({ ...flow, status: "DRAFT" }, "Tarefa", "key-0001"));
    for (const input of ["", "  ", "a".repeat(8001)]) await assert.rejects(runner.run(flow, input, "key-0001"));
    assert.equal(calls, 0);
});
test("agentes no chat: clique duplicado não executa duas vezes e falha libera nova tentativa explícita", async () => {
    let reject, calls = 0;
    const runner = A.createRunner({ post() { calls++; return new Promise((_resolve, fail) => { reject = fail; }); } });
    const first = runner.run(flow, "Tarefa", "key-0001");
    await assert.rejects(runner.run(flow, "Tarefa", "key-0002"), e => e.status === 409);
    reject(new Error("Falha simulada"));
    await assert.rejects(first);
    const retry = runner.run(flow, "Tarefa", "key-0003");
    assert.equal(calls, 2);
    reject(new Error("Falha simulada")); await assert.rejects(retry);
});
test("agentes no chat: resposta com erro usa os nomes dos agentes, sem expor ids técnicos", () => {
    const p = A.presentation(flow, { status: "FAILED", errorCode: "AGENT_COMPLETION_FAILED", steps: [{ nodeId: "a", nodeKind: "agent", status: "FAILED" }] });
    assert.equal(p.succeeded, false);
    assert.deepEqual(p.steps, [{ name: "Projetos", status: "Interrompido", failed: true }]);
    assert.ok(!p.content.includes("AGENT_COMPLETION_FAILED"));
});
