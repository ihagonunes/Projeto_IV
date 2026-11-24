/* SPI-180 · Execution from the chat composer, using the existing agents API. */
(function (root, factory) {
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    else root.AtlasAgentChat = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
    "use strict";
    function createRunner(client) {
        let pending = false;
        return {
            async run(flow, input, idempotencyKey) {
                if (pending) throw Object.assign(new Error("Uma execução já está em andamento."), { status: 409 });
                if (!flow?.id || flow.status !== "PUBLISHED") throw new Error("Confirme o fluxo na praça antes de enviar sua tarefa.");
                if (typeof input !== "string" || !input.trim() || input.length > 8000) throw new Error("Escreva uma tarefa de até 8.000 caracteres.");
                pending = true;
                try {
                    return await client.post(`/agentes/fluxos/${encodeURIComponent(flow.id)}/execucoes`,
                        { input }, { headers: { "Idempotency-Key": idempotencyKey } });
                } finally { pending = false; }
            }
        };
    }
    function presentation(flow, view) {
        const states = { SUCCEEDED: "Concluído", FAILED: "Interrompido", TIMED_OUT: "Tempo excedido", SKIPPED: "Não executado", CANCELLED: "Cancelado" };
        const graph = flow?.graph?.nodes || [];
        const hasResult = typeof view.result === "string" && view.result.trim().length > 0;
        const completedWithoutText = view.status === "SUCCEEDED" && !hasResult;
        const emptyResponse = view.errorCode === "AGENT_EMPTY_RESPONSE";
        return {
            title: completedWithoutText ? "Fluxo concluído sem resposta" : view.status === "SUCCEEDED" ? "Resultado do fluxo" : "O fluxo foi interrompido",
            content: hasResult ? view.result : completedWithoutText
                ? "Os agentes concluíram a execução, mas nenhum texto de resultado foi recebido. Não houve uma nova tentativa automática."
                : emptyResponse
                ? "O modelo devolveu uma resposta vazia. O fluxo foi interrompido para não executar as próximas etapas. O consumo desta chamada pode ter sido registrado."
                : (view.status === "TIMED_OUT"
                ? "A execução ultrapassou o tempo disponível. Você pode tentar novamente quando quiser."
                : "Não foi possível concluir a tarefa. Confira o orçamento de IA e a disponibilidade do modelo antes de tentar novamente."),
            steps: (view.steps || []).filter(s => s.nodeKind === "agent").map(s => ({
                name: graph.find(n => n.id === s.nodeId)?.name || "Agente",
                status: states[s.status] || "Em andamento", failed: ["FAILED", "TIMED_OUT"].includes(s.status)
            })),
            succeeded: view.status === "SUCCEEDED" && hasResult
        };
    }
    return { createRunner, presentation };
});
