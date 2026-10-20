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
    const campoMesOrcamento = document.getElementById("orcamentoMes");
    const tabelaOrcamentos = document.getElementById("tabelaOrcamentos");
    const mensagemOrcamento = document.getElementById("orcamentoMensagem");
    const campoAgrupamento = document.getElementById("filtroAgrupamento");
    const campoModelo = document.getElementById("filtroModelo");
    const campoUsuario = document.getElementById("filtroUsuario");
    const colunaUsuario = document.getElementById("filtroUsuarioCol");
    const tituloCustos = document.getElementById("tituloCustos");
    const thCustoDimensao = document.getElementById("thCustoDimensao");

    const nomesPorId = {};
    const nomesModelo = {};
    const nomesUsuario = {};
    let dadosAtuais = { usage: null, costs: [] };

    const DIMENSOES = {
        DEPARTMENT: {
            titulo: "Tokens e custo por departamento",
            coluna: "Departamento",
            vazio: "Sem consumo por departamento no período.",
            nome: (id) => nomesPorId[id] || "Sem departamento"
        },
        MODEL: {
            titulo: "Tokens e custo por modelo",
            coluna: "Modelo",
            vazio: "Sem consumo por modelo no período.",
            nome: (id) => nomesModelo[id] || id || "Sem modelo"
        },
        USER: {
            titulo: "Tokens e custo por usuário",
            coluna: "Usuário",
            vazio: "Sem consumo por usuário no período.",
            nome: (id) => nomesUsuario[id] || id || "Sem usuário"
        }
    };

    const STATUS_ORCAMENTO = {
        OK: { texto: "OK", classe: "text-bg-success" },
        ALERTA: { texto: "Alerta", classe: "text-bg-warning" },
        EXCEDIDO: { texto: "Excedido", classe: "text-bg-danger" },
        SEM_ORCAMENTO: { texto: "Sem orçamento", classe: "text-bg-secondary" }
    };

    // Periodo padrao: ultimos 30 dias (mesmo default do backend).
    const hoje = new Date();
    const inicio = new Date(hoje.getTime() - 30 * 24 * 60 * 60 * 1000);
    campoDe.value = comoData(inicio);
    campoAte.value = comoData(hoje);
    if (campoMesOrcamento) {
        campoMesOrcamento.value = comoMes(hoje);
    }

    function comoData(data) {
        return data.toISOString().slice(0, 10);
    }

    function comoMes(data) {
        return data.toISOString().slice(0, 7);
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
        if (campoModelo?.value) {
            params.set("modelId", campoModelo.value);
        }
        if (campoUsuario?.value) {
            params.set("userId", campoUsuario.value);
        }
        return params.toString();
    }

    function dimensaoAtual() {
        return DIMENSOES[campoAgrupamento?.value] || DIMENSOES.DEPARTMENT;
    }

    function atualizarCabecalhoCustos() {
        const dimensao = dimensaoAtual();
        if (tituloCustos) tituloCustos.textContent = dimensao.titulo;
        if (thCustoDimensao) thCustoDimensao.textContent = dimensao.coluna;
    }

    function adicionarOpcao(select, valor, texto) {
        if (!select) return;
        const opcao = document.createElement("option");
        opcao.value = valor;
        opcao.textContent = texto;
        select.appendChild(opcao);
    }

    async function carregarModelos() {
        if (!client) return;
        try {
            const resposta = await client.get("/v1/ai-gateway/models");
            const lista = Array.isArray(resposta) ? resposta : (resposta?.content || []);
            lista.forEach((item) => {
                nomesModelo[item.id] = item.name;
                adicionarOpcao(campoModelo, item.id, item.name);
            });
        } catch (e) {
            // MODEL:READ indisponível: o seletor fica apenas com "Todos".
        }
    }

    async function carregarUsuarios() {
        if (!client) return;
        try {
            const resposta = await client.get("/v1/usuarios?size=100");
            const lista = Array.isArray(resposta) ? resposta : (resposta?.content || []);
            lista.forEach((item) => {
                nomesUsuario[item.id] = item.name || item.email;
                adicionarOpcao(campoUsuario, item.id, item.name || item.email);
            });
        } catch (e) {
            // USER:READ indisponível: o seletor fica apenas com "Todos".
        }
    }

    /**
     * Visibilidade é decisão do backend; /me/permissoes serve só para a
     * experiência do front (ADR-36 item 9). SELF não expõe o filtro de usuário.
     */
    async function carregarVisao() {
        if (!client || !colunaUsuario) return;
        try {
            const acesso = await client.get("/v1/usuarios/me/permissoes");
            const escopos = (acesso?.concessoes || []).map((concessao) => concessao.escopo);
            const visao = escopos.includes("TENANT")
                ? "TENANT"
                : (escopos.includes("DEPARTMENT") ? "DEPARTMENT" : "SELF");
            if (visao === "SELF") {
                colunaUsuario.classList.add("d-none");
                return;
            }
            await carregarUsuarios();
        } catch (e) {
            colunaUsuario.classList.add("d-none");
        }
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
        const dimensao = dimensaoAtual();
        if (!costs || !costs.length) {
            tabela.innerHTML = `<tr><td colspan="4" class="text-center text-muted py-3">${dimensao.vazio}</td></tr>`;
            return;
        }
        tabela.innerHTML = costs.map((c) => {
            const nome = dimensao.nome(c.chave);
            return `
                <tr>
                    <td>${escapar(nome)}</td>
                    <td class="text-end">${Number(c.requisicoes || 0).toLocaleString("pt-BR")}</td>
                    <td class="text-end">${Number(c.tokensEntrada || 0).toLocaleString("pt-BR")} / ${Number(c.tokensSaida || 0).toLocaleString("pt-BR")}</td>
                    <td class="text-end">${formatarMoeda(c.custoEstimado)}</td>
                </tr>`;
        }).join("");
    }

    function escapar(valor) {
        return String(valor ?? "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function avisarOrcamento(texto, tipo) {
        if (!mensagemOrcamento) return;
        mensagemOrcamento.textContent = texto || "";
        mensagemOrcamento.className = texto ? `alert alert-${tipo || "info"} mx-3 mt-3 mb-0` : "d-none";
    }

    function formatarMoedaOrcamento(valor) {
        return "R$ " + Number(valor).toLocaleString("pt-BR", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function formatarPercentual(valor) {
        if (valor == null) return "—";
        return Number(valor).toLocaleString("pt-BR", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }) + "%";
    }

    function renderOrcamentos(itens) {
        if (!tabelaOrcamentos) return;
        if (!itens || !itens.length) {
            tabelaOrcamentos.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">'
                + 'Nenhum departamento ativo no seu escopo para este mês.</td></tr>';
            return;
        }
        tabelaOrcamentos.innerHTML = itens.map((item) => {
            const status = STATUS_ORCAMENTO[item.status]
                || { texto: item.status, classe: "text-bg-secondary" };
            const orcamento = item.orcamento == null
                ? "Sem orçamento"
                : formatarMoedaOrcamento(item.orcamento)
                    + (item.orcamentoPadrao ? ' <span class="text-muted small">(padrão)</span>' : "");
            return `
                <tr>
                    <td>${escapar(item.departmentName)}</td>
                    <td class="text-end">${orcamento}</td>
                    <td class="text-end">${formatarMoedaOrcamento(item.custoReal || 0)}</td>
                    <td class="text-end">${formatarPercentual(item.pctUsado)}</td>
                    <td><span class="badge ${status.classe}">${escapar(status.texto)}</span></td>
                </tr>`;
        }).join("");
    }

    async function carregarOrcamentos() {
        if (!client || !tabelaOrcamentos || !campoMesOrcamento) return;
        avisarOrcamento("");
        try {
            const resposta = await client.get(
                `/v1/observability/orcamentos?mes=${encodeURIComponent(campoMesOrcamento.value)}`);
            renderOrcamentos(resposta?.itens || []);
        } catch (err) {
            tabelaOrcamentos.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">'
                + 'Não foi possível carregar o orçamento.</td></tr>';
            if (err?.status === 403) {
                avisarOrcamento("Você não tem permissão para visualizar o orçamento neste escopo.", "warning");
            } else if (err?.status >= 500) {
                avisarOrcamento("O serviço de orçamento está indisponível. Tente novamente em instantes.", "danger");
            } else {
                avisarOrcamento(err?.message || "Não foi possível carregar o orçamento.", "warning");
            }
        }
    }

    async function carregar() {
        if (!client) {
            avisar("Cliente HTTP não carregado.", "warning");
            return;
        }
        avisar("");
        try {
            const filtros = queryString();
            const agrupamento = encodeURIComponent(campoAgrupamento?.value || "DEPARTMENT");
            const [usage, costs] = await Promise.all([
                client.get(`/v1/observability/metrics/usage?${filtros}`),
                client.get(`/v1/observability/metrics/costs?${filtros}&groupBy=${agrupamento}`)
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

    if (campoMesOrcamento) {
        campoMesOrcamento.addEventListener("change", () => {
            if (campoMesOrcamento.value) carregarOrcamentos();
        });
    }

    if (campoAgrupamento) {
        campoAgrupamento.addEventListener("change", () => {
            atualizarCabecalhoCustos();
            carregar();
        });
    }

    (async () => {
        atualizarCabecalhoCustos();
        await carregarVisao();
        await Promise.all([carregarDepartamentos(), carregarModelos()]);
        await carregar();
        await carregarOrcamentos();
    })();
});
