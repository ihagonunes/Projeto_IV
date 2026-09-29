/**
 * dashboard.js — painel de uso e custo (SPI-92/93/95).
 *
 * Consome as agregacoes do backend via window.ApiClient:
 *   GET /api/v1/observability/metrics/usage  -> serie + usuarios ativos
 *   GET /api/v1/observability/metrics/costs  -> tokens/custo por departamento
 *
 * O escopo (proprio/departamento/tenant) e o filtro por tenant sao resolvidos
 * no backend a partir do JWT (SPI-94). O frontend apenas exibe o que recebe.
 */
document.addEventListener("DOMContentLoaded", () => {
    const client = window.ApiClient;
    const form = document.getElementById("dashboardFiltros");
    const campoDe = document.getElementById("filtroDe");
    const campoAte = document.getElementById("filtroAte");
    const campoDepartamento = document.getElementById("filtroDepartamento");
    const mensagem = document.getElementById("dashboardMensagem");
    const kpiRequests = document.getElementById("kpiRequests");
    const kpiActiveUsers = document.getElementById("kpiActiveUsers");
    const kpiCusto = document.getElementById("kpiCusto");
    const grafico = document.getElementById("graficoSeries");
    const tabela = document.getElementById("tabelaCustos");

    const nomesPorId = {};
    let dadosAtuais = { usage: null, costs: [] };

    // Periodo padrao: ultimos 30 dias (mesmo default do backend).
    const hoje = new Date();
    const inicio = new Date(hoje.getTime() - 30 * 24 * 60 * 60 * 1000);
    campoDe.value = comoData(inicio);
    campoAte.value = comoData(hoje);

    function comoData(data) {
        return data.toISOString().slice(0, 10);
    }

    function avisar(texto, tipo) {
        if (!mensagem) return;
        mensagem.textContent = texto || "";
        mensagem.className = texto ? `alert alert-${tipo || "info"}` : "d-none";
    }

    function queryString() {
        const params = new URLSearchParams();
        params.set("from", `${campoDe.value}T00:00:00Z`);
        params.set("to", `${campoAte.value}T23:59:59Z`);
        if (campoDepartamento.value) {
            params.set("departmentId", campoDepartamento.value);
        }
        return params.toString();
    }

    function formatarMoeda(valor) {
        return "R$ " + Number(valor || 0).toLocaleString("pt-BR", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 4
        });
    }

    async function carregarDepartamentos() {
        try {
            const resposta = await client.get("/v1/departamentos");
            const lista = Array.isArray(resposta) ? resposta : (resposta?.content || []);
            lista.forEach((dep) => {
                nomesPorId[dep.id] = dep.name;
                const opcao = document.createElement("option");
                opcao.value = dep.id;
                opcao.textContent = dep.name;
                campoDepartamento.appendChild(opcao);
            });
        } catch (e) {
            // Sem DEPARTMENT:READ ou indisponivel: o filtro fica so com "Todos".
        }
    }

    function limpar() {
        kpiRequests.textContent = "—";
        kpiActiveUsers.textContent = "—";
        kpiCusto.textContent = "—";
        grafico.innerHTML = "";
        tabela.innerHTML = "";
    }

    function renderKpis(usage, costs) {
        const total = (usage?.series || []).reduce((soma, p) => soma + Number(p.requests || 0), 0);
        kpiRequests.textContent = total.toLocaleString("pt-BR");
        kpiActiveUsers.textContent = Number(usage?.activeUsers || 0).toLocaleString("pt-BR");
        const custo = (costs || []).reduce((soma, c) => soma + Number(c.estimatedCost || 0), 0);
        kpiCusto.textContent = formatarMoeda(custo);
    }

    function renderSeries(usage) {
        const serie = usage?.series || [];
        if (!serie.length) {
            grafico.innerHTML = '<p class="text-muted mb-0">Sem requisições registradas no período.</p>';
            return;
        }
        const maximo = Math.max(...serie.map((p) => Number(p.requests || 0)), 1);
        grafico.innerHTML = serie.map((ponto) => {
            const valor = Number(ponto.requests || 0);
            const largura = Math.max((valor / maximo) * 100, 1);
            return `
                <div class="d-flex align-items-center gap-2 mb-2">
                    <div class="text-muted small" style="width: 130px;">${ponto.period}</div>
                    <div class="flex-grow-1 bg-body-secondary rounded" style="height: 14px;">
                        <div class="bg-primary rounded" style="height: 14px; width: ${largura}%;"></div>
                    </div>
                    <div class="small fw-semibold" style="width: 60px; text-align: right;">${valor.toLocaleString("pt-BR")}</div>
                </div>`;
        }).join("");
    }

    function renderCustos(costs) {
        if (!costs || !costs.length) {
            tabela.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Sem consumo por departamento no período.</td></tr>';
            return;
        }
        tabela.innerHTML = costs.map((c) => {
            const nome = c.departmentId
                ? (nomesPorId[c.departmentId] || "Departamento")
                : "Sem departamento";
            return `
                <tr>
                    <td>${nome}</td>
                    <td class="text-end">${Number(c.requests || 0).toLocaleString("pt-BR")}</td>
                    <td class="text-end">${Number(c.inputTokens || 0).toLocaleString("pt-BR")} / ${Number(c.outputTokens || 0).toLocaleString("pt-BR")}</td>
                    <td class="text-end">${formatarMoeda(c.estimatedCost)}</td>
                </tr>`;
        }).join("");
    }

    async function carregar() {
        if (!client) {
            avisar("Cliente HTTP não carregado.", "warning");
            return;
        }
        avisar("");
        try {
            const [usage, costs] = await Promise.all([
                client.get(`/v1/observability/metrics/usage?${queryString()}`),
                client.get(`/v1/observability/metrics/costs?${queryString()}`)
            ]);
            dadosAtuais = { usage, costs: costs || [] };
            renderKpis(dadosAtuais.usage, dadosAtuais.costs);
            renderSeries(dadosAtuais.usage);
            renderCustos(dadosAtuais.costs);
        } catch (err) {
            dadosAtuais = { usage: null, costs: [] };
            limpar();
            avisar(err?.message || "Não foi possível carregar as métricas.", "warning");
        }
    }

    if (form) {
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            if (!form.checkValidity()) {
                form.classList.add("was-validated");
                return;
            }
            if (campoDe.value > campoAte.value) {
                avisar("A data inicial não pode ser posterior à final.", "danger");
                return;
            }
            carregar();
        });
    }

    carregarDepartamentos().then(carregar);
});
