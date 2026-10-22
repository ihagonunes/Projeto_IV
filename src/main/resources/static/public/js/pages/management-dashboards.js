/* Apenas apresentação: todos os números chegam prontos do provedor demonstrativo. */
(() => {
    "use strict";
    const payload = document.getElementById("dashboard-data");
    if (!payload) return;
    const dashboard = JSON.parse(payload.textContent);
    if (!dashboard?.management || !dashboard.data?.charts?.length) return;
    const message = document.getElementById("management-chart-message");
    if (typeof Chart === "undefined") {
        message.textContent = "Os gráficos não puderam ser carregados. Consulte os valores em “Ver dados do gráfico”.";
        message.hidden = false;
        document.querySelectorAll(".demo-chart").forEach(element => { element.hidden = true; });
        return;
    }
    Chart.defaults.color = "#b8c4d9";
    Chart.defaults.borderColor = "#26324a";
    Chart.defaults.font.family = "'Poppins', sans-serif";
    const colors = ["#f0c22f", "#69b9ec", "#af94ef", "#82dbb1"];
    dashboard.data.charts.forEach((item, index) => {
        const canvas = document.getElementById(`management-chart-${index}`);
        if (!canvas) return;
        new Chart(canvas, {
            type: item.kind || "bar",
            data: {
                labels: item.labels,
                datasets: item.datasets.map((dataset, datasetIndex) => ({
                    label: dataset.label,
                    data: dataset.values,
                    backgroundColor: item.datasets.length === 1 ? item.labels.map((_, labelIndex) => colors[labelIndex % colors.length]) : colors[datasetIndex % colors.length],
                    borderRadius: 4
                }))
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: !window.matchMedia("(prefers-reduced-motion: reduce)").matches,
                plugins: { legend: { position: "bottom" } },
                scales: { x: { grid: { display: false } }, y: { beginAtZero: true } }
            }
        });
    });
})();
