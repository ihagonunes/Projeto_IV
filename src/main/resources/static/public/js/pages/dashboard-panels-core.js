/* SPI-93: apresentação dos agregados autorizados. Nenhum score é calculado aqui. */
(function (root, factory) {
    const core = factory();
    if (typeof module === "object" && module.exports) module.exports = core;
    else root.AtlasPanels = core;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
    "use strict";
    const number = value => value == null ? "Sem dado" : Number(value).toLocaleString("pt-BR", { maximumFractionDigits: 4 });
    const money = (value, source) => value == null ? "Sem dado" : source === "HISTORICO"
        ? Number(value).toLocaleString("pt-BR", { style: "currency", currency: "USD", maximumFractionDigits: 4 })
        : `${number(value)} (moeda não informada)`;
    const sum = (rows, key) => rows.reduce((total, row) => total + Number(row[key] || 0), 0);
    const knownSum = (rows, key) => rows.some(row => row[key] != null) ? sum(rows, key) : null;
    function normalize(source, data, names = new Map()) {
        const historical = source === "HISTORICO";
        const costs = (data.costs || []).map(row => historical ? {
            id: row.modelId, name: row.name, requests: row.requests, input: row.inputTokens,
            output: row.outputTokens, cost: row.estimatedCostUsd, unpriced: row.unpricedRecords || 0
        } : {
            id: row.chave, name: names.get(row.chave) || "Modelo sem nome disponível", requests: row.requisicoes,
            input: row.tokensEntrada, output: row.tokensSaida, cost: row.custoEstimado, unpriced: 0
        });
        const usage = historical ? data : data.usage;
        const series = (usage.series || []).map(row => ({ period: row.period, requests: row.requests,
            input: row.inputTokens, output: row.outputTokens,
            cost: historical ? row.estimatedCostUsd : row.estimatedCost, unpriced: row.unpricedRecords || 0 }));
        return { source, costs, series, active: usage.activeUsers, tokens: sum(costs, "input") + sum(costs, "output"),
            cost: knownSum(costs, "cost"), requests: sum(series, "requests"), unpriced: sum(costs, "unpriced") };
    }
    function reason(label) {
        if (/posição/.test(label)) return "Posição pessoal calculada com os AI Credits do mesmo mês e departamento.";
        if (/Ranking/.test(label)) return "Ranking de equipe ainda indisponível.";
        if (/anônima/.test(label)) return "Referência mensal do departamento, com pelo menos 10 colegas com uso.";
        if (/contexto/.test(label)) return "A comparação econômica coletiva depende de uma fonte de referência."
        if (/Governance|Governança corporativa/.test(label)) return "Governance Score ainda não implementado com todos os componentes.";
        if (/Gastos da empresa/.test(label)) return "O perfil gestor está limitado à área; o total corporativo não é solicitado.";
        if (/ROI/.test(label)) return "Faltam custos de treinamento e metodologia de atribuição do benefício.";
        if (/infraestrutura/.test(label)) return "Não há coleta de CPU e memória integrada.";
        if (/homologad|monitoramento|Segurança|segurança/.test(label)) return "Faltam fontes/classificação e cobertura de monitoramento integradas.";
        if (/capacidade|Capacidade|Limites do cenário/.test(label)) return "Faltam limites contratados e política de alocação individual.";
        if (/projetado|potencial|cenário|recomendada|otimizad/.test(label)) return "O cenário de otimização entre modelos ainda não está implementado; a projeção mensal simples é exibida separadamente.";
        if (/economizado|gerado|líquido|estimativas|eficiência|benefício|Retorno|retorno/.test(label)) return "Depende de premissas aprovadas de produtividade, custo-hora, moeda e cobertura.";
        if (/agentes/.test(label)) return "Não há consolidação por identidade de agente; tentativas do Gateway não equivalem a execuções de agentes.";
        return "Dados exibidos após a consulta autenticada da fonte e do período selecionados.";
    }
    function recentComparison(series, from, to) {
        const end = new Date(`${to}T00:00:00Z`);
        if (!Number.isFinite(end.getTime())) return null;
        const day = 86400000, split = end.getTime() - 6 * day, begin = end.getTime() - 13 * day;
        if (Date.parse(`${from}T00:00:00Z`) > begin) return null;
        const groups = [series.filter(row => Date.parse(row.period) >= begin && Date.parse(row.period) < split),
            series.filter(row => Date.parse(row.period) >= split && Date.parse(row.period) <= end.getTime())];
        return groups.map((rows, i) => ({ name: i ? "Últimos 7 dias" : "7 dias anteriores", cost: knownSum(rows, "cost"),
            requests: sum(rows, "requests") }));
    }
    return { number, money, sum, knownSum, normalize, reason, recentComparison };
});
