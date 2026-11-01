/* SPI-136: lógica de apresentação e adapters; nenhuma regra de autorização local. */
(function (root, factory) {
    const core = factory();
    if (typeof module === "object" && module.exports) module.exports = core;
    else root.AtlasChat = core;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
    "use strict";
    const MAX_LENGTH = 8000;
    const models = [
        { id: "preview-balanced", name: "Atlas Equilíbrio", provider: "Provider de demonstração", description: "Para escrever, planejar e explorar ideias", badge: "Padrão" },
        { id: "preview-reasoning", name: "Atlas Raciocínio", provider: "Provider de demonstração", description: "Para análises e problemas com várias etapas", badge: "Análise" },
        { id: "preview-fast", name: "Atlas Ágil", provider: "Provider de demonstração", description: "Para tarefas rápidas do dia a dia", badge: "Rápido" }
    ];
    const errorMessages = {
        400: "Confira a mensagem: escreva entre 1 e 8.000 caracteres.",
        401: "Sua sessão expirou. Entre novamente para continuar.",
        403: "Seu acesso ao chat ou ao modelo não está permitido. Consulte o administrador.",
        404: "Esta conversa não está disponível para sua conta.",
        409: "O modelo não está disponível ou seu departamento precisa de configuração.",
        502: "O serviço de IA não conseguiu responder. Sua mensagem pode ter sido salva.",
        503: "Não foi possível confirmar o registro da execução. Confira o histórico antes de reenviar.",
        504: "O serviço de IA demorou para responder. Confira o histórico antes de reenviar."
    };
    function validateMessage(value) { return typeof value === "string" && value.trim().length > 0 && value.length <= MAX_LENGTH; }
    function errorMessage(error) {
        return error?.name === "AbortError" ? "Espera interrompida. A execução no servidor pode continuar; confira o histórico antes de reenviar."
            : errorMessages[error?.status] || "Não foi possível confirmar o envio. Confira sua conexão e o histórico antes de reenviar.";
    }
    function costLabel(receipt) {
        return receipt && receipt.statusCusto === "KNOWN" && receipt.custoEstimado != null
            ? Number(receipt.custoEstimado).toLocaleString("pt-BR", { minimumFractionDigits: 6, maximumFractionDigits: 6 })
            : "Indisponível";
    }
    function tokenLabel(value) { return value == null ? "Indisponível" : Number(value).toLocaleString("pt-BR"); }
    function mergeById(current, incoming) {
        const result = new Map(current.map(item => [item.id, item]));
        incoming.forEach(item => result.set(item.id, item));
        return [...result.values()];
    }
    function filterConversations(conversations, search, archived) {
        const term = search.toLocaleLowerCase("pt-BR").normalize("NFD").replace(/[\u0300-\u036f]/g, "");
        return conversations.filter(item => (item.status === "ARCHIVED") === archived && item.titulo
            .toLocaleLowerCase("pt-BR").normalize("NFD").replace(/[\u0300-\u036f]/g, "").includes(term))
            .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt));
    }
    function createLiveService(api, transport = globalThis.fetch) {
        return {
            preview: false,
            list: page => api.get(`/conversas?page=${page}&size=20`),
            messages: id => api.get(`/conversas/${encodeURIComponent(id)}/mensagens`),
            async models() {
                const data = await api.get("/conversas/modelos");
                const allowed = (data.models || []).filter(model => model.available);
                const mapped = allowed.map(model => ({
                    id: model.id,
                    name: model.name,
                    provider: model.providerName || "Provider",
                    description: model.id === data.defaultModelId
                        ? "Padrão do seu departamento" : "Permitido pelo seu departamento",
                    badge: model.id === data.defaultModelId ? "Padrão" : "Alternativo",
                    isDefault: model.id === data.defaultModelId
                }));
                return mapped.sort((a, b) => Number(b.isDefault) - Number(a.isDefault))
                    .map(({ isDefault, ...model }) => model);
            },
            async send(id, content, modelId, signal) {
                if (!validateMessage(content)) return Promise.reject(Object.assign(new Error("Mensagem inválida"), { status: 400 }));
                const path = id ? `/conversas/${encodeURIComponent(id)}/mensagens` : "/conversas";
                const response = await transport(`/api/v1${path}`, {
                    method: "POST", credentials: "same-origin", signal,
                    headers: { "Content-Type": "application/json", "Accept": "application/json", "X-Qsoft-Request": "1" },
                    body: JSON.stringify({ mensagem: content, requestedModelId: modelId })
                });
                let data;
                try { data = await response.json(); }
                catch (error) {
                    if (error.name === "AbortError") throw error;
                    throw Object.assign(new Error("Resposta inválida do serviço"), { status: response.status });
                }
                if (!response.ok) throw Object.assign(new Error("Falha no envio"), { status: response.status });
                return data;
            }
        };
    }
    // Fixtures somente em memória: nenhuma conversa/arquivo vai a storage ou à rede.
    function createPreviewService() {
        let sequence = 0;
        const id = () => `preview-${++sequence}`;
        const now = () => new Date().toISOString();
        let conversations, messages, receipts;
        const service = {
            preview: true, scenario: "success", quota: { used: 8400, limit: 10000, input: 3200, output: 5200 },
            reset() {
                sequence = 0; receipts = new Map(); messages = new Map();
                conversations = [
                    ["Planejamento do próximo trimestre", "Como organizar as prioridades do próximo trimestre?", "Comece com três decisões: o resultado que você quer atingir, os recursos disponíveis e os critérios de sucesso.\n\n1. Defina até três objetivos prioritários.\n2. Transforme cada objetivo em entregas mensuráveis.\n3. Distribua responsáveis e marcos no trimestre.\n4. Reserve um momento semanal para revisar o progresso.\n\nQual é a principal meta da sua equipe neste momento?"],
                    ["Ideias para apresentação da equipe", "Preciso de uma estrutura para apresentar os resultados da equipe.", "Uma narrativa clara pode seguir esta sequência:\n\n1. Contexto e objetivo do período\n2. Principais entregas e evidências\n3. Aprendizados e desafios\n4. Próximos passos e decisões necessárias\n\nComece pelo resultado mais relevante e use poucos indicadores em cada slide."],
                    ["Revisão de comunicado interno", "Como deixar um comunicado mais claro?", "Abra com a informação principal, explique quem é afetado e termine com a ação esperada. Se quiser, cole um exemplo fictício para trabalharmos juntos."],
                    ["Checklist de início de projeto", "O que preciso definir antes de iniciar um projeto?", "Defina objetivo, escopo, responsáveis, prazo e critérios de aceite. Registre também riscos e dependências para alinhar as expectativas da equipe."]
                ].map((entry, index) => {
                    const conversation = { id: id(), titulo: entry[0], status: "ACTIVE", createdAt: new Date(Date.now() - index * 86400000).toISOString(), updatedAt: new Date(Date.now() - index * 86400000).toISOString() };
                    const user = { id: id(), role: "USER", content: entry[1], requestId: null, createdAt: conversation.createdAt };
                    const assistant = { id: id(), role: "ASSISTANT", content: entry[2], requestId: id(), createdAt: conversation.createdAt };
                    messages.set(conversation.id, [user, assistant]);
                    receipts.set(assistant.requestId, makeReceipt(assistant.requestId, models[0]));
                    return conversation;
                });
                service.scenario = "success"; service.quota = { used: 8400, limit: 10000, input: 3200, output: 5200 };
            },
            async list() { return { content: structuredClone(conversations), page: 0, totalPages: 1, totalElements: conversations.length }; },
            async messages(conversationId) { return structuredClone(messages.get(conversationId) || []); },
            async models() { return structuredClone(models); },
            receipt(requestId) { return receipts.get(requestId); },
            async regenerate(conversationId, messageId, modelId) {
                const thread = messages.get(conversationId);
                const index = thread?.findIndex(item => item.id === messageId);
                if (index == null || index < 0 || thread[index].role !== "ASSISTANT") throw new Error("Resposta não encontrada");
                const model = models.find(item => item.id === modelId) || models[0];
                const original = thread[index];
                const assistant = { ...original, id: id(), requestId: id(), createdAt: now(), content: "Uma outra forma de abordar a questão:\n\n• Comece pelo resultado desejado.\n• Identifique a principal restrição.\n• Compare duas alternativas antes de decidir.\n\nEsta é uma nova resposta fictícia da prévia, sem consulta à IA." };
                thread[index] = assistant; receipts.delete(original.requestId);
                receipts.set(assistant.requestId, makeReceipt(assistant.requestId, model));
                service.quota.used += 460; service.quota.input += 120; service.quota.output += 340;
                return structuredClone(assistant);
            },
            async rename(conversationId, title) { const c = conversations.find(item => item.id === conversationId); c.titulo = title.trim(); },
            async archive(conversationId, archived) { conversations.find(item => item.id === conversationId).status = archived ? "ARCHIVED" : "ACTIVE"; },
            async remove(conversationId) { const old = messages.get(conversationId) || []; old.forEach(m => receipts.delete(m.requestId)); messages.delete(conversationId); conversations = conversations.filter(item => item.id !== conversationId); },
            async send(conversationId, content, modelId, signal) {
                if (!validateMessage(content)) throw Object.assign(new Error("Mensagem inválida"), { status: 400 });
                let c = conversations.find(item => item.id === conversationId);
                if (!c) { c = { id: id(), titulo: content.trim().replace(/\s+/g, " ").slice(0, 60), status: "ACTIVE", createdAt: now(), updatedAt: now() }; conversations.unshift(c); messages.set(c.id, []); }
                const user = { id: id(), role: "USER", content, requestId: null, createdAt: now() };
                messages.get(c.id).push(user); c.updatedAt = now();
                await new Promise((resolve, reject) => {
                    if (signal?.aborted) return reject(new DOMException("Aborted", "AbortError"));
                    const abort = () => { clearTimeout(timer); reject(new DOMException("Aborted", "AbortError")); };
                    const timer = setTimeout(() => { signal?.removeEventListener("abort", abort); resolve(); }, 900);
                    signal?.addEventListener("abort", abort, { once: true });
                });
                if (service.scenario !== "success") throw Object.assign(new Error("Cenário de prévia"), { status: Number(service.scenario) });
                const model = models.find(item => item.id === modelId) || models[0];
                const assistant = { id: id(), role: "ASSISTANT", content: "Vamos organizar essa ideia.\n\n1. Defina o objetivo e o contexto da tarefa.\n2. Separe as informações que você já tem das que precisa buscar.\n3. Escolha uma primeira ação pequena e verificável.\n\nQual resultado você espera alcançar?\n\nEsta é uma resposta fictícia da prévia interativa; nenhuma IA foi consultada.", requestId: id(), createdAt: now() };
                messages.get(c.id).push(assistant); c.updatedAt = now();
                const receipt = makeReceipt(assistant.requestId, model); receipts.set(assistant.requestId, receipt);
                service.quota.used += 460; service.quota.input += 120; service.quota.output += 340;
                return { conversa: structuredClone(c), mensagens: [user, assistant], recibo: receipt };
            }
        };
        function makeReceipt(requestId, model) { return { requestId, modelo: model.name, provider: model.provider, tokensEntrada: 120, tokensSaida: 340, custoEstimado: 0.000920, statusCusto: "KNOWN", latenciaMs: 812 }; }
        service.reset(); return service;
    }
    return { MAX_LENGTH, validateMessage, errorMessage, costLabel, tokenLabel, mergeById, filterConversations, createLiveService, createPreviewService };
});
