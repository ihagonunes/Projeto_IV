/* SPI-144: cards de adoção, treinamento e AI Credits no dashboard. */
(function () {
    "use strict";
    const core = window.AtlasEngagement;
    const form = document.getElementById("metrics-filters");
    const panel = document.getElementById("engagement-panel");
    if (!form || !panel || !core) return;
    const client = window.ApiClient;
    const message = document.getElementById("engagement-message");
    const content = document.getElementById("engagement-content");
    const source = document.getElementById("engagement-source");
    const department = document.getElementById("dashboard-area");
    const to = document.getElementById("metrics-to");
    const personal = panel.dataset && panel.dataset.scope === "self";
    let version = 0;
    const text = (id, value) => {
        const element = document.getElementById(id);
        if (element) element.textContent = value;
    };
    const show = (id, visible) => {
        const element = document.getElementById(id);
        if (element) element.hidden = !visible;
    };
    function departmentName(id) {
        if (!id) return "Sem departamento";
        const options = department && department.children ? Array.from(department.children) : [];
        const option = options.find(item => item.value === id);
        return option ? option.textContent : id;
    }
    function filterNote(departmentId) {
        if (department && department.value && !departmentId) {
            return "O departamento selecionado é da fonte histórica de consumo e não se aplica ao engajamento.";
        }
        return "";
    }
    function renderAdoption(report) {
        const data = core.mapAdoption(report);
        if (!data) {
            show("engagement-adoption", false);
            return;
        }
        show("engagement-adoption", true);
        text("engagement-adoption-source", `Fonte: ${data.sourceLabel} · corte ${data.ate || core.MISSING} · janela de ${data.windowDays || 30} dias`);
        text("engagement-adoption-rate", data.rateLabel);
        text("engagement-adoption-status", data.statusLabel);
        text("engagement-adoption-active", `${data.activeLabel} / ${data.eligibleLabel}`);
        text("engagement-adoption-idle", data.idleLicensesLabel);
        text("engagement-adoption-date", data.ate || core.MISSING);
        text("engagement-adoption-note", data.note);
        text("engagement-coverage", data.coverageLabel);
        const donut = document.getElementById("engagement-donut");
        if (donut) {
            if (data.donut) {
                donut.hidden = false;
                donut.style.background = `conic-gradient(#2f9e6b 0 ${data.donut.rate}%, #8a94a6 ${data.donut.rate}% 100%)`;
                donut.setAttribute("aria-label", `${data.donut.label}; ${data.rateLabel} de adoção`);
                text("engagement-donut-label", `${data.rateLabel} · ${data.donut.label}`);
            } else {
                donut.hidden = true;
                donut.removeAttribute("style");
                text("engagement-donut-label", "Sem dado");
            }
        }
        const body = document.getElementById("engagement-departments");
        if (!body) return;
        body.replaceChildren();
        for (const item of data.departments) {
            const row = document.createElement("tr");
            for (const value of [departmentName(item.id), item.eligibleLabel, item.activeLabel, item.rateLabel]) {
                const cell = document.createElement("td");
                cell.textContent = value;
                row.appendChild(cell);
            }
            body.appendChild(row);
        }
    }
    function renderTraining(report) {
        const data = core.mapTraining(report);
        if (!data) {
            show("engagement-training", false);
            return;
        }
        show("engagement-training", true);
        text("engagement-training-source", `Fonte: ${data.sourceLabel} · corte ${data.ate || core.MISSING}`);
        text("engagement-training-note", data.nominalNote
            + (data.quadrants.some(quadrant => quadrant.truncated) ? " Lista truncada em até 200 por quadrante." : ""));
        text("engagement-training-coverage", data.coverageLabel);
        const container = document.getElementById("engagement-quadrants");
        const nominal = document.getElementById("engagement-nominal");
        if (container) container.replaceChildren();
        if (nominal) {
            nominal.replaceChildren();
            nominal.hidden = !data.nominal;
        }
        for (const quadrant of data.quadrants) {
            if (container) {
                const card = document.createElement("article");
                card.className = "engagement-quadrant";
                card.dataset.quadrant = quadrant.key;
                const title = document.createElement("h4");
                title.textContent = quadrant.label;
                const value = document.createElement("strong");
                value.textContent = quadrant.totalLabel;
                card.append(title, value);
                container.appendChild(card);
            }
            if (nominal && data.nominal) {
                const block = document.createElement("section");
                const title = document.createElement("h4");
                title.textContent = quadrant.label;
                block.appendChild(title);
                if (quadrant.users.length) {
                    const list = document.createElement("ul");
                    for (const user of quadrant.users) {
                        const item = document.createElement("li");
                        item.textContent = user.name;
                        list.appendChild(item);
                    }
                    block.appendChild(list);
                } else {
                    const empty = document.createElement("p");
                    empty.textContent = "Nenhum usuário neste quadrante.";
                    block.appendChild(empty);
                }
                nominal.appendChild(block);
            }
        }
    }
    function renderCredits(report) {
        const data = core.mapCredits(report, personal);
        if (!data) {
            show("engagement-credits", false);
            return;
        }
        show("engagement-credits", true);
        text("engagement-credits-source", `Fonte: ${data.sourceLabel} · ${data.monthLabel} · ${data.statusLabel}`);
        text("engagement-credits-note", data.note || (data.partial
            ? "Há linhas com dados parciais; o score exibido preserva a situação de cada linha."
            : ""));
        text("engagement-credits-coverage", data.coverageLabel);
        show("engagement-credits-personal", data.personal && Boolean(data.item));
        show("engagement-credits-team", !data.personal);
        if (data.personal) {
            if (!data.item) return;
            text("engagement-credits-score", data.item.scoreLabel);
            text("engagement-credits-status", data.item.dataStatusLabel);
            text("engagement-credits-usage", data.item.usageLabel);
            text("engagement-credits-training", data.item.trainingLabel);
            text("engagement-credits-license", data.item.licenseLabel);
            return;
        }
        text("engagement-credits-total", data.totalLabel);
        text("engagement-credits-average", data.averageLabel);
        text("engagement-credits-components",
            `Uso ${data.components.usage} · Treino ${data.components.training} · Licença ${data.components.license}`);
        const buckets = document.getElementById("engagement-credits-buckets");
        if (!buckets) return;
        buckets.replaceChildren();
        for (const bucket of data.buckets) {
            const row = document.createElement("div");
            row.className = "engagement-bucket";
            const label = document.createElement("span");
            label.textContent = `${bucket.label}: ${bucket.countLabel} (${bucket.shareLabel})`;
            const bar = document.createElement("meter");
            bar.min = 0;
            bar.max = 100;
            bar.value = Number(bucket.shareLabel.replace("%", ""));
            bar.setAttribute("aria-label", `${bucket.label}: ${bucket.countLabel} participantes (${bucket.shareLabel})`);
            row.append(label, bar);
            buckets.appendChild(row);
        }
    }
    async function load() {
        if (!window.Auth || !window.Auth.requireAuth()) return;
        const current = ++version;
        const fonte = source && source.value ? source.value : "OPERACIONAL";
        const ate = to && to.value ? to.value : null;
        const departmentId = department && department.value
            && !department.value.startsWith("historical:") ? department.value : null;
        const note = filterNote(departmentId);
        if (content) content.hidden = true;
        if (message) message.textContent = "Carregando engajamento…";
        try {
            if (personal) {
                const credits = await client.get(`/v1/engajamento/creditos?${core.creditsQuery({
                    fonte, mes: core.monthFromDate(ate), departmentId
                })}`);
                if (current !== version) return;
                renderCredits(credits);
            } else {
                const query = core.engagementQuery({ fonte, ate, departmentId });
                const [adoption, training, credits] = await Promise.all([
                    client.get(`/v1/engajamento/adocao?${query}`),
                    client.get(`/v1/engajamento/treinamentos/cruzamento?${query}`),
                    client.get(`/v1/engajamento/creditos?${core.creditsQuery({
                        fonte, mes: core.monthFromDate(ate), departmentId
                    })}`)
                ]);
                if (current !== version) return;
                renderAdoption(adoption);
                renderTraining(training);
                renderCredits(credits);
            }
            if (content) content.hidden = false;
            if (message) message.textContent = note;
        } catch (error) {
            if (current !== version) return;
            if (content) content.hidden = true;
            if (message) {
                message.textContent = error.status === 403
                    ? "Sem permissão para consultar o engajamento neste recorte. O recorte não é ampliado automaticamente."
                    : "Não foi possível carregar o engajamento. Tente novamente.";
            }
        }
    }
    if (source) {
        source.disabled = false;
        source.addEventListener("change", load);
    }
    if (department) department.addEventListener("change", load);
    form.addEventListener("submit", event => {
        event.preventDefault();
        load();
    });
    if (window.Auth && window.Auth.requireAuth()) load();
})();
