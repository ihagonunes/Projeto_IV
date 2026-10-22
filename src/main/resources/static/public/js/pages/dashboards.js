/* Apenas apresentação: séries e indicadores chegam prontos do provedor. */
(() => {
    "use strict";
    const payload = document.getElementById("dashboard-data");
    if (!payload) return;
    const dashboard = JSON.parse(payload.textContent);
    if (!dashboard || !dashboard.data) return;

    const rows = Array.from(document.querySelectorAll("[data-execution-row]"));
    if (rows.length) {
        const pager = document.getElementById("pagination");
        const previous = document.getElementById("previous-page");
        const next = document.getElementById("next-page");
        let page = 0;
        const size = 10;
        const pages = Math.ceil(rows.length / size);
        function showPage() {
            rows.forEach((row, i) => { row.hidden = i < page * size || i >= (page + 1) * size; });
            document.getElementById("page-label").textContent = "Página " + (page + 1) + " de " + pages + " · " + rows.length + " execuções";
            previous.disabled = page === 0;
            next.disabled = page === pages - 1;
        }
        previous.addEventListener("click", () => { if (page > 0) { page--; showPage(); } });
        next.addEventListener("click", () => { if (page < pages - 1) { page++; showPage(); } });
        pager.hidden = false;
        showPage();
    }

    const message = document.getElementById("chart-message");
    if (typeof Chart === "undefined") {
        message.textContent = "Os gráficos não puderam ser carregados. Consulte os valores em “Ver dados do gráfico”.";
        message.hidden = false;
        document.querySelectorAll(".demo-chart").forEach(el => { el.hidden = true; });
        return;
    }
    const colors = { gold: "#f0c22f", blue: "#69b9ec", purple: "#af94ef", green: "#82dbb1" };
    Chart.defaults.color = "#b8c4d9";
    Chart.defaults.borderColor = "#26324a";
    Chart.defaults.font.family = "'Poppins', sans-serif";
    Chart.defaults.font.size = 10;
    const animation = !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const options = {
        responsive: true, maintainAspectRatio: false, animation,
        interaction: { mode: "index", intersect: false },
        plugins: { legend: { position: "bottom", labels: { boxWidth: 10, padding: 16 } } },
        scales: { x: { grid: { display: false }, ticks: { maxTicksLimit: 10 } }, y: { beginAtZero: true } }
    };
    const series = dashboard.data.timeline;
    const agent = dashboard.profile === "agentes";
    const timeline = agent ? [
        { type: "line", label: "Latência média (ms)", data: series.map(row => row.latencyMs), yAxisID: "latency", borderColor: colors.blue, backgroundColor: colors.blue, pointRadius: 2, order: 0 },
        { type: "bar", label: "Execuções", data: series.map(row => row.executions), backgroundColor: colors.gold, borderRadius: 3, order: 1 }
    ] : [
        { label: "Entrada", data: series.map(row => row.inputTokens), backgroundColor: colors.gold, borderRadius: 3 },
        { label: "Saída", data: series.map(row => row.outputTokens), backgroundColor: colors.blue, borderRadius: 3 }
    ];
    const firstOptions = structuredClone(options);
    firstOptions.scales.y.title = { display: true, text: agent ? "Execuções" : "Tokens" };
    if (agent) {
        firstOptions.scales.y.ticks = { precision: 0 };
        firstOptions.scales.latency = { position: "right", beginAtZero: true, grid: { drawOnChartArea: false }, title: { display: true, text: "Latência (ms)" } };
    } else {
        firstOptions.scales.x.stacked = true;
        firstOptions.scales.y.stacked = true;
    }
    new Chart(document.getElementById("timeline-chart"), { type: "bar", data: { labels: series.map(row => row.label), datasets: timeline }, options: firstOptions });
    const detailOptions = structuredClone(options);
    let detail;
    if (agent) {
        const stages = dashboard.data.stages;
        detail = { labels: stages.map(row => row.label), datasets: [
            { label: "Busca RAG", data: stages.map(row => row.ragMs), backgroundColor: colors.gold },
            { label: "Tool orçamentária", data: stages.map(row => row.toolMs), backgroundColor: colors.blue },
            { label: "Geração LLM", data: stages.map(row => row.llmMs), backgroundColor: colors.purple }
        ] };
        detailOptions.indexAxis = "y";
        detailOptions.scales.x = { stacked: true, beginAtZero: true, title: { display: true, text: "Latência média (ms)" } };
        detailOptions.scales.y = { stacked: true, grid: { display: false } };
    } else {
        detail = { labels: series.map(row => row.label), datasets: [
            { label: "Meu consumo", data: series.map(row => row.totalTokens), borderColor: colors.gold, backgroundColor: colors.gold, pointRadius: 2 },
            { label: "Média anônima", data: series.map(row => row.averageTokens), borderColor: colors.blue, backgroundColor: colors.blue, borderDash: [5, 5], pointRadius: 2 }
        ] };
        detailOptions.scales.y.title = { display: true, text: "Tokens" };
    }
    new Chart(document.getElementById("detail-chart"), { type: agent ? "bar" : "line", data: detail, options: detailOptions });
})();
