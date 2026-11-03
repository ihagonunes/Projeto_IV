/* SPI-90 / S3-F1: apresentação dos agregados; escopo aplicado pelo backend. */
(function () {
    "use strict";
    const byId = id => document.getElementById(`metrics-${id}`);
    const form = byId("filters");
    if (!form) return;
    const personal = form.dataset && form.dataset.scope === "self";
    const client = window.ApiClient;
    const from = byId("from"), to = byId("to"), model = byId("model"), user = byId("user");
    const message = byId("message"), apply = byId("apply");
    const department = document.getElementById("dashboard-area");
    const models = new Map();
    let users = new Map(), searchVersion = 0, requestVersion = 0, timer, canSearchUsers = false;
    const localDate = date => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
    const today = new Date(), start = new Date(today);
    start.setDate(start.getDate() - 30);
    from.value = localDate(start);
    to.value = localDate(today);
    const number = value => Number(value || 0).toLocaleString("pt-BR");
    const money = value => Number(value || 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL", minimumFractionDigits: 4 });
    function option(parent, value, label) {
        const item = document.createElement("option");
        item.value = value;
        item.textContent = label;
        parent.appendChild(item);
    }
    async function loadDepartments() {
        if (personal || !department) return;
        try {
            const result = await client.get("/v1/departamentos");
            const list = Array.isArray(result) ? result : (result?.content || []);
            for (const item of list) option(department, item.id, item.name);
            department.disabled = false;
        } catch (error) {
            // Sem DEPARTMENT:READ ou indisponível: mantém o filtro desabilitado.
        }
    }
    function clear() {
        for (const id of ["requests", "active", "tokens", "cost"]) byId(id).textContent = "—";
        byId("series").replaceChildren();
        byId("costs").replaceChildren();
    }
    function render(usage, costs) {
        const total = key => costs.reduce((sum, row) => sum + Number(row[key] || 0), 0);
        const requests = usage.series.reduce((sum, row) => sum + Number(row.requests), 0);
        clear();
        if (!requests && !costs.length) {
            message.textContent = "Sem dados no período.";
            return;
        }
        message.textContent = "";
        byId("requests").textContent = number(requests);
        byId("active").textContent = number(usage.activeUsers);
        byId("tokens").textContent = `${number(total("tokensEntrada"))} / ${number(total("tokensSaida"))}`;
        byId("cost").textContent = money(total("custoEstimado"));
        const max = Math.max(1, ...usage.series.map(row => Number(row.requests)));
        for (const row of usage.series) {
            const line = document.createElement("div"), meter = document.createElement("meter");
            line.textContent = `${row.period}: ${number(row.requests)} `;
            meter.min = 0; meter.max = max; meter.value = Number(row.requests);
            meter.setAttribute("aria-label", `${row.period}: ${number(row.requests)} requisições`);
            line.appendChild(meter);
            byId("series").appendChild(line);
        }
        for (const row of costs) {
            const tr = document.createElement("tr");
            for (const value of [models.get(row.chave) || "Modelo fora do catálogo disponível", number(row.requisicoes), `${number(row.tokensEntrada)} / ${number(row.tokensSaida)}`, money(row.custoEstimado)]) {
                const td = document.createElement("td"); td.textContent = value; tr.appendChild(td);
            }
            byId("costs").appendChild(tr);
        }
    }
    async function load() {
        const version = ++requestVersion;
        clear();
        if (!form.checkValidity() || from.value > to.value) {
            message.textContent = "Informe um período válido: a data inicial não pode ser posterior à final.";
            return;
        }
        if (canSearchUsers && user.value && !users.has(user.value)) {
            message.textContent = "Selecione um usuário da lista de sugestões.";
            return;
        }
        if (model.value.startsWith("historical:") || (department && department.value.startsWith("historical:"))) {
            message.textContent = "Este recorte histórico não possui vínculo com os dados operacionais.";
            return;
        }
        const params = new URLSearchParams({ from: from.value, to: to.value });
        if (model.value) params.set("modelId", model.value);
        if (department && department.value) params.set("departmentId", department.value);
        if (canSearchUsers && user.value) params.set("userId", users.get(user.value));
        message.textContent = "Carregando métricas…";
        try {
            const [usage, costs] = await Promise.all([
                client.get(`/v1/observability/metrics/${personal ? "me/" : ""}usage?${params}`),
                client.get(`/v1/observability/metrics/${personal ? "me/" : ""}costs?${params}&groupBy=MODEL`)
            ]);
            if (version === requestVersion) render(usage, costs);
        } catch (error) {
            if (version !== requestVersion) return;
            clear();
            message.textContent = error.status === 403 ? "Sem permissão para consultar este recorte." : "Não foi possível carregar as métricas. Tente novamente.";
        }
    }
    user.addEventListener("input", () => {
        clearTimeout(timer);
        const version = ++searchVersion;
        if (users.has(user.value)) { if (user.dataset) user.dataset.userId = users.get(user.value); return; }
        if (user.dataset) delete user.dataset.userId;
        users = new Map();
        byId("users").replaceChildren();
        if (!canSearchUsers || user.value.trim().length < 2) return;
        const term = user.value.trim();
        timer = setTimeout(async () => {
            try {
                const result = await client.get(`/v1/usuarios?name=${encodeURIComponent(term)}&size=20`);
                if (version !== searchVersion) return;
                for (const item of result.content) {
                    const label = `${item.name} (${item.email})`;
                    users.set(label, item.id);
                    option(byId("users"), label, label);
                }
            } catch (error) {
                if (version === searchVersion) message.textContent = "Não foi possível buscar usuários autorizados.";
            }
        }, 250);
    });
    form.addEventListener("submit", event => { event.preventDefault(); load(); });
    model.addEventListener("change", load);
    if (department) department.addEventListener("change", load);
    user.addEventListener("change", () => {
        if (user.dataset) { if (users.has(user.value)) user.dataset.userId = users.get(user.value); else delete user.dataset.userId; }
        if (!user.value || users.has(user.value)) load();
    });
    async function init() {
        if (!window.Auth.requireAuth()) return;
        try {
            const access = await client.get("/v1/usuarios/me/permissoes");
            canSearchUsers = !personal && access.permissoes.includes("USAGE:READ") && access.permissoes.includes("USER:READ")
                && access.concessoes.some(grant => ["MANAGER", "EXECUTIVE"].includes(grant.papel));
            byId("user-field").hidden = !canSearchUsers;
            await loadDepartments();
            const catalog = await client.get("/v1/ai-gateway/models/dashboard");
            for (const item of catalog) {
                models.set(item.id, item.name);
                option(model, item.id, item.name);
            }
            model.disabled = false;
            apply.disabled = false;
            await load();
        } catch (error) {
            clear();
            message.textContent = "Não foi possível carregar permissões e catálogo. Recarregue a página para tentar novamente.";
        }
    }
    init();
})();
