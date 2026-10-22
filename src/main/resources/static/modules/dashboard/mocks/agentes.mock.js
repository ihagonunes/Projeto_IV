"use strict";

// Massa determinística EXCLUSIVA de desenvolvimento. Não representa agentes disponíveis
// nem regras de cálculo/autorização reais. Valores monetários em USD e BRL explícitos.
const USD_BRL_DEMO = 5.15; // Referência temporária, não cotação oficial/dinâmica.
const agents = [
    { id: "politicas-demo", name: "Agente de Políticas" },
    { id: "orcamento-demo", name: "Projeção orçamentária" }
];
const dayBoundaries = [6, 20, 30, 38, 54, 59, 72, 79, 90];
const executions = Array.from({ length: 90 }, (_, i) => {
    const day = 1 + dayBoundaries.findIndex(end => i < end);
    const totalMs = i === 0 ? 80 : i === 89 ? 2000 : 120 + (i * 137) % 1850;
    const ragMs = Math.floor(totalMs * (i % 3 === 0 ? 0.48 : 0.16));
    const toolMs = Math.floor(totalMs * (i % 3 === 1 ? 0.49 : 0.12));
    const llmMs = Math.floor(totalMs * (i % 3 === 2 ? 0.56 : 0.22));
    const costUsd = (1 + i % 5) / 100;
    return {
        sessionId: "DEMO-" + String(i + 1).padStart(4, "0"),
        agentId: agents[i % 2].id,
        model: ["Modelo Alfa (demo)", "Modelo Beta (demo)", "Modelo Gama (demo)"][i % 3],
        occurredAt: "2026-10-" + String(day).padStart(2, "0") + "T" +
            String(3 + (i % 10) * 2).padStart(2, "0") + ":30:00-03:00",
        inputTokens: 120 + (i * 193) % 2400,
        outputTokens: 30 + (i * 71) % 800,
        totalMs, ragMs, toolMs, llmMs, costUsd,
        costBrl: Number((costUsd * USD_BRL_DEMO).toFixed(5)),
        status: i % 13 === 0 ? "FAILED" : "COMPLETED"
    };
});
module.exports = { USD_BRL_DEMO, agents, executions };
