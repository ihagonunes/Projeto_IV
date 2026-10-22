"use strict";

// Amostra exclusiva de apresentação. Pessoas, departamentos, custos e indicadores são fictícios.
// Nenhuma linha representa o usuário autenticado ou uma concessão de acesso real.
const departments = [
    { id: "produto", name: "Produto", budget: 1250, people: ["Ana", "Bruno", "Carla", "Diego"] },
    { id: "operacoes", name: "Operações", budget: 1100, people: ["Elisa", "Felipe", "Giovana", "Hugo"] },
    { id: "comercial", name: "Comercial", budget: 1350, people: ["Iara", "João", "Lia", "Mateus"] },
    { id: "financas", name: "Finanças", budget: 950, people: ["Nina", "Otávio", "Paula", "Rafael"] }
];
const models = ["Modelo Alfa", "Modelo Beta", "Modelo Gama"];
const round = value => Math.round(value * 100) / 100;
const sum = (rows, key) => round(rows.reduce((total, row) => total + Number(row[key] || 0), 0));
const money = value => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
const integer = value => new Intl.NumberFormat("pt-BR").format(value);
const percent = value => `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(value)}%`;
const change = (current, previous) => previous ? round((current / previous - 1) * 100) : null;
const dateKey = date => date.toISOString().slice(0, 10);
const records = [];
for (let day = new Date("2026-09-01T12:00:00Z"), dayIndex = 0; day <= new Date("2026-10-09T12:00:00Z"); day.setUTCDate(day.getUTCDate() + 1), dayIndex++) {
    const date = dateKey(day);
    departments.forEach((department, departmentIndex) => department.people.forEach((person, personIndex) => {
        // Uma pessoa da equipe de Produto fica sem uso na semana demonstrativa.
        if (departmentIndex === 0 && personIndex === 3 && date >= "2026-10-04") return;
        const requests = 2 + (dayIndex * 3 + departmentIndex * 5 + personIndex * 2) % 9;
        const cost = round(requests * (0.35 + departmentIndex * 0.07 + personIndex * 0.04));
        records.push({ date, department: department.id, person, model: models[(dayIndex + personIndex + departmentIndex) % models.length], requests, cost, tokens: requests * (145 + personIndex * 20) });
    }));
}
const periods = {
    semanal: { start: "2026-10-04", end: "2026-10-10", previousStart: "2026-09-27", previousEnd: "2026-10-02", label: "04 a 10 de outubro de 2026 · dados até 09/10" },
    mensal: { start: "2026-10-01", end: "2026-10-31", previousStart: "2026-09-01", previousEnd: "2026-09-09", label: "Outubro de 2026 · dados até 09/10" }
};
const inRange = (row, start, end) => row.date >= start && row.date <= end;
const group = (rows, field) => [...new Set(rows.map(row => row[field]))].map(label => ({ label, requests: sum(rows.filter(row => row[field] === label), "requests"), cost: sum(rows.filter(row => row[field] === label), "cost"), tokens: sum(rows.filter(row => row[field] === label), "tokens") }));
const kpi = (label, value, hint) => ({ label, value, hint });
const table = (title, subtitle, columns, rows) => ({ title, subtitle, columns, rows });
const chart = (title, subtitle, labels, datasets, kind = "bar") => ({ title, subtitle, labels, datasets, kind });
const series = (rows, field, title, subtitle) => {
    const grouped = group(rows, field);
    return chart(title, subtitle, grouped.map(row => field === "department" ? departments.find(department => department.id === row.label).name : row.label), [{ label: "Custo (R$)", values: grouped.map(row => row.cost) }]);
};
const emptyResult = () => ({ kpis: [], charts: [], tables: [], notes: [], links: [] });

function leader(current, previous) {
    const own = current.filter(row => row.person === "Ana" && row.department === "produto");
    const team = current.filter(row => row.department === "produto");
    const former = previous.filter(row => row.department === "produto");
    const members = departments[0].people.map(person => {
        const rows = team.filter(row => row.person === person);
        const requests = sum(rows, "requests");
        const training = person === "Ana" || person === "Carla";
        return { person, requests, cost: sum(rows, "cost"), training, points: requests * 2 + (training ? 10 : 0) };
    });
    const active = members.filter(member => member.requests > 0).length;
    const modelRows = group(team, "model");
    return {
        kpis: [
            kpi("Meu gasto", money(sum(own, "cost")), "Amostra individual do período"),
            kpi("Gasto da equipe", money(sum(team, "cost")), "Inclui o gasto próprio"),
            kpi("Comparação com período anterior", percent(change(sum(team, "cost"), sum(former, "cost")) || 0), `Anterior: ${money(sum(former, "cost"))}`),
            kpi("Usuários ativos / inativos", `${active} / ${members.length - active}`, `${percent(active / members.length * 100)} de atividade`)
        ],
        charts: [
            series(team, "person", "Distribuição por membro", "Custo demonstrativo no período"),
            series(team, "model", "Modelos utilizados", "Custo por modelo na equipe")
        ],
        tables: [
            table("Ranking de engajamento", "Pontos demonstrativos: 2 por requisição + 10 por capacitação concluída.", ["Posição", "Membro", "Requisições", "Pontos", "Capacitação"], members.slice().sort((a, b) => b.points - a.points).map((member, index) => [`${index + 1}º`, member.person, integer(member.requests), integer(member.points), member.training ? "Concluída" : "Pendente"])),
            table("Uso por modelo", "Soma coerente com o gasto da equipe.", ["Modelo", "Requisições", "Custo"], modelRows.map(row => [row.label, integer(row.requests), money(row.cost)]))
        ],
        notes: [members.length - active ? `${members.length - active} membro sem uso no período demonstrativo: possível gargalo de adoção.` : "Todos os membros tiveram uso no período demonstrativo.", `${members.filter(member => !member.training).length} capacitações pendentes na amostra; sinal ilustrativo para acompanhamento.`],
        links: []
    };
}

function manager(current, previous, budgetPreview) {
    const area = current.filter(row => row.department === "produto");
    const priorArea = previous.filter(row => row.department === "produto");
    const department = departments[0];
    const previewLimit = budgetPreview || department.budget;
    const modelRows = group(area, "model");
    const areaAgents = [
        { name: "Agente de Políticas · Produto", executions: 48, successes: 45, latency: 480 },
        { name: "Projeção orçamentária · Produto", executions: 32, successes: 29, latency: 710 }
    ];
    const trainingCost = 1200, estimatedSavings = 1800;
    return {
        kpis: [
            kpi("Gasto da empresa", money(sum(current, "cost")), "Quatro departamentos da amostra"),
            kpi("Gasto do departamento", money(sum(area, "cost")), `Anterior: ${money(sum(priorArea, "cost"))}`),
            kpi("AI Adoption & Governance Score", "73 / 100", "Score departamental ilustrativo"),
            kpi("ROI de treinamentos", percent((estimatedSavings - trainingCost) / trainingCost * 100), `${money(estimatedSavings)} benefício estimado · ${money(trainingCost)} investimento`)
        ],
        charts: [
            series(area, "model", "Modelos no departamento", "Custo demonstrativo por modelo"),
            chart("Comparação temporal", "Custo do departamento por período", ["Anterior", "Atual"], [{ label: "Custo (R$)", values: [sum(priorArea, "cost"), sum(area, "cost")] }])
        ],
        tables: [
            table("Orçamento departamental", "Prévia de gestão; os controles abaixo não gravam alterações.", ["Departamento", "Limite mensal", "Uso no período", "Saldo demonstrativo"], [[department.name, money(previewLimit), money(sum(area, "cost")), money(previewLimit - sum(area, "cost"))]]),
            table("Desempenho dos agentes da área", "Amostra própria da área; não usa execuções do painel transversal de Agentes.", ["Agente", "Execuções", "Sucesso", "Latência média"], areaAgents.map(agent => [agent.name, integer(agent.executions), percent(agent.successes / agent.executions * 100), `${integer(agent.latency)} ms`])),
            table("Modelos utilizados", "Valores do período selecionado.", ["Modelo", "Requisições", "Custo"], modelRows.map(row => [row.label, integer(row.requests), money(row.cost)]))
        ],
        notes: ["Orçamento: qualquer ajuste nesta página é somente uma prévia e não será salvo.", "Score e ROI usam premissas fictícias; não representam regra aprovada nem retorno financeiro real."],
        links: [], budgetPreview: previewLimit
    };
}

function executive(current, previous) {
    const grouped = departments.map(department => ({ ...department, cost: sum(current.filter(row => row.department === department.id), "cost"), requests: sum(current.filter(row => row.department === department.id), "requests") }));
    const active = new Set(current.map(row => row.person)).size;
    const totalBudget = sum(departments, "budget");
    return {
        kpis: [
            kpi("Custo corporativo", money(sum(current, "cost")), `Anterior: ${money(sum(previous, "cost"))}`),
            kpi("Requisições totais", integer(sum(current, "requests")), "Quatro departamentos da amostra"),
            kpi("AI Adoption & Governance Score", "76 / 100", "Score global ilustrativo"),
            kpi("Taxa geral de adoção", percent(active / 16 * 100), `${active} de 16 pessoas com uso`)
        ],
        charts: [
            series(current, "department", "FinOps corporativo", "Custo por departamento demonstrativo"),
            chart("Comparativo de períodos", "Custo total da organização", ["Anterior", "Atual"], [{ label: "Custo (R$)", values: [sum(previous, "cost"), sum(current, "cost")] }])
        ],
        tables: [
            table("Benchmarking entre departamentos", "Ordenado pelo custo da amostra; não representa desempenho real.", ["Departamento", "Requisições", "Custo", "Orçamento", "Uso do orçamento"], grouped.slice().sort((a, b) => b.cost - a.cost).map(row => [row.name, integer(row.requests), money(row.cost), money(row.budget), percent(row.cost / row.budget * 100)])),
            table("Relatório executivo demonstrativo", "Resumo visível para análise; sem exportação ou publicação nesta etapa.", ["Indicador", "Valor"], [["Custo corporativo", money(sum(current, "cost"))], ["Orçamento total", money(totalBudget)], ["Saldo demonstrativo", money(totalBudget - sum(current, "cost"))], ["Adoção geral", percent(active / 16 * 100)]])
        ],
        notes: ["FinOps, benchmarking e relatório usam somente a massa demonstrativa; exportação não habilitada.", "Score global ilustrativo, sem cálculo oficial ou acesso a dados reais."],
        links: []
    };
}

const attempts = Array.from({ length: 24 }, (_, index) => ({
    id: `DEMO-GW-${String(index + 1).padStart(3, "0")}`,
    date: `2026-10-${String(1 + index % 9).padStart(2, "0")}`,
    model: models[index % models.length],
    latency: 190 + (index * 83) % 720,
    status: index % 11 === 0 ? "BLOCKED" : index % 7 === 0 ? "ERROR" : "COMPLETED",
    tokens: 310 + (index * 71) % 850
}));
const infrastructure = [
    { name: "Gateway de IA · demonstração", cpu: 42, memory: 58, requests: 24 },
    { name: "Fila de processamento · demonstração", cpu: 26, memory: 35, requests: 24 }
];
function governance(period) {
    const rows = attempts.filter(row => inRange(row, period.start, period.end));
    const failures = rows.filter(row => row.status === "ERROR").length;
    const blocked = rows.filter(row => row.status === "BLOCKED").length;
    const average = rows.length ? Math.round(sum(rows, "latency") / rows.length) : 0;
    const statusRows = ["COMPLETED", "ERROR", "BLOCKED"].map(status => ({ status, count: rows.filter(row => row.status === status).length }));
    return {
        kpis: [
            kpi("Tentativas do Gateway", integer(rows.length), "Logs técnicos demonstrativos"),
            kpi("Latência média", `${integer(average)} ms`, "Tentativas no período"),
            kpi("Erros / bloqueios", `${failures} / ${blocked}`, "Estados diferentes na amostra"),
            kpi("Tokens processados", integer(sum(rows, "tokens")), "Consumo ilustrativo de infraestrutura")
        ],
        charts: [
            chart("Status das requisições", "Volume por resultado", statusRows.map(row => row.status), [{ label: "Tentativas", values: statusRows.map(row => row.count) }]),
            chart("Latência por modelo", "Média em milissegundos", models, [{ label: "Latência média (ms)", values: models.map(model => { const sample = rows.filter(row => row.model === model); return sample.length ? Math.round(sum(sample, "latency") / sample.length) : 0; }) }])
        ],
        tables: [
            table("Logs demonstrativos do AI Gateway", "Identificadores fictícios; nenhum log real consultado.", ["ID", "Data", "Modelo", "Status", "Latência", "Tokens"], rows.slice().reverse().map(row => [row.id, row.date, row.model, row.status, `${row.latency} ms`, integer(row.tokens)])),
            table("Consumo de infraestrutura", "Amostra estática de capacidade, sem telemetria real.", ["Serviço", "CPU", "Memória", "Tentativas no período"], infrastructure.map(row => [row.name, percent(row.cpu), percent(row.memory), integer(rows.length)]))
        ],
        notes: [`Segurança: ${blocked} tentativa(s) bloqueada(s) na amostra; isto não é um alerta operacional real.`, "Os atalhos abaixo abrem áreas existentes e não representam autorização administrativa."],
        links: [{ label: "Usuários e concessões", href: "/admin/usuarios" }, { label: "Convites", href: "/admin/convites" }, { label: "Orçamentos", href: "/onboarding/departamentos/orcamentos" }]
    };
}

function getDashboard({ profile, period, state, budgetPreview }) {
    if (state === "erro") {
        const error = new Error("Falha simulada da fonte de dados.");
        error.demo = true;
        throw error;
    }
    const selectedPeriod = periods[period];
    const current = state === "vazio" ? [] : records.filter(row => inRange(row, selectedPeriod.start, selectedPeriod.end));
    const previous = state === "vazio" ? [] : records.filter(row => inRange(row, selectedPeriod.previousStart, selectedPeriod.previousEnd));
    const builders = { lider: leader, gestor: manager, executivo: executive, governanca: () => governance(selectedPeriod) };
    const data = state === "vazio" ? emptyResult() : builders[profile](current, previous, budgetPreview);
    return { meta: { simulated: true, timeZone: "America/Sao_Paulo", referenceDay: "2026-10-09", version: 1 }, profile, title: { lider: "Líder", gestor: "Gestor", executivo: "Executivo", governanca: "Governança/TI" }[profile], period: { key: period, label: selectedPeriod.label }, pending: false, state, agents: [], selectedAgent: "", management: true, data };
}

module.exports = { getDashboard, records, departments, attempts };
