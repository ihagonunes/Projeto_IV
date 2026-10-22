"use strict";
const { agents, executions, USD_BRL_DEMO } = require("./agentes.mock");
const timeZone = "America/Sao_Paulo";
const referenceDay = "2026-10-09";
const dayFormatter = new Intl.DateTimeFormat("en-CA", {
    timeZone, year: "numeric", month: "2-digit", day: "2-digit"
});
const dayOf = value => dayFormatter.format(new Date(value));
const periods = {
    semanal: { start: "2026-10-04", end: "2026-10-10", label: "04 a 10 de outubro de 2026 · domingo a sábado" },
    mensal: { start: "2026-10-01", end: "2026-10-31", label: "Outubro de 2026" }
};
const labels = { colaborador: "Colaborador", lider: "Líder", gestor: "Gestor", executivo: "Executivo", agentes: "Análise de agentes" };

// Agregações abaixo são SOMENTE preparação de fixtures para teste da apresentação.
// A futura API deverá entregar indicadores, percentis e séries prontos.
// Percentis demonstrativos: nearest-rank; não define o critério do produto.
const sum = (rows, key) => rows.reduce((value, row) => value + row[key], 0);
const mean = (rows, key) => rows.length ? Math.round(sum(rows, key) / rows.length) : null;
const percentile = (rows, p) => {
    const sorted = rows.map(row => row.totalMs).sort((a, b) => a - b);
    return sorted.length ? sorted[Math.ceil(p * sorted.length) - 1] : null;
};
function dates(period) {
    const result = [];
    for (let date = new Date(period.start + "T12:00:00Z"); date.toISOString().slice(0, 10) <= period.end; date.setUTCDate(date.getUTCDate() + 1)) {
        result.push(date.toISOString().slice(0, 10));
    }
    return result;
}
function agentSnapshot(periodKey, agentId, empty) {
    const period = periods[periodKey];
    const source = empty ? [] : executions.filter(row => !agentId || row.agentId === agentId);
    const rows = source.filter(row => dayOf(row.occurredAt) >= period.start && dayOf(row.occurredAt) <= period.end);
    return {
        kpis: {
            latencyMeanMs: mean(rows, "totalMs"), p50Ms: percentile(rows, 0.5), p95Ms: percentile(rows, 0.95),
            total: rows.length, dailyCostBrl: sum(source.filter(row => dayOf(row.occurredAt) === referenceDay), "costBrl"),
            successPercent: rows.length ? 100 * rows.filter(row => row.status === "COMPLETED").length / rows.length : null
        },
        timeline: dates(period).map(day => {
            const group = rows.filter(row => dayOf(row.occurredAt) === day);
            return { label: day.slice(8) + "/" + day.slice(5, 7), executions: group.length, latencyMs: mean(group, "totalMs") };
        }),
        stages: agents.filter(agent => !agentId || agent.id === agentId).map(agent => {
            const group = rows.filter(row => row.agentId === agent.id);
            return { label: agent.name, ragMs: mean(group, "ragMs"), toolMs: mean(group, "toolMs"), llmMs: mean(group, "llmMs"), totalMs: mean(group, "totalMs") };
        }),
        executions: rows.slice().sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
    };
}
function collaboratorSnapshot(periodKey, empty) {
    // Este conjunto contém SOMENTE dados individuais da pessoa demonstrativa.
    // Não derive os dados operacionais a partir do conjunto gerencial de agentes.
    const period = periods[periodKey];
    const timeline = dates(period).map(day => {
        const i = Number(day.slice(8)) - 1;
        return {
        label: day.slice(8) + "/" + day.slice(5, 7),
        totalTokens: empty || day > referenceDay ? 0 : 1960 + i * 190,
        inputTokens: empty || day > referenceDay ? 0 : 1500 + i * 137,
        outputTokens: empty || day > referenceDay ? 0 : 460 + i * 53,
        averageTokens: empty || day > referenceDay ? 0 : 2200 + i * 121
        };
    });
    return {
        kpis: {
            totalTokens: sum(timeline, "totalTokens"),
            inputTokens: sum(timeline, "inputTokens"), outputTokens: sum(timeline, "outputTokens"),
            rank: empty ? null : periodKey === "semanal" ? 8 : 12,
            credits: empty ? 0 : periodKey === "semanal" ? 320 : 1080,
            averageTokens: empty ? null : sum(timeline, "averageTokens")
        },
        timeline,
        history: empty ? [] : [
            { id: "minha-demo-03", title: "Consulta às políticas internas", date: "2026-10-09T09:15:00-03:00", channel: "Agente de Políticas", href: "/agente-politicas" },
            { id: "minha-demo-02", title: "Organização das atividades", date: "2026-10-08T14:30:00-03:00", channel: "Chat", href: "/chat" },
            { id: "minha-demo-01", title: "Resumo de anotações", date: "2026-10-05T10:45:00-03:00", channel: "Chat", href: "/chat" }
        ]
    };
}
function getDashboard({ profile, period, state, agent }) {
    if (state === "erro") {
        const error = new Error("Falha simulada da fonte de dados.");
        error.demo = true;
        throw error;
    }
    const pending = ["lider", "gestor", "executivo"].includes(profile);
    return {
        meta: { simulated: true, timeZone, referenceDay, usdBrl: USD_BRL_DEMO, version: 1 },
        profile, title: labels[profile], period: { key: period, ...periods[period] },
        pending, state, agents: profile === "agentes" ? agents : [],
        selectedAgent: agent,
        data: pending ? null : profile === "agentes" ? agentSnapshot(period, agent, state === "vazio") : collaboratorSnapshot(period, state === "vazio")
    };
}
module.exports = { getDashboard, periods, labels, dayOf, agentSnapshot };
