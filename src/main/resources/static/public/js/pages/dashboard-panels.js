/* SPI-93: conexão dos espaços de cada perfil às APIs existentes, sem ampliar o RBAC. */
(function () {
    "use strict";
    const C = window.AtlasPanels, form = document.getElementById("metrics-filters");
    if (!C || !form) return;
    const settings = document.getElementById("dashboard-main").dataset;
    const profile = settings.profile;
    const source = document.getElementById("dashboard-source"), cache = new Map();
    const chartNodes = Array.from(document.querySelectorAll("[data-dashboard-chart]"));
    const cards = Array.from(document.querySelectorAll("[data-dashboard-kpi]"));
    let revision = 0, historyPage = 0;
    function node(tag, text, cls) {
        const element = document.createElement(tag);
        if (text != null) element.textContent = text;
        if (cls) element.className = cls;
        return element;
    }
    function card(label, value, note) {
        for (const element of cards.filter(item => item.dataset.dashboardKpi === label)) {
            const strong = element.querySelector("strong"), description = element.querySelector("p");
            strong.textContent = value == null ? "Sem dado" : value;
            strong.removeAttribute("aria-label");
            description.textContent = note;
        }
    }
    function chart(title, rows, format = C.number, note = "") {
        for (const target of chartNodes.filter(item => item.dataset.dashboardChart === title)) {
            target.replaceChildren(node("p", note, "demo-muted small"));
            target.classList.add("dashboard-connected-chart");
            target.classList.remove("dashboard-chart-slot");
            if (!rows.length) { target.append(node("p", "Sem dados neste período e recorte.")); continue; }
            const max = Math.max(1, ...rows.map(row => Number(row.value || 0)));
            for (const row of rows) {
                const line = node("div", null, "dashboard-series-row");
                line.append(node("span", row.name), node("strong", format(row.value)));
                if (row.value != null) {
                    const meter = node("meter"); meter.min = 0; meter.max = max; meter.value = Number(row.value);
                    meter.setAttribute("aria-label", `${row.name}: ${format(row.value)}`); line.append(meter);
                }
                target.append(line);
            }
        }
    }
    function messageChart(title, text) {
        for (const target of chartNodes.filter(item => item.dataset.dashboardChart === title)) {
            target.replaceChildren(node("p", text, "demo-muted"));
        }
    }
    function pending() {
        for (const item of cards) card(item.dataset.dashboardKpi, null, C.reason(item.dataset.dashboardKpi));
        for (const item of chartNodes) messageChart(item.dataset.dashboardChart, C.reason(item.dataset.dashboardChart));
    }
    function table(title, headers, rows, note) {
        const section = Array.from(document.querySelectorAll("[data-dashboard-table]"))
            .find(item => item.dataset.dashboardTable === title);
        if (!section) return;
        const head = section.querySelector("thead tr"), body = section.querySelector("tbody");
        head.replaceChildren(...headers.map(value => { const th = node("th", value); th.scope = "col"; return th; }));
        body.replaceChildren();
        if (!rows.length) {
            const tr = node("tr"), td = node("td", "Sem registros neste recorte."); td.colSpan = headers.length;
            tr.append(td); body.append(tr);
        }
        for (const row of rows) {
            const tr = node("tr");
            for (const value of row) { const td = node("td"); if (typeof value === "string") td.textContent = value; else td.append(value); tr.append(td); }
            body.append(tr);
        }
        const caption = section.querySelector("caption"); caption.textContent = `${title}. ${note}`;
        const description = section.querySelector(".demo-section-title p"); description.textContent = note;
    }
    function selectedParams() {
        const params = new URLSearchParams({ from: document.getElementById("metrics-from").value,
            to: document.getElementById("metrics-to").value });
        const model = document.getElementById("metrics-model").value;
        const department = document.getElementById("dashboard-area")?.value;
        const user = document.getElementById("metrics-user");
        for (const [key, value] of [["modelId", model], ["departmentId", department]]) {
            if (value) params.set(key, source.value === "HISTORICO" ? value.replace(/^historical:/, "") : value);
        }
        if (profile !== "colaborador" && user?.value && user.dataset.userId) params.set("userId", user.dataset.userId);
        return params;
    }
    function sourceNote(data) {
        return `${data.source === "HISTORICO" ? "Histórico · custo estimado USD" : "Operacional · custo registrado; moeda não informada pela API"}`
            + (data.unpriced ? ` · ${C.number(data.unpriced)} registros sem preço/tokens completos; custo parcial.` : ".");
    }
    const simulatedHours = requests => requests * 30 / 3600;
    function demoReturn(data) {
        if (settings.demoReturn !== "true" || !data.requests) return;
        const hourlyValueUsd = 15, unitCostUsd = 0.01;
        const hours = simulatedHours;
        const gross = requests => hours(requests) * hourlyValueUsd;
        const cost = requests => requests * unitCostUsd;
        const net = requests => gross(requests) - cost(requests);
        const usd = value => C.money(value, "HISTORICO");
        const note = "Simulação de demo · 30 s por requisição, US$ 15/h e custo simulado de US$ 0,01 por requisição.";
        card("Tempo economizado estimado", C.number(hours(data.requests)) + " h", note);
        card("Valor gerado estimado", usd(gross(data.requests)), note);
        card("Benefício líquido estimado", usd(net(data.requests)), note);
        chart("Evolução da minha eficiência", data.series.flatMap(row => [
            { name: row.period + " · custo simulado", value: cost(row.requests) },
            { name: row.period + " · benefício líquido simulado", value: net(row.requests) }
        ]), usd, note);
    }
    function renderContextEfficiency(requests, averageRequests) {
        const rows = [{ name: "Meu tempo estimado", value: simulatedHours(requests) }];
        if (averageRequests != null) {
            rows.push({ name: "Média estimada do departamento", value: simulatedHours(averageRequests) });
        }
        const note = "Simulação · 30 segundos por requisição." + (averageRequests == null
            ? " A referência coletiva exige mês fechado completo, todos os modelos e 10 colegas com uso."
            : " Mesmo mês e fonte, com média anônima dos colegas com uso.");
        chart("Eficiência em contexto", rows, value => C.number(value) + " h", note);
    }
    async function peerComparison(data, current, entry) {
        if (settings.demoReturn === "true") renderContextEfficiency(data.requests);
        const month = entry.from.slice(0, 7);
        const start = new Date(month + "-01T00:00:00Z");
        const end = new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth() + 1, 0));
        const personal = [{ name: "Meu consumo", value: data.tokens }];
        const params = new URLSearchParams(entry.params);
        if (entry.from !== month + "-01" || entry.to !== end.toISOString().slice(0, 10) || params.has("modelId")) {
            const note = "A referência do departamento está disponível para meses fechados completos e todos os modelos.";
            card("Média anônima de consumo", "Fora do recorte", note);
            chart("Comparação anônima", personal, C.number, note);
            return;
        }
        const query = new URLSearchParams({month, source: data.source});
        try {
            const result = await window.ApiClient.get("/v1/observability/metrics/me/peer-consumption?" + query);
            if (current !== revision) return;
            if (settings.demoReturn === "true") renderContextEfficiency(data.requests, result.averageRequests);
            const average = result.averageTokens;
            const note = average == null ? "Referência indisponível. São necessários 10 colegas com uso em um mês fechado."
                : "Média de tokens dos colegas do departamento com uso, na mesma fonte e mês.";
            card("Média anônima de consumo", average == null ? "Sem referência" : C.number(average), note);
            if (average != null) personal.push({ name: "Média do departamento", value: average });
            chart("Comparação anônima", personal, C.number, note);
        } catch {
            if (current !== revision) return;
            const note = "A referência coletiva não pôde ser consultada.";
            card("Média anônima de consumo", "Consulta indisponível", note);
            chart("Comparação anônima", personal, C.number, note);
        }
    }
    async function extra(data, current, entry) {
        if (profile === "colaborador") {
            demoReturn(data);
            await peerComparison(data, current, entry);
            return;
        }
        const params = entry.params == null ? selectedParams() : new URLSearchParams(entry.params), client = window.ApiClient;
        params.set("from", entry.from); params.set("to", entry.to);
        if (profile === "lider") {
            try {
                let own;
                if (data.source === "HISTORICO") own = C.normalize(data.source, await client.get(`/v1/observability/metrics/historical/me?${params}`));
                else {
                    const [usage, costs] = await Promise.all([
                        client.get(`/v1/observability/metrics/me/usage?${params}`),
                        client.get(`/v1/observability/metrics/me/costs?${params}`)
                    ]);
                    own = C.normalize(data.source, { usage, costs });
                }
                if (current === revision) {
                    card("Meus gastos", C.money(own.cost, data.source), sourceNote(own));
                    const personal = C.recentComparison(own.series, entry.from, entry.to);
                    const collective = C.recentComparison(data.series, entry.from, entry.to);
                    if (personal && collective) chart("Últimos 7 dias × 7 dias anteriores",
                        [...personal.map(row => ({ name: `${row.name} · pessoal`, value: row.cost })),
                            ...collective.map(row => ({ name: `${row.name} · equipe`, value: row.cost }))].filter(row => row.value != null),
                        value => C.money(value, data.source), "Duas janelas móveis de 7 dias até o corte. O custo pessoal pode estar incluído no da equipe; não somar as séries.");
                }
            } catch (error) { if (current === revision) card("Meus gastos", null, "Não foi possível consultar o custo pessoal neste recorte."); }
        }
        if (["lider", "executivo"].includes(profile) && data.source === "OPERACIONAL") {
            try {
                const groupBy = profile === "lider" ? "USER" : "DEPARTMENT";
                const groups = await client.get(`/v1/observability/metrics/costs?${params}&groupBy=${groupBy}`);
                if (current !== revision) return;
                const labels = new Map();
                if (groupBy === "DEPARTMENT") {
                    const select = document.getElementById("dashboard-area");
                    for (const option of Array.from(select?.options || [])) labels.set(option.value, option.textContent);
                }
                chart(profile === "lider" ? "Custo por membro" : "Benchmarking entre departamentos",
                    groups.map(row => ({ name: labels.get(row.chave) || row.chave || "Sem vínculo", value: row.custoEstimado })),
                    value => C.money(value, data.source), "Custo registrado no período; somente recursos devolvidos pela API autorizada.");
                if (profile === "executivo") table("Consolidado por departamento", ["Departamento", "Custo registrado"],
                    groups.map(row => [labels.get(row.chave) || row.chave || "Sem departamento", C.money(row.custoEstimado, data.source)]), sourceNote(data));
            } catch (error) {
                if (current === revision) messageChart(profile === "lider" ? "Custo por membro" : "Benchmarking entre departamentos",
                    error.status === 403 ? "Sem permissão para esta distribuição." : "Não foi possível carregar a distribuição. Reaplique os filtros para tentar novamente.");
            }
        }
        if (profile === "executivo" && data.source === "HISTORICO") {
            const departments = cache.get("HISTORICO")?.data.departments || [];
            const requested = params.get("departmentId");
            try {
                // Consultas sequenciais e restritas aos IDs devolvidos pelo backend; sem ler banco no BFF.
                const rows = [];
                for (const department of departments.filter(item => !requested || item.id === requested)) {
                    const query = new URLSearchParams(params); query.set("departmentId", department.id);
                    const result = C.normalize("HISTORICO", await client.get(`/v1/observability/metrics/historical?${query}`));
                    if (current !== revision) return;
                    rows.push({ name: department.name, value: result.cost, unpriced: result.unpriced });
                }
                if (current !== revision) return;
                chart("Benchmarking entre departamentos", rows, value => C.money(value, data.source), "Custo histórico estimado por departamento; registros sem preço tornam os valores parciais.");
                table("Consolidado por departamento", ["Departamento", "Custo estimado USD", "Cobertura de custo"],
                    rows.map(row => [row.name, C.money(row.value, data.source), row.unpriced ? `${C.number(row.unpriced)} registros incompletos` : "Sem registros incompletos no recorte"]),
                    "Histórico de consumo. A adoção de 30 dias por departamento está no bloco de engajamento, em recorte próprio.");
            } catch (error) {
                if (current === revision) messageChart("Benchmarking entre departamentos", "Não foi possível consultar os departamentos neste recorte.");
            }
        }
    }
    function render() {
        const current = ++revision, entry = cache.get(source.value);
        for (const section of document.querySelectorAll("[data-dashboard-chart-source]")) {
            section.hidden = section.dataset.dashboardChartSource !== source.value;
        }
        // Limpa também comparações de outro período/fonte antes de disparar novas consultas.
        pending();
        if (profile === "executivo") table("Consolidado por departamento", ["Departamento", "Custo"], [], "Carregando o recorte selecionado.");
        const status = document.getElementById("dashboard-source-status");
        if (!entry?.data) {
            const note = entry?.error || "Carregando dados da fonte selecionada…";
            status.textContent = note;
            for (const label of ["Meu consumo de tokens", "Meu custo registrado", "Meus gastos", "Gastos da equipe", "Gastos do departamento", "Gastos corporativos"]) card(label, null, note);
            renderEngagement();
            return;
        }
        const data = C.normalize(source.value, entry.data, entry.names);
        const note = sourceNote(data);
        status.textContent = `${note} Período: ${entry.from} a ${entry.to}. Fontes nunca são somadas.`;
        card("Meu consumo de tokens", C.number(data.tokens), `${note} Entrada ${C.number(C.sum(data.costs, "input"))}; saída ${C.number(C.sum(data.costs, "output"))}.`);
        for (const label of ["Meu custo registrado", "Gastos da equipe", "Gastos do departamento", "Gastos corporativos"]) card(label, C.money(data.cost, data.source), note);
        const tokens = data.series.flatMap(row => [{ name: `${row.period} · entrada`, value: row.input }, { name: `${row.period} · saída`, value: row.output }]);
        chart("Meu consumo ao longo do tempo", tokens, C.number, "Tokens de entrada e saída por dia; valores ausentes não são estimados.");
        for (const title of ["Evolução dos gastos do departamento", "FinOps corporativo"]) chart(title, data.series.map(row => ({ name: row.period, value: row.cost })), value => C.money(value, data.source), note);
        for (const title of ["Modelos usados pela equipe", "Distribuição de modelos"]) chart(title, data.costs.map(row => ({ name: row.name, value: row.requests })), C.number, "Distribuição por número de requisições.");
        const comparison = C.recentComparison(data.series, entry.from, entry.to);
        if (comparison) chart("Últimos 7 dias × 7 dias anteriores", comparison.filter(row => row.cost != null).map(row => ({ name: row.name, value: row.cost })), value => C.money(value, data.source), "Duas janelas móveis completas de 7 dias terminando na data final selecionada; custo do recorte autorizado.");
        else messageChart("Últimos 7 dias × 7 dias anteriores", "Selecione pelo menos 14 dias para comparar duas janelas completas de 7 dias.");
        renderEngagement();
        extra(data, current, entry);
    }
    let engagement = {}, engagementError = null;
    function renderEngagement() {
        const adopted = engagement.adoption, credits = engagement.credits;
        const selectedSource = document.getElementById("engagement-source")?.value;
        const note = `Fonte de engajamento: ${selectedSource || "não selecionada"}.`;
        card("Meus AI Credits", null, engagementError || "Carregando os Credits do mês selecionado…");
        if (adopted) {
            const mapped = window.AtlasEngagement.mapAdoption(adopted);
            const info = `${mapped.sourceLabel} · 30 dias até ${mapped.ate}. ${mapped.statusLabel}`;
            for (const label of ["AI Adoption da área", "AI Adoption global", "Taxa de usuários ativos"]) card(label, mapped.rateLabel, info);
            card("Taxa de usuários inativos", adopted.elegiveis > 0 ? window.AtlasEngagement.percentLabel(100 * adopted.inativos / adopted.elegiveis) : null, info);
            chart("Participação da equipe", [{ name: "Ativos", value: adopted.ativos }, { name: "Inativos", value: adopted.inativos }], C.number, info);
            chart("Adoção nos últimos 30 dias", [{ name: "Ativos (30 dias)", value: adopted.ativos }, { name: "Inativos (30 dias)", value: adopted.inativos }], C.number, `${info}. Retrato do corte selecionado, sem série histórica.`);
        } else {
            for (const label of ["AI Adoption da área", "AI Adoption global", "Taxa de usuários ativos", "Taxa de usuários inativos"]) card(label, null, engagementError || `${note} Carregando adoção…`);
        }
        if (profile === "colaborador") {
            const position = credits?.posicaoPessoal;
            card("Minha posição em AI Credits", position == null ? (credits ? "Sem posição" : null) : position + "º",
                position != null ? "Mesmo departamento, mês, fonte e situação dos dados. Empates compartilham posição."
                    : credits ? "Sem posição calculada para este mês e fonte."
                    : engagementError || "Consultando a posição do mês selecionado…");
        }
        if (credits && profile === "colaborador") {
            const mapped = window.AtlasEngagement.mapCredits(credits, true);
            card("Meus AI Credits", mapped.item?.scoreLabel, `${mapped.sourceLabel} · ${mapped.monthLabel} · ${mapped.statusLabel}. ${mapped.item?.dataStatusLabel || "Sem registro"}`);
        }
        const training = engagement.training;
        for (const element of document.querySelectorAll("[data-dashboard-highlight]")) {
            element.textContent = training
                ? `${window.AtlasEngagement.sourceLabel(training.sourceKind)} · corte ${training.ate}: `
                    + (element.dataset.dashboardHighlight === "Treinados sem uso recente"
                        ? `${C.number(training.quadrantes?.TREINADO_INATIVO?.total)} treinados sem uso na janela. É um sinal para acompanhamento, não avaliação de produtividade.`
                        : `${C.number(training.quadrantes?.NAO_TREINADO_ATIVO?.total)} usuários ativos sem treinamento concluído.`)
                : engagementError || "Carregando cruzamento de treinamento e uso…";
        }
    }
    async function history() {
        if (profile !== "colaborador") return;
        const title = "Minhas conversas no Chat";
        try {
            const result = await window.ApiClient.get(`/v1/conversas?page=${historyPage}&size=10&status=ACTIVE`);
            table(title, ["Conversa", "Canal", "Criada em", "Ações"], result.content.map(item => {
                const link = node("a", "Abrir meu Chat"); link.href = "/chat";
                return [item.titulo || "Conversa sem título", "Chat", new Date(item.createdAt).toLocaleString("pt-BR"), link];
            }), "Conversas ativas pessoais, independentes dos filtros analíticos. O Agente de Políticas ainda não possui histórico unificado. Gerencie e exclua conversas no Chat.");
            const section = document.querySelector('[data-dashboard-table="Meu histórico"]');
            let navigation = section.querySelector(".dashboard-history-pagination");
            if (!navigation) { navigation = node("div", null, "dashboard-history-pagination"); section.append(navigation); }
            navigation.replaceChildren();
            for (const [label, direction, disabled] of [["Anteriores", -1, historyPage === 0], ["Próximas", 1, historyPage + 1 >= result.totalPages]]) {
                const button = node("button", label, "btn btn-outline-secondary btn-sm"); button.type = "button"; button.disabled = disabled;
                button.addEventListener("click", () => { historyPage += direction; history(); }); navigation.append(button);
            }
        } catch (error) { table(title, ["Histórico"], [], error.status === 403 ? "Seu perfil não tem permissão para listar conversas." : "Não foi possível carregar conversas. Recarregue a página para tentar novamente."); }
    }
    window.AtlasDashboard = {
        publish(kind, value) {
            if (["OPERACIONAL", "HISTORICO"].includes(kind)) {
                cache.set(kind, value); render();
            } else if (kind === "engagement-start") { engagement = {}; engagementError = null; renderEngagement(); }
            else if (kind === "engagement-error") { engagement = {}; engagementError = value; renderEngagement(); }
            else if (kind === "engagement") { engagement = { ...engagement, ...value }; renderEngagement(); }
            else if (["budgets", "budgets-status"].includes(kind)) {
                const section = document.querySelector('[data-dashboard-management="Orçamento departamental"]');
                if (!section) return;
                let container = section.querySelector(".dashboard-budget-values");
                if (!container) { container = node("div", null, "dashboard-budget-values"); section.append(container); }
                if (kind === "budgets-status") {
                    container.replaceChildren(node("p", value));
                    for (const old of section.querySelectorAll(".dashboard-budget")) old.parentElement.hidden = true;
                    return;
                }
                container.replaceChildren(node("p", `Orçamento operacional · mês ${value.mes}. Moeda não informada pela API. Não se aplica ao filtro histórico.`));
                if (!value.itens?.length) container.append(node("p", "Sem departamentos com orçamento autorizado neste recorte."));
                for (const item of value.itens || []) container.append(node("p", `${item.departmentName}: orçamento ${C.number(item.orcamento)} · consumo ${C.number(item.custoReal)} · disponível ${item.orcamento == null ? "Sem orçamento" : C.number(Number(item.orcamento) - Number(item.custoReal))} · ${item.status}`));
                for (const old of section.querySelectorAll(".dashboard-budget")) old.parentElement.hidden = true;
            }
        }
    };
    pending(); source.disabled = false;
    source.addEventListener("change", render);
    if (window.Auth.requireAuth()) history();
})();
