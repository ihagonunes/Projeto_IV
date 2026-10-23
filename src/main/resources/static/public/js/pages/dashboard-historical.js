/* FEAT-DASH-001: fonte histórica independente; autorização no backend. */
(function () {
    "use strict";
    const el = id => document.getElementById(`historical-${id}`);
    const form = document.getElementById("metrics-filters");
    if (!form) return;
    const personal = form.dataset && form.dataset.scope === "self";
    const from = document.getElementById("metrics-from"), to = document.getElementById("metrics-to");
    const model = document.getElementById("metrics-model"), user = document.getElementById("metrics-user");
    const department = document.getElementById("dashboard-area");
    let version = 0, initialized = false;
    const number = value => Number(value).toLocaleString("pt-BR");
    const money = value => value == null ? "Indisponível" : Number(value).toLocaleString("pt-BR", { style: "currency", currency: "USD", minimumFractionDigits: 4 });
    function clear() {
        for (const id of ["requests", "active", "tokens", "cost"]) el(id).textContent = "—";
        el("series").replaceChildren(); el("costs").replaceChildren();
    }
    function options(parent, values) {
        for (const value of values) {
            const option = document.createElement("option");
            option.value = `historical:${value.id}`; option.textContent = `${value.name} (histórico)`;
            parent.appendChild(option);
        }
    }
    async function load() {
        const current = ++version;
        clear(); el("retry").hidden = true;
        if ((!personal || initialized) && (!form.checkValidity() || from.value > to.value)) {
            el("message").textContent = "Informe um período válido."; return;
        }
        if ((model.value && !model.value.startsWith("historical:"))
                || (department && department.value && !department.value.startsWith("historical:"))) {
            el("message").textContent = "O filtro selecionado não possui vínculo com as identidades do histórico.";
            return;
        }
        const params = new URLSearchParams();
        if (!personal || initialized) { params.set("from", from.value); params.set("to", to.value); }
        if (department && department.value) params.set("departmentId", department.value.slice("historical:".length));
        if (model.value) params.set("modelId", model.value.slice("historical:".length));
        if (!personal && user.value) {
            if (!user.dataset || !user.dataset.userId) { el("message").textContent = "Selecione um usuário da lista de sugestões."; return; }
            params.set("userId", user.dataset.userId);
        }
        el("message").textContent = "Carregando histórico…";
        try {
            const data = await window.ApiClient.get(`/v1/observability/metrics/historical${personal ? "/me" : ""}?${params}`);
            if (current !== version) return;
            if (!initialized) {
                if (personal) { from.value = data.from; to.value = data.to; }
                options(model, data.models);
                model.disabled = false;
                if (department) { options(department, data.departments); department.disabled = false; }
                document.getElementById("metrics-apply").disabled = false;
                initialized = true;
            }
            el("content").hidden = false;
            el("coverage").textContent = data.coverage.from ? `Cobertura da fonte: ${data.coverage.from} a ${data.coverage.to}. Período selecionado: ${from.value} a ${to.value}.` : "Nenhum histórico importado para esta empresa.";
            const total = key => data.costs.reduce((sum, row) => sum + Number(row[key] || 0), 0);
            const requests = data.series.reduce((sum, row) => sum + Number(row.requests), 0);
            const unpriced = total("unpricedRecords");
            el("message").textContent = unpriced ? `${number(unpriced)} registros sem preço ou tokens completos. Custo parcial.` : requests ? "" : "Sem dados históricos no período e filtros selecionados.";
            el("requests").textContent = number(requests);
            el("active").textContent = number(data.activeUsers);
            el("tokens").textContent = `${number(total("inputTokens"))} / ${number(total("outputTokens"))}`;
            el("cost").textContent = money(data.costs.some(row => row.estimatedCostUsd != null) ? total("estimatedCostUsd") : null);
            const max = Math.max(1, ...data.series.map(row => Number(row.requests)));
            for (const row of data.series) {
                const line = document.createElement("div"), meter = document.createElement("meter");
                line.textContent = `${row.period}: ${number(row.requests)} `;
                meter.min = 0; meter.max = max; meter.value = Number(row.requests);
                meter.setAttribute("aria-label", `${row.period}: ${number(row.requests)} requisições`);
                line.appendChild(meter); el("series").appendChild(line);
            }
            for (const row of data.costs) {
                const tr = document.createElement("tr");
                for (const value of [row.name, number(row.requests), `${number(row.inputTokens)} / ${number(row.outputTokens)}`, money(row.estimatedCostUsd) + (row.unpricedRecords ? " (parcial)" : "")]) {
                    const td = document.createElement("td"); td.textContent = value; tr.appendChild(td);
                }
                el("costs").appendChild(tr);
            }
        } catch (error) {
            if (current !== version) return;
            clear();
            if (error.status === 403) {
                el("content").hidden = true;
                el("message").textContent = personal ? "Sua conta não possui um vínculo histórico autorizado." : "Sem permissão para consultar este recorte histórico.";
            } else {
                el("message").textContent = "Não foi possível carregar o histórico.";
                el("retry").hidden = false;
            }
        }
    }
    form.addEventListener("submit", event => { event.preventDefault(); load(); });
    model.addEventListener("change", load);
    if (department) department.addEventListener("change", load);
    user.addEventListener("change", load);
    el("retry").addEventListener("click", load);
    if (window.Auth.requireAuth()) load();
})();
