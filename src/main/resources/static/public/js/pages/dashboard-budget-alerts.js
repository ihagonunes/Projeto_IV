/* POPROJ-54 / SPI-124: orçamento do mês corrente; o backend aplica tenant e RBAC. */
(function () {
    "use strict";
    const get = name => document.getElementById(`budget-notification-${name}`);
    const panel = document.getElementById("budget-notification");
    if (!panel || !window.Auth.isAuthenticated()) return;
    const message = get("message"), details = get("details"), list = get("items"), retry = get("retry");
    const states = { OK: "OK", ALERTA: "Alerta", EXCEDIDO: "Excedido", SEM_ORCAMENTO: "Sem orçamento" };
    const amount = value => value == null ? "Sem dado" : Number(value).toLocaleString("pt-BR", {
        minimumFractionDigits: 2, maximumFractionDigits: 2
    });
    let version = 0;

    async function load() {
        const current = ++version;
        window.AtlasDashboard?.publish("budgets-status", "Carregando orçamento do mês operacional…");
        const month = new Date().toISOString().slice(0, 7);
        panel.hidden = true;
        panel.className = "demo-panel p-3 mb-4";
        details.hidden = true;
        details.open = false;
        list.replaceChildren();
        message.textContent = "";
        retry.hidden = true;
        try {
            const response = await window.ApiClient.get(`/v1/observability/orcamentos?mes=${month}`);
            if (current !== version) return;
            const items = response.itens || [];
            window.AtlasDashboard?.publish("budgets", response);
            if (!items.length) return;
            const demo = window.AtlasBudgetDemo;
            if (demo && items.every(demo.applies)) {
                panel.hidden = false;
                details.hidden = false;
                details.open = true;
                get("title").textContent = "Orçamento do mês · simulação";
                message.textContent = "Cenário de demonstração. O mês atual está sem orçamento e sem gasto registrado.";
                get("summary").textContent = "Detalhes do cenário";
                get("count").textContent = "Demo";
                get("count").className = "badge text-bg-secondary";
                get("icon").hidden = true;
                const row = document.createElement("li");
                row.textContent = "Orçamento " + demo.money(demo.budget) + " · consumido " + demo.money(demo.spent)
                    + " · disponível " + demo.money(demo.budget - demo.spent);
                list.appendChild(row);
                return;
            }
            const alerts = items.filter(item => ["ALERTA", "EXCEDIDO"].includes(item.status));
            const exceeded = alerts.some(item => item.status === "EXCEDIDO");
            panel.hidden = false;
            details.hidden = false;
            details.open = true;
            get("title").textContent = `Orçamento do mês — ${month}`;
            message.textContent = alerts.length ? "Atenção ao consumo do orçamento mensal." : "Nenhum departamento em alerta.";
            get("summary").textContent = alerts.length
                ? "Atenção: departamentos atingiram o limite de alerta do orçamento mensal. Ver detalhes"
                : "Consultar situação dos departamentos";
            get("count").textContent = `${alerts.length} ${alerts.length === 1 ? "alerta" : "alertas"}`;
            get("count").className = `badge text-bg-${exceeded ? "danger" : alerts.length ? "warning" : "success"}`;
            get("icon").hidden = !alerts.length;
            if (alerts.length) {
                panel.className = `demo-panel p-3 mb-4 border border-${exceeded ? "danger" : "warning"}`;
            }
            // Críticos primeiro, preservando todos os departamentos autorizados para consulta.
            const ordered = [...alerts, ...items.filter(item => !["ALERTA", "EXCEDIDO"].includes(item.status))];
            for (const item of ordered) {
                const row = document.createElement("li");
                const label = document.createElement("span");
                const status = states[item.status] || "Situação indisponível";
                label.className = `badge me-2 text-bg-${item.status === "EXCEDIDO" ? "danger"
                    : item.status === "ALERTA" ? "warning" : item.status === "OK" ? "success" : "secondary"}`;
                label.textContent = status;
                row.appendChild(label);
                const text = document.createElement("span");
                const pct = item.pctUsado == null ? null : Number(item.pctUsado);
                if (["ALERTA", "EXCEDIDO"].includes(item.status)) {
                    text.textContent = pct != null && Number.isFinite(pct)
                        ? `Atenção: O departamento ${item.departmentName} atingiu ${pct.toLocaleString("pt-BR", { maximumFractionDigits: 2 })}% do orçamento mensal alocado.`
                        : `Atenção: O departamento ${item.departmentName} registrou consumo com orçamento mensal igual a zero.`;
                } else {
                    text.textContent = `${item.departmentName}: ${status}.`;
                }
                row.appendChild(text);
                const values = document.createElement("div");
                values.className = "demo-muted small";
                const balance = item.orcamento == null || item.custoReal == null
                    ? null : Number(item.orcamento) - Number(item.custoReal);
                values.textContent = `Orçamento ${amount(item.orcamento)} · Consumo ${amount(item.custoReal)} · Saldo ${amount(balance)}`;
                row.appendChild(values);
                list.appendChild(row);
            }
        } catch (error) {
            if (current !== version) return;
            // SELF e usuários sem permissão não recebem nomes, valores ou alertas gerenciais.
            if (error.status === 401 || error.status === 403) {
                window.AtlasDashboard?.publish("budgets-status", "Sem permissão para consultar orçamentos.");
                return;
            }
            window.AtlasDashboard?.publish("budgets-status", "Não foi possível consultar orçamentos. Tente novamente.");
            panel.hidden = false;
            message.textContent = "Não foi possível consultar os alertas de orçamento. Tente novamente.";
            retry.hidden = false;
        }
    }
    retry.addEventListener("click", load);
    window.addEventListener("focus", load);
    load();
})();
