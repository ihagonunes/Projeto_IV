/* SPI-92 / SPI-93: gráficos nativos e consultas já autorizadas, exclusivos do dashboard. */
(function () {
    "use strict";
    const C = window.AtlasVisualCore, form = document.getElementById("metrics-filters");
    if (!C || !form) return;
    const byId = id => document.getElementById(id);
    const palette = ["var(--color-primary)", "#aaa6eb", "#82b9d9", "#91d6bb", "#e8a08b", "#bac6d8", "#d997c1", "#a3c591"];
    const metric = byId("dashboard-timeline-metric"), model = byId("dashboard-timeline-model");
    const plot = byId("dashboard-timeline-plot"), message = byId("dashboard-timeline-message");
    let source, entry, catalog = [], mode = "general", epoch = 0, drawing = 0, abort = null;
    let requests = new Map();
    const node = (tag, text, cls) => {
        const e = document.createElement(tag);
        if (text != null) e.textContent = text;
        if (cls) e.className = cls;
        return e;
    };
    const svgNode = (tag, attrs = {}, text) => {
        const e = document.createElementNS("http://www.w3.org/2000/svg", tag);
        for (const [key, value] of Object.entries(attrs)) e.setAttribute(key, value);
        if (text != null) e.textContent = text;
        return e;
    };
    const short = value => new Intl.NumberFormat("pt-BR", { notation: "compact", maximumFractionDigits: 1 }).format(value);
    const date = value => new Date(value).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", timeZone: "UTC" });
    function line(target, series, format, { from, to, title = "Evolução no período" } = {}) {
        target.replaceChildren();
        const known = series.flatMap(s => s.points).filter(p => p.value != null);
        if (!known.length) { target.append(node("p", "Sem medições disponíveis neste recorte. Valores ausentes não são zero.", "demo-muted")); return; }
        const geometry = C.geometry(series, from, to), id = "atlas-chart-" + (++line.id);
        const legend = node("div", null, "dashboard-legend");
        series.forEach((s, index) => {
            const item = node("span"), swatch = node("i");
            swatch.style.background = palette[index % palette.length];
            item.append(swatch, node("span", s.name + (!s.points.some(p => p.value != null) ? " · sem registros" : "")));
            legend.append(item);
        });
        const chart = svgNode("svg", { viewBox: "0 0 650 250", role: "img", "aria-labelledby": id, class: "dashboard-native-line" });
        chart.append(svgNode("title", { id }, title + ". " + series.map(s => s.name).join(", ")));
        for (let i = 0; i < 4; i++) {
            const value = geometry.floor + (geometry.ceiling - geometry.floor || 1) * i / 3;
            chart.append(svgNode("line", { x1: 66, x2: 624, y1: geometry.y(value), y2: geometry.y(value), class: "dashboard-gridline" }),
                svgNode("text", { x: 55, y: geometry.y(value) + 4, "text-anchor": "end" }, short(value)));
        }
        series.forEach((s, index) => {
            const color = palette[index % palette.length];
            for (const segment of C.segments(s.points, geometry)) {
                if (segment.length > 1) chart.append(svgNode("polyline", { points: segment.map(p => p.join(",")).join(" "), fill: "none", stroke: color, "stroke-width": 2.5, "stroke-linejoin": "round" }));
                else chart.append(svgNode("circle", { cx: segment[0][0], cy: segment[0][1], r: 3, fill: color }));
            }
        });
        for (let i = 0; i < 5; i++) {
            const time = geometry.low + (geometry.high - geometry.low) * i / 4;
            chart.append(svgNode("text", { x: 66 + 558 * i / 4, y: 242, "text-anchor": i === 0 ? "start" : i === 4 ? "end" : "middle" }, date(time)));
        }
        const readout = node("p", "Passe sobre uma data ou use Tab para consultar os valores.", "dashboard-point-readout");
        readout.setAttribute("aria-live", "polite");
        const times = [...new Set(series.flatMap(s => s.points.map(p => p.time)))].sort((a, b) => a - b);
        const details = node("details", null, "dashboard-chart-details"), summary = node("summary", "Ver valores exatos");
        const table = node("table", null, "table demo-table"), head = node("thead"), tr = node("tr"), body = node("tbody");
        for (const label of ["Data", ...series.map(s => s.name)]) { const th = node("th", label); th.scope = "col"; tr.append(th); }
        head.append(tr);
        const maps = series.map(s => new Map(s.points.map(p => [p.time, p.value])));
        times.forEach((time, index) => {
            const labels = maps.map(map => map.get(time) == null ? "Sem dado" : format(map.get(time)));
            const text = date(time) + " · " + labels.map((v, i) => series[i].name + ": " + v).join(" · ");
            const x = geometry.x(time), before = index ? geometry.x(times[index - 1]) : 66, after = index + 1 < times.length ? geometry.x(times[index + 1]) : 624;
            const hit = svgNode("rect", { x: Math.max(66, (before + x) / 2), y: 20, width: Math.max(2, (after - before) / 2), height: 190, fill: "transparent", tabindex: 0, "aria-label": text, class: "dashboard-chart-hit" });
            const show = () => { readout.textContent = text; };
            hit.append(svgNode("title", {}, text));
            hit.addEventListener("mouseenter", show); hit.addEventListener("focus", show); hit.addEventListener("click", show); chart.append(hit);
            const row = node("tr");
            for (const label of [new Date(time).toLocaleDateString("pt-BR", { timeZone: "UTC" }), ...labels]) row.append(node("td", label));
            body.append(row);
        });
        table.append(head, body);
        const overflow = node("div", null, "table-responsive"); overflow.append(table); details.append(summary, overflow);
        target.append(legend, chart, readout, details);
    }
    line.id = 0;
    function bars(target, rows, format, note) {
        target.replaceChildren(node("p", note, "demo-muted small"));
        const max = Math.max(0, ...rows.filter(r => r.value != null).map(r => Math.abs(Number(r.value))));
        if (!rows.length) { target.append(node("p", "Sem dados neste período e recorte.")); return; }
        for (const row of rows) {
            const wrapper = node("div", null, "dashboard-visual-bar"), labels = node("div", null, "dashboard-visual-bar-labels");
            labels.append(node("span", row.name), node("strong", format(row.value)));
            wrapper.append(labels);
            if (row.value != null) {
                const track = node("div", null, "dashboard-visual-track"), fill = node("span");
                fill.style.width = (max ? Math.abs(Number(row.value)) / max * 100 : 0) + "%";
                track.setAttribute("aria-hidden", "true"); track.append(fill); wrapper.append(track);
            }
            target.append(wrapper);
        }
    }
    function renderRows(target, title, rows, format, note) {
        target.classList.remove("dashboard-chart-slot"); target.classList.add("dashboard-connected-chart");
        if (/^Evolução da minha eficiência$/.test(title)) {
            const names = [...new Set(rows.map(r => r.name.slice(13)))];
            const series = names.map(name => ({ name, points: rows.filter(r => r.name.slice(13) === name).map(r => ({ time: Date.parse(r.name.slice(0, 10)), value: r.value })) }));
            line(target, series, format, { title }); target.append(node("p", note, "demo-muted small"));
        } else if (/^(Participação da equipe|Adoção nos últimos 30 dias)$/.test(title) && rows.length === 2 && rows.every(r => r.value != null) && rows.reduce((sum, r) => sum + Number(r.value), 0) > 0) {
            target.replaceChildren(node("p", note, "demo-muted small"));
            const total = rows.reduce((sum, r) => sum + Number(r.value), 0), rate = Number(rows[0].value) / total * 100;
            const ring = node("div", null, "dashboard-visual-ring"); ring.style.setProperty("--rate", rate + "%");
            ring.setAttribute("role", "img"); ring.setAttribute("aria-label", rows.map(r => r.name + ": " + format(r.value)).join("; "));
            ring.append(node("strong", new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(rate) + "%"));
            const wrapper = node("div", null, "dashboard-ring-row"); wrapper.append(ring, node("p", rows.map(r => r.name + ": " + format(r.value)).join(" · "))); target.append(wrapper);
        } else bars(target, rows, format, note);
    }
    async function modelSeries(item, context, current, signal) {
        if (!requests.has(item.id)) {
            const endpoint = C.endpoint(context.source, form.dataset.scope === "self", context.entry, item.id);
            const promise = window.ApiClient.request(endpoint, { method: "GET", signal }).then(result => C.normalizeSeries(context.source, result));
            requests.set(item.id, promise);
            promise.catch(() => { if (current === epoch) requests.delete(item.id); });
        }
        return requests.get(item.id);
    }
    async function draw() {
        const current = epoch, drawId = ++drawing;
        if (!plot || !entry?.data) return;
        const context = { source, entry }, activeMetric = metric.value;
        plot.replaceChildren(); message.textContent = "Carregando gráfico…"; plot.setAttribute("aria-busy", "true");
        const format = activeMetric === "cost" ? value => window.AtlasPanels.money(value, context.source)
            : value => window.AtlasPanels.number(value) + (activeMetric === "requests" ? " requisições" : " tokens");
        try {
            let series;
            const selected = catalog.find(m => m.id === model.value);
            if (mode === "general") {
                const data = window.AtlasPanels.normalize(context.source, context.entry.data, context.entry.names);
                series = [{ name: "Consumo do recorte selecionado", points: C.points(data.series, activeMetric) }];
            } else {
                const items = mode === "single" ? (selected ? [selected] : []) : catalog;
                if (!items.length) { message.textContent = "Nenhum modelo disponível no catálogo autorizado desta fonte."; return; }
                const results = new Array(items.length);
                let next = 0;
                await Promise.all(Array.from({ length: Math.min(3, items.length) }, async () => {
                    while (next < items.length && current === epoch && !abort.signal.aborted) {
                        const index = next++, item = items[index];
                        const rows = await modelSeries(item, context, current, abort.signal);
                        results[index] = { name: item.name, points: C.points(rows, activeMetric) };
                    }
                }));
                series = results;
            }
            if (current !== epoch || drawId !== drawing) return;
            line(plot, series, format, { from: context.entry.from, to: context.entry.to, title: "Consumo diário de IA" });
            const selectedModel = new URLSearchParams(context.entry.params).has("modelId");
            message.textContent = (mode === "models" ? `Comparação dos ${catalog.length} modelos do catálogo autorizado. ` : "")
                + (mode !== "general" && selectedModel ? "O filtro de modelo do consumo é substituído apenas neste gráfico. " : "")
                + "Lacunas e custos desconhecidos permanecem sem medição; as fontes não são somadas.";
        } catch (error) {
            if (current !== epoch || drawId !== drawing) return;
            plot.replaceChildren();
            message.textContent = error.status === 403 ? "Sem permissão para comparar os modelos neste recorte."
                : "Não foi possível carregar a comparação. Selecione novamente o modo para tentar de novo.";
        } finally { if (current === epoch && drawId === drawing) plot.setAttribute("aria-busy", "false"); }
    }
    function selectSource(nextSource, nextEntry) {
        if (!plot || (source === nextSource && entry === nextEntry)) return;
        source = nextSource; entry = nextEntry; epoch++; drawing++;
        if (abort) abort.abort(); abort = new AbortController(); requests = new Map();
        const previousModel = model.value;
        catalog = C.catalog(source, entry || {}); model.replaceChildren();
        for (const item of catalog) { const option = node("option", item.name); option.value = item.id; model.append(option); }
        const appliedModel = new URLSearchParams(entry?.params || "").get("modelId");
        if (catalog.some(item => item.id === previousModel)) model.value = previousModel;
        else if (catalog.some(item => item.id === appliedModel)) model.value = appliedModel;
        for (const button of document.querySelectorAll("[data-timeline-mode]")) button.disabled = !entry?.data;
        for (const button of document.querySelectorAll("[data-dashboard-days]")) button.disabled = !entry?.data;
        metric.disabled = !entry?.data; model.disabled = !entry?.data || !catalog.length;
        byId("dashboard-timeline-context").textContent = entry?.data
            ? `${source === "HISTORICO" ? "Histórico · USD estimado" : "Operacional · moeda não informada"} · ${entry.from} a ${entry.to}` : "Aguardando dados da fonte selecionada.";
        plot.replaceChildren();
        if (!entry?.data) { message.textContent = entry?.error || "Carregando dados…"; plot.setAttribute("aria-busy", "false"); return; }
        draw();
    }
    for (const button of document.querySelectorAll("[data-timeline-mode]")) button.addEventListener("click", () => {
        mode = button.dataset.timelineMode;
        for (const item of document.querySelectorAll("[data-timeline-mode]")) item.setAttribute("aria-pressed", String(item === button));
        byId("dashboard-timeline-model-field").hidden = mode !== "single";
        draw();
    });
    metric?.addEventListener("change", draw); model?.addEventListener("change", draw);
    for (const button of document.querySelectorAll("[data-dashboard-days]")) button.addEventListener("click", () => {
        const end = byId("metrics-to").value;
        if (!/^\d{4}-\d{2}-\d{2}$/.test(end)) return;
        const start = new Date(Date.parse(end) - (Number(button.dataset.dashboardDays) - 1) * 86400000);
        if (!Number.isFinite(start.getTime())) return;
        byId("metrics-from").value = start.toISOString().slice(0, 10);
        form.dispatchEvent(new Event("submit", { cancelable: true }));
    });
    function projection(data, format, simulated = false) {
        const target = byId("budget-projection-chart");
        if (!target) return;
        bars(target, [{ name: "Gasto até hoje", value: data.gastoAteHoje },
            { name: "Projetado para o mês", value: data.projetado },
            { name: "Orçamento", value: data.orcamento }], format,
        simulated ? "Comparação do cenário simulado, sem gravação financeira." : "Mesmo mês e departamento da projeção. Moeda não informada pela API.");
    }
    window.AtlasDashboardVisuals = { selectSource, renderRows, line, projection };
})();
