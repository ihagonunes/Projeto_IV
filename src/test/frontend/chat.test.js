"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const C = require("../../main/resources/static/public/js/pages/chat-core");
const express = require("express");
const path = require("node:path");
const fs = require("node:fs");

test("chat: valida branco e limite sem confundir caracteres com tokens", () => {
    for (const value of [null, "", "  ", "a".repeat(8001)]) assert.equal(C.validateMessage(value), false);
    assert.equal(C.validateMessage("a".repeat(8000)), true);
    assert.equal(C.validateMessage("Olá\nAtlas"), true);
});
test("chat: dados de custo/tokens desconhecidos nunca viram zero", () => {
    assert.equal(C.costLabel({ statusCusto: "INCONCLUSIVE", custoEstimado: 0 }), "Indisponível");
    assert.equal(C.costLabel({ statusCusto: "KNOWN", custoEstimado: null }), "Indisponível");
    assert.equal(C.costLabel({ statusCusto: "KNOWN", custoEstimado: 0 }), "0,000000");
    assert.equal(C.tokenLabel(null), "Indisponível");
    assert.equal(C.tokenLabel(0), "0");
});
test("chat: histórico filtra acentos, status e ordena sem alterar origem", () => {
    const items = [{ id: "a", titulo: "Revisão", status: "ACTIVE", updatedAt: "2026-10-01" },
        { id: "b", titulo: "Revisão 2", status: "ARCHIVED", updatedAt: "2026-10-02" }];
    assert.deepEqual(C.filterConversations(items, "revisao", false).map(c => c.id), ["a"]);
    assert.deepEqual(C.filterConversations(items, "revisao", true).map(c => c.id), ["b"]);
    assert.equal(items.length, 2);
    assert.deepEqual(C.mergeById([{ id: "a", v: 1 }], [{ id: "a", v: 2 }, { id: "b" }]), [{ id: "a", v: 2 }, { id: "b" }]);
});
test("chat conectado: envio usa somente contrato existente, cookie, CSRF e AbortSignal", async () => {
    const calls = []; const signal = new AbortController().signal;
    const service = C.createLiveService({ async get(path) { calls.push(path); return { defaultModelId: "model-2", models: [
        { id: "model-1", name: "Modelo real", providerName: "Provider real", available: true },
        { id: "model-2", name: "Modelo padrão", providerName: "Provider real", available: true },
        { id: "model-3", name: "Indisponível", providerName: "Provider real", available: false }
    ] }; } },
        async (url, options) => { calls.push({ url, options }); return { ok: true, async json() { return { conversa: { id: "real" } }; } }; });
    const models = await service.models();
    assert.equal(calls[0], "/conversas/modelos");
    assert.deepEqual(models.map(model => model.id), ["model-2", "model-1"]);
    assert.equal(models[0].badge, "Padrão");
    await service.send(null, "Olá", null, signal);
    assert.equal(calls[1].url, "/api/v1/conversas");
    assert.equal(calls[1].options.credentials, "same-origin"); assert.equal(calls[1].options.signal, signal);
    assert.equal(calls[1].options.headers["X-Qsoft-Request"], "1");
    const firstBody = JSON.parse(calls[1].options.body);
    assert.equal(firstBody.mensagem, "Olá");
    assert.equal(firstBody.requestedModelId, null);
    assert.match(firstBody.clientMessageId, /^[A-Za-z0-9_-]{8,64}$/);
    assert.equal(calls[1].options.headers.Authorization, undefined);
    await service.send("abc", "Outra", "uuid", signal);
    assert.equal(calls[2].url, "/api/v1/conversas/abc/mensagens");
    assert.match(JSON.parse(calls[2].options.body).clientMessageId, /^[A-Za-z0-9_-]{8,64}$/);
    await assert.rejects(service.send(null, " ", null, signal), error => error.status === 400);
    assert.equal(calls.length, 3);
});
test("chat conectado: erros mantêm status para apresentação sem expor corpo do serviço", async () => {
    const service = C.createLiveService({}, async () => ({ ok: false, status: 504, json: async () => ({ message: "detalhe interno" }) }));
    await assert.rejects(service.send("id", "Olá", null), error => error.status === 504 && !error.message.includes("interno"));
    for (const status of [400, 401, 403, 404, 409, 502, 503, 504]) assert.ok(C.errorMessage({ status }).length > 20);
    assert.match(C.errorMessage({ name: "AbortError" }), /servidor pode continuar/);
});
test("chat conectado: interrupção durante leitura da resposta mantém estado desconhecido", async () => {
    const service = C.createLiveService({}, async () => ({ ok: true, status: 201,
        async json() { throw new DOMException("Aborted", "AbortError"); } }));
    await assert.rejects(service.send(null, "Olá", null), error => error.name === "AbortError"
        && C.errorMessage(error).includes("servidor pode continuar"));
});
test("chat conectado: histórico, gestão e consumo usam os contratos reais", async () => {
    const calls = [];
    const api = {
        async get(url) {
            calls.push({ method: "GET", url });
            if (url.includes("observability")) return { series: [{ requests: 3 }], activeUsers: 1 };
            return { content: [], page: 0, totalPages: 1 };
        },
        async patch(url, body) {
            calls.push({ method: "PATCH", url, body });
            return { id: "c1", titulo: body.titulo || "Título", status: body.status || "ACTIVE" };
        },
        async delete(url) { calls.push({ method: "DELETE", url }); return ""; }
    };
    const service = C.createLiveService(api, async () => ({ ok: true, json: async () => ({}) }));

    await service.list(0, "ARCHIVED", "a%b");
    assert.match(calls[0].url, /^\/conversas\?/);
    assert.match(calls[0].url, /status=ARCHIVED/);
    assert.match(calls[0].url, /q=a%25b/);

    const renamed = await service.rename("c1", "Novo título");
    assert.equal(calls[1].method, "PATCH");
    assert.equal(calls[1].url, "/conversas/c1");
    assert.deepEqual(calls[1].body, { titulo: "Novo título" });
    assert.equal(renamed.titulo, "Novo título");

    await service.archive("c1", true);
    assert.deepEqual(calls[2].body, { status: "ARCHIVED" });
    await service.remove("c1");
    assert.equal(calls[3].method, "DELETE");
    assert.equal(calls[3].url, "/conversas/c1");

    const usage = await service.usage();
    assert.equal(calls[4].url, "/observability/metrics/me/usage");
    assert.equal(usage.activeUsers, 1);
});

test("chat conectado: recibos duráveis e clientMessageId seguro", () => {
    const messages = [
        { id: "m1", role: "ASSISTANT", requestId: "r1", recibo: { requestId: "r1", modelo: "Modelo" } },
        { id: "m2", role: "ASSISTANT", requestId: "r2", recibo: null }
    ];
    const receipts = C.collectReceipts(messages);
    assert.equal(receipts.size, 1);
    assert.equal(receipts.get("r1").modelo, "Modelo");
    assert.match(C.clientMessageId(), /^[A-Za-z0-9_-]{8,64}$/);
    assert.match(C.errorMessage({ status: 409, data: { code: "CONVERSATION_ARCHIVED" } }), /arquivada/);
    assert.match(C.errorMessage({ status: 409, data: { code: "TURN_IN_PROGRESS_OR_FAILED" } }), /processamento/);
});
test("chat prévia: modelo por turno, recibo, regeneração e histórico sem duplicar pergunta", async () => {
    const service = C.createPreviewService();
    const turn = await service.send(null, "Uma ideia", "preview-reasoning");
    assert.equal(turn.recibo.modelo, "Atlas Raciocínio"); assert.equal(turn.recibo.requestId, turn.mensagens[1].requestId);
    const regenerated = await service.regenerate(turn.conversa.id, turn.mensagens[1].id, "preview-fast");
    const messages = await service.messages(turn.conversa.id);
    assert.equal(messages.length, 2); assert.equal(messages.filter(m => m.role === "USER").length, 1);
    assert.equal(service.receipt(regenerated.requestId).modelo, "Atlas Ágil");
    await service.rename(turn.conversa.id, "  Novo título  "); await service.archive(turn.conversa.id, true);
    const conversation = (await service.list()).content.find(c => c.id === turn.conversa.id);
    assert.equal(conversation.titulo, "Novo título"); assert.equal(conversation.status, "ARCHIVED");
    await service.remove(turn.conversa.id); assert.equal((await service.list()).content.some(c => c.id === turn.conversa.id), false);
    service.reset(); assert.equal((await service.list()).content.length, 4);
});
test("chat prévia: falha preserva USER e interrupção não fabrica resposta", async () => {
    const service = C.createPreviewService(); const conversation = (await service.list()).content[0];
    const original = (await service.messages(conversation.id)).length;
    service.scenario = "502";
    await assert.rejects(service.send(conversation.id, "Falha", null), error => error.status === 502);
    assert.equal((await service.messages(conversation.id)).length, original + 1);
    const controller = new AbortController(); const result = service.send(conversation.id, "Interromper", null, controller.signal); controller.abort();
    await assert.rejects(result, error => error.name === "AbortError");
    assert.equal((await service.messages(conversation.id)).at(-1).role, "USER");
});
test("chat prévia: sessões isoladas e cópias de dados não alteram origem", async () => {
    const first = C.createPreviewService(); const second = C.createPreviewService();
    const list = await first.list(); list.content[0].titulo = "Tentativa";
    assert.notEqual((await first.list()).content[0].titulo, "Tentativa");
    await first.remove(list.content[0].id); assert.equal((await second.list()).content.length, 4);
});
test("chat: página conectada exige sessão; prévia renderiza sem API ou script de autenticação", async t => {
    const app = express(); const base = path.resolve(__dirname, "../../main/resources/static");
    app.set("views", [path.join(base, "views"), path.join(base, "modules")]); app.set("view engine", "ejs");
    app.use(require("express-ejs-layouts")); let sessionChecks = 0;
    app.use(require(path.join(base, "routes/chat.routes"))((req, res) => { sessionChecks++; res.redirect("/login"); }));
    const server = app.listen(0, "127.0.0.1"); await new Promise(resolve => server.once("listening", resolve));
    t.after(() => new Promise(resolve => server.close(resolve)));
    const url = `http://127.0.0.1:${server.address().port}`;
    const live = await fetch(`${url}/chat`, { redirect: "manual" }); assert.equal(live.status, 302); assert.equal(live.headers.get("location"), "/login");
    const preview = await fetch(`${url}/chat/previa`); const html = await preview.text();
    assert.equal(preview.status, 200); assert.match(html, /data-preview="true"/); assert.match(html, /chat-core\.js/);
    assert.doesNotMatch(html, /core\/api-client\.js/); assert.equal(sessionChecks, 1);
});

test("chat: renderiza��o n�o usa innerHTML e a busca � limitada a 100", () => {
    const base = path.resolve(__dirname, "../../main/resources/static");
    for (const file of ["public/js/pages/chat.js", "public/js/pages/chat-core.js", "modules/chat/views/index.ejs"]) {
        const source = fs.readFileSync(path.join(base, file), "utf8");
        assert.ok(!source.includes("innerHTML"), `${file} n�o pode usar innerHTML`);
    }
    const view = fs.readFileSync(path.join(base, "modules/chat/views/index.ejs"), "utf8");
    assert.match(view, /id="chat-search"[^>]*maxlength="100"/);
});
