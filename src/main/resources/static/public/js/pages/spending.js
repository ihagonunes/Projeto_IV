/* LOCAL-BUDGET-001: o backend valida permissões, saldo e distribuição. */
(function () {
    "use strict";
    const $ = id => document.getElementById(id);
    const page = $("spending-page");
    const api = window.API;
    const money = value => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
    const roleNames = { COLLABORATOR: "Colaborador", LEADER: "Líder", MANAGER: "Gestor", EXECUTIVE: "Executivo" };
    let overview;
    let models = [];
    const departmentInputs = new Map(), weightInputs = new Map(), rateInputs = new Map();
    function el(tag, text) { const element = document.createElement(tag); if (text != null) element.textContent = text; return element; }
    function input(type, value, min, step) {
        const element = el("input"); element.type = type; element.value = value ?? "";
        if (min != null) element.min = min;
        if (step != null) element.step = step;
        return element;
    }
    function row(body, values) {
        const tr = el("tr");
        for (const value of values) { const td = el("td"); td.append(value instanceof Node ? value : document.createTextNode(String(value))); tr.append(td); }
        body.append(tr);
    }
    async function action(work) {
        page.querySelectorAll("button").forEach(button => { button.disabled = true; });
        try { await work(); } catch (error) { $("spending-message").textContent = error.message; }
        finally { page.querySelectorAll("button").forEach(button => { button.disabled = false; }); }
    }
    function draft() {
        return {
            amount: Number($("spending-amount").value),
            departments: [...departmentInputs].map(([departmentId, field]) => ({ departmentId, percentage: Number(field.value) })),
            allocations: [],
            weights: Object.fromEntries([...weightInputs].map(([role, field]) => [role, Number(field.value)])),
            rates: [...rateInputs].filter(([, fields]) => fields.reference.value.trim()).map(([modelId, fields]) => ({ modelId, currency: fields.currency.value.toUpperCase(), exchangeRate: Number(fields.rate.value), reference: fields.reference.value.trim() }))
        };
    }
    function updateShares() {
        let total = 0;
        const cents = Math.round(Number($("spending-amount").value) * 100);
        for (const field of departmentInputs.values()) {
            const percentage = Number(field.value);
            total += percentage;
            const units = Math.round(percentage * 10000);
            const cell = field.closest("tr").lastElementChild;
            if (!Number.isSafeInteger(cents) || !Number.isSafeInteger(units)) { cell.textContent = "—"; continue; }
            cell.textContent = money(Number(BigInt(cents) * BigInt(units) / 1000000n) / 100);
        }
        $("spending-percentage-total").textContent = `Distribuído ${total}% de 100%.`;
    }
    async function loadBudget() {
        ++previewVersion;
        overview = await api.get("/orcamento-ia");
        models = await api.get("/ai-gateway/models/spending");
        const plan = overview.plan;
        $("spending-period").textContent = `Mês ${overview.month} · ${overview.settings.timezone}`;
        $("spending-amount").value = plan?.amount ?? 0;
        $("spending-timezone").value = overview.settings.timezone;
        $("spending-departments").replaceChildren(); departmentInputs.clear();
        for (const department of overview.departments) {
            const field = input("number", plan?.departments.find(d => d.departmentId === department.id)?.percentage ?? 0, 0, "0.0001");
            field.max = 100; field.required = true; field.setAttribute("aria-label", `Percentual de ${department.name}`);
            field.addEventListener("input", updateShares); departmentInputs.set(department.id, field);
            row($("spending-departments"), [department.name, field, ""]);
        }
        $("spending-weights").replaceChildren(); weightInputs.clear();
        for (const [role, label] of Object.entries(roleNames)) {
            const field = input("number", plan?.weights[role] ?? 1, "0.0001", "0.0001"); field.required = true;
            const labelNode = el("label", label + " "); labelNode.append(field); $("spending-weights").append(labelNode); weightInputs.set(role, field);
        }
        renderLimits(plan);
        $("spending-models").replaceChildren(); rateInputs.clear();
        for (const model of models) {
            const saved = plan?.rates.find(r => r.modelId === model.id);
            const currency = input("text", saved?.currency ?? "", null, null); currency.maxLength = 3; currency.setAttribute("aria-label", `Moeda de ${model.name}`);
            const rate = input("number", saved?.exchangeRate ?? "", "0.00000001", "0.00000001"); rate.setAttribute("aria-label", `Taxa de ${model.name}`);
            const reference = input("text", saved?.reference ?? "", null, null); reference.maxLength = 200; reference.setAttribute("aria-label", `Referência de ${model.name}`);
            const label = el("div"); label.append(el("strong", model.name), el("p", `Preço por 1.000 tokens · entrada ${model.inputPricePer1k ?? "pendente"} · saída ${model.outputPricePer1k ?? "pendente"}`));
            rateInputs.set(model.id, { currency, rate, reference }); row($("spending-models"), [label, currency, rate, reference]);
        }
        $("spending-pending").replaceChildren();
        for (const reservation of overview.pending) {
            const form = el("form");
            form.append(el("p", `${reservation.requestId} · ${reservation.month} · reserva ${money(reservation.reserved)} · moeda ${reservation.currency} · ${reservation.createdAt}`));
            const cost = input("number", "", 0, "0.00000001"); cost.required = true; cost.setAttribute("aria-label", "Custo confirmado na moeda original");
            const reference = input("text", "", null, null); reference.required = true; reference.maxLength = 200; reference.setAttribute("aria-label", "Referência da conciliação");
            const submit = el("button", "Conciliar reserva"); form.append(cost, reference, submit);
            form.addEventListener("submit", event => { event.preventDefault(); void action(async () => {
                await api.put(`/orcamento-ia/reservas/${reservation.requestId}`, { cost: Number(cost.value), reference: reference.value });
                await loadBudget(); $("spending-message").textContent = "Reserva conciliada.";
            }); });
            $("spending-pending").append(form);
        }
        if (!overview.pending.length) $("spending-pending").append(el("p", "Nenhuma reserva pendente."));
        updateShares();
    }
    $("spending-amount").addEventListener("input", updateShares);
    let previewVersion = 0, previewTimer;
    function renderLimits(plan) {
        $("spending-employees").replaceChildren();
        const groups = new Map();
        for (const allocation of plan?.allocations || []) {
            const user = overview.employees.find(u => u.userId.value === allocation.userId);
            if (!user) continue;
            const key = allocation.departmentId + ":" + user.hierarchyLevel;
            const group = groups.get(key) || { departmentId: allocation.departmentId, role: user.hierarchyLevel, count: 0, limit: allocation.limit };
            group.count++; groups.set(key, group);
        }
        for (const group of groups.values()) row($("spending-employees"), [
            overview.departments.find(d => d.id === group.departmentId)?.name ?? "Departamento",
            roleNames[group.role] ?? group.role, group.count, money(group.limit), money(group.limit * group.count)
        ]);
    }
    async function preview(version) {
        if (!overview || !$("spending-plan-form").checkValidity()) return;
        try {
            const proposal = draft(); proposal.rates = [];
            const result = await api.post("/orcamento-ia/sugestao", proposal);
            if (version === previewVersion) renderLimits(result);
        } catch (error) { if (version === previewVersion) $("spending-message").textContent = error.message; }
    }
    function schedulePreview() {
        const version = ++previewVersion;
        clearTimeout(previewTimer);
        previewTimer = setTimeout(() => void preview(version), 300);
    }
    $("spending-suggest").addEventListener("click", () => void preview(++previewVersion));
    $("spending-plan-form").addEventListener("input", schedulePreview);
    $("spending-plan-form").addEventListener("submit", event => { event.preventDefault(); void action(async () => {
        await api.put(`/orcamento-ia/${overview.month}`, draft()); await loadBudget();
        $("spending-message").textContent = "Distribuição confirmada.";
    }); });
    $("spending-save-timezone").addEventListener("click", () => void action(async () => {
        await api.put("/orcamento-ia/fuso", { timezone: $("spending-timezone").value }); await loadBudget();
        $("spending-message").textContent = "Fuso atualizado.";
    }));
    $("spending-alert-form").addEventListener("submit", event => { event.preventDefault(); void action(async () => {
        await api.put("/orcamento-ia/alerta", { percentage: Number($("spending-threshold").value) });
        $("spending-message").textContent = "Percentual de alerta atualizado para toda a empresa.";
    }); });
    const initialize = () => action(async () => {
        if (page.dataset.executive === "true") { $("spending-budget").hidden = false; await loadBudget(); }
        if (page.dataset.manager === "true") { $("spending-alert").hidden = false; const settings = await api.get("/orcamento-ia/alerta"); $("spending-threshold").value = settings.alertPct; }
        if (page.dataset.executive !== "true" && page.dataset.manager !== "true") $("spending-message").textContent = "A distribuição é definida pelo executivo. O percentual de alerta é definido pelo gestor.";
    });
    const modal = page.closest("dialog");
    if (modal) {
        document.querySelectorAll("[data-spending-open]").forEach(button => button.addEventListener("click", () => { modal.showModal(); void initialize(); }));
        document.querySelector("[data-spending-close]").addEventListener("click", () => modal.close());
    } else void initialize();
})();
