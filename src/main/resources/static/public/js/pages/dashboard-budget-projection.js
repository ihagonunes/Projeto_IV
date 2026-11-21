/* SPI-131: card determinístico de projeção do orçamento no dashboard. */
(function () {
    "use strict";
    const panel = document.getElementById("budget-projection-panel");
    if (!panel || !window.Auth || !window.Auth.isAuthenticated()) return;
    const client = window.ApiClient;
    const department = document.getElementById("budget-projection-department");
    const message = document.getElementById("budget-projection-message");
    const content = document.getElementById("budget-projection-content");
    const retry = document.getElementById("budget-projection-retry");
    const labels = {
        DENTRO_DO_ORCAMENTO: "Dentro do orçamento",
        ATENCAO: "Atenção: projeção acima do limiar",
        ESGOTA_NO_MES: "Esgota no mês",
        JA_EXCEDIDO: "Orçamento já excedido",
        SEM_ORCAMENTO: "Sem orçamento"
    };
    let version = 0, projectionVersion = 0, budgets = [];
    const text = (id, value) => {
        const element = document.getElementById(id);
        if (element) element.textContent = value;
    };
    const money = value => value == null ? "Sem dado"
        : Number(value).toLocaleString("pt-BR", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }) + " (moeda não informada)";
    const number = value => value == null ? "Sem dado"
        : Number(value).toLocaleString("pt-BR", { maximumFractionDigits: 2 });
    const month = () => new Date().toISOString().slice(0, 7);
    function dateLabel(value) {
        if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
        return `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)}`;
    }
    function option(value, label) {
        const item = document.createElement("option");
        item.value = value;
        item.textContent = label;
        department.appendChild(item);
    }
    function preferredDepartment(items) {
        const alert = items.find(item => ["ALERTA", "EXCEDIDO"].includes(item.status));
        return alert ? alert.departmentId : items[0].departmentId;
    }
    function render(data) {
        window.AtlasDashboardVisuals?.projection(data, money);
        text("budget-projection-title", "Projeção do mês");
        text("budget-projection-spent", money(data.gastoAteHoje));
        text("budget-projection-projected", money(data.projetado));
        text("budget-projection-exhaustion", data.diaEsgotamento
            ? `Dia ${dateLabel(data.diaEsgotamento)}` : "Não esgota neste mês");
        text("budget-projection-recommendation",
            labels[data.recomendacao] || "Situação desconhecida");
        text("budget-projection-status", data.pctProjetado == null
            ? "Sem percentual aplicável"
            : `${number(data.pctProjetado)}% do orçamento projetado`);
        const budget = data.orcamento == null ? "sem orçamento definido"
            : `${money(data.orcamento)}${data.orcamentoPadrao ? " (padrão)" : ""}`;
        text("budget-projection-detail",
            `Média diária ${money(data.mediaDiaria)} na janela de ${data.janelaDias} dias; `
            + `${data.diasRestantes} dias restantes; ${budget}.`);
    }
    async function loadProjection() {
        const current = ++projectionVersion;
        const departmentId = department.value;
        content.hidden = true;
        retry.hidden = true;
        if (!departmentId) {
            message.textContent = "Selecione um departamento autorizado.";
            return;
        }
        message.textContent = "Carregando projeção…";
        try {
            const data = await client.get(`/v1/observability/orcamentos/projecao?`
                + `departmentId=${encodeURIComponent(departmentId)}&mes=${month()}`);
            if (current !== projectionVersion) return;
            const demo = window.AtlasBudgetDemo;
            if (demo?.applies(budgets.find(item => item.departmentId === departmentId))
                    && data.gastoAteHoje != null && Number(data.gastoAteHoje) === 0 && data.orcamento == null) {
                text("budget-projection-title", "Projeção do mês · simulação");
                text("budget-projection-spent", demo.money(demo.spent));
                text("budget-projection-projected", demo.money(demo.projected));
                text("budget-projection-exhaustion", "Dia 28 do cenário");
                text("budget-projection-recommendation", "Esgota no mês");
                text("budget-projection-status", number(100 * demo.projected / demo.budget) + "% do orçamento simulado");
                text("budget-projection-detail", "Cenário de 30 dias: média diária de " + demo.money(demo.daily)
                    + " e " + demo.remainingDays + " dias restantes. Orçamento simulado de " + demo.money(demo.budget) + ".");
                window.AtlasDashboardVisuals?.projection({ gastoAteHoje: demo.spent, projetado: demo.projected, orcamento: demo.budget }, demo.money, true);
            } else {
                render(data);
            }
            content.hidden = false;
            message.textContent = "";
        } catch (error) {
            if (current !== projectionVersion) return;
            if (error.status === 401 || error.status === 403) {
                panel.hidden = true;
                return;
            }
            message.textContent = error.status === 404
                ? "Departamento não encontrado."
                : "Não foi possível carregar a projeção. Tente novamente.";
            retry.hidden = false;
        }
    }
    async function load() {
        const current = ++version;
        content.hidden = true;
        retry.hidden = true;
        message.textContent = "Carregando projeção…";
        try {
            const response = await client.get(
                `/v1/observability/orcamentos?mes=${month()}`);
            if (current !== version) return;
            const items = (response && response.itens) || [];
            budgets = items;
            if (!items.length) {
                panel.hidden = true;
                return;
            }
            panel.hidden = false;
            department.replaceChildren();
            const previous = department.value;
            for (const item of items) {
                option(item.departmentId, item.departmentName || "Departamento");
            }
            department.value = items.some(item => item.departmentId === previous)
                ? previous : preferredDepartment(items);
            department.disabled = false;
            await loadProjection();
        } catch (error) {
            if (current !== version) return;
            if (error.status === 401 || error.status === 403) {
                panel.hidden = true;
                return;
            }
            panel.hidden = false;
            message.textContent = "Não foi possível consultar os orçamentos autorizados. Tente novamente.";
            retry.hidden = false;
        }
    }
    department.addEventListener("change", loadProjection);
    retry.addEventListener("click", load);
    load();
})();
