/* POPROJ-54 / SPI-124: orçamento do mês corrente; o backend aplica tenant e RBAC. */
(function () {
    "use strict";
    const get = name => document.getElementById(`budget-notification-${name}`);
    const panel = document.getElementById("budget-notification");
    if (!panel || !window.Auth.isAuthenticated()) return;
    const message = get("message"), details = get("details"), list = get("items"), retry = get("retry");
    const states = { OK: "Dentro do limite", ALERTA: "Atenção", EXCEDIDO: "Excedido", SEM_ORCAMENTO: "Sem orçamento" };
    const amount = value => value == null || value === "" || !Number.isFinite(Number(value)) ? "—" : Number(value).toLocaleString("pt-BR", {
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
        for (const name of ["total", "alerts", "missing"]) get(name).textContent = "—";
        message.textContent = "";
        get("period").textContent = "";
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
                const row = document.createElement("tr");
                const cell = document.createElement("td"); cell.colSpan = 6;
                cell.textContent = "Orçamento " + demo.money(demo.budget) + " · consumido " + demo.money(demo.spent)
                    + " · disponível " + demo.money(demo.budget - demo.spent);
                row.appendChild(cell);
                list.appendChild(row);
                return;
            }
            const alerts = items.filter(item => ["ALERTA", "EXCEDIDO"].includes(item.status));
            const missing = items.filter(item => item.status === "SEM_ORCAMENTO").length;
            const exceeded = alerts.some(item => item.status === "EXCEDIDO");
            panel.hidden = false;
            details.hidden = false;
            details.open = true;
            get("title").textContent = "Orçamento por departamento";
            get("period").textContent = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric", timeZone: "UTC" }).format(new Date(`${month}-01T12:00:00Z`));
            get("total").textContent = String(items.length);
            get("alerts").textContent = String(alerts.length);
            get("missing").textContent = String(missing);
            message.textContent = alerts.length ? "Há departamentos que precisam de atenção ao consumo."
                : missing ? "Há departamentos sem orçamento informado. Confira a situação abaixo."
                : "Nenhum departamento em alerta nos dados informados.";
            get("summary").textContent = alerts.length
                ? "Atenção: departamentos atingiram o limite de alerta do orçamento mensal. Ver detalhes"
                : "Situação dos departamentos";
            get("count").textContent = `${alerts.length} ${alerts.length === 1 ? "alerta" : "alertas"}`;
            get("count").className = `badge text-bg-${exceeded ? "danger" : alerts.length || missing ? "warning" : "success"}`;
            get("icon").hidden = !alerts.length;
            if (alerts.length) {
                panel.className = `demo-panel p-3 mb-4 border border-${exceeded ? "danger" : "warning"}`;
            }
            // Críticos primeiro, preservando todos os departamentos autorizados para consulta.
            const ordered = [...alerts, ...items.filter(item => !["ALERTA", "EXCEDIDO"].includes(item.status))];
            for (const item of ordered) {
                const row = document.createElement("tr");
                row.className = `budget-department-row budget-state-${item.status || "UNKNOWN"}`;
                const department = document.createElement("th");
                department.setAttribute("scope", "row");
                department.textContent = item.departmentName || "Departamento sem nome";
                row.appendChild(department);
                const label = document.createElement("span");
                const status = states[item.status] || "Situação indisponível";
                label.className = `budget-state-badge text-bg-${item.status === "EXCEDIDO" ? "danger"
                    : item.status === "ALERTA" ? "warning" : item.status === "OK" ? "success" : "secondary"}`;
                label.textContent = status;
                const statusCell = document.createElement("td");
                statusCell.appendChild(label); row.appendChild(statusCell);
                const balance = item.orcamento == null || item.custoReal == null
                    ? null : Number(item.orcamento) - Number(item.custoReal);
                for (const value of [item.orcamento, item.custoReal, balance]) {
                    const cell = document.createElement("td");
                    cell.className = "budget-number"; cell.textContent = amount(value); row.appendChild(cell);
                }
                const usage = document.createElement("td"); usage.className = "budget-usage";
                const pct = item.status === "SEM_ORCAMENTO" || item.pctUsado == null ? null : Number(item.pctUsado);
                if (pct != null && Number.isFinite(pct) && pct >= 0) {
                    const value = document.createElement("span");
                    value.textContent = `${pct.toLocaleString("pt-BR", { maximumFractionDigits: 2 })}%`;
                    const meter = document.createElement("meter");
                    meter.min = 0; meter.max = 100; meter.value = Math.min(100, pct);
                    meter.setAttribute("aria-label", `Utilização do orçamento de ${item.departmentName}`);
                    usage.appendChild(value); usage.appendChild(meter);
                } else { usage.textContent = "—"; }
                row.appendChild(usage);
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
