(function () {
    "use strict";

    const number = new Intl.NumberFormat("pt-BR");
    window.renderDailyRequests = (target, series) => {
        if (!target) return;
        target.replaceChildren();
        if (!series.length) {
            target.textContent = "Sem requisições neste recorte.";
            return;
        }

        const peak = series.reduce((max, row) => Math.max(max, Number(row.requests)), 1);
        const plot = document.createElement("ol");
        plot.className = "dashboard-daily-plot";
        for (const { period, requests } of series) {
            const item = document.createElement("li");
            const date = period.slice(0, 10).split("-").reverse().join("/");
            const caption = `${date} · ${number.format(requests)} requisições`;
            const bar = document.createElement("span");
            const label = document.createElement("span");
            item.tabIndex = 0;
            item.setAttribute("aria-label", caption);
            item.title = caption;
            bar.className = "dashboard-daily-bar";
            bar.style.setProperty("--bar-height", `${100 * Number(requests) / peak}%`);
            label.className = "dashboard-daily-label";
            label.textContent = `${date.slice(0, 5)} · ${number.format(requests)}`;
            item.append(bar, label);
            plot.appendChild(item);
        }
        target.appendChild(plot);
    };
})();
