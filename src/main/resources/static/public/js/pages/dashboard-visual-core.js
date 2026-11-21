/* SPI-92: transformações de apresentação; sem regras de negócio ou autorização. */
(function (root, factory) {
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    else root.AtlasVisualCore = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
    "use strict";
    function value(row, metric) {
        const finite = v => v != null && Number.isFinite(Number(v)) ? Number(v) : null;
        if (metric === "tokens") {
            const input = finite(row.input), output = finite(row.output);
            return input == null || output == null ? null : input + output;
        }
        return finite(row[metric]);
    }
    function points(rows, metric) {
        return rows.map(row => ({ time: Date.parse(row.period), label: row.period, value: value(row, metric) }))
            .filter(row => Number.isFinite(row.time)).sort((a, b) => a.time - b.time);
    }
    function geometry(series, from, to) {
        const all = series.flatMap(s => s.points), known = all.filter(p => p.value != null);
        const start = Date.parse(from), end = Date.parse(to);
        const low = Number.isFinite(start) ? start : Math.min(...all.map(p => p.time));
        const high = Number.isFinite(end) ? end : Math.max(...all.map(p => p.time));
        const floor = Math.min(0, ...known.map(p => p.value));
        const ceiling = Math.max(0, ...known.map(p => p.value));
        const span = ceiling - floor || 1;
        const x = time => 66 + (time - low) / Math.max(86400000, high - low) * 558;
        const y = v => 208 - (v - floor) / span * 174;
        return { low, high, floor, ceiling, x, y };
    }
    function segments(points, geometry) {
        const groups = [];
        let group = [], last = null;
        for (const p of points) {
            if (p.value == null || (last != null && p.time - last > 86400000)) {
                if (group.length) groups.push(group);
                group = [];
            }
            if (p.value != null) group.push([geometry.x(p.time), geometry.y(p.value)]);
            last = p.time;
        }
        if (group.length) groups.push(group);
        return groups;
    }
    function catalog(source, entry) {
        const rows = source === "HISTORICO" ? (entry.data?.models || [])
            : Array.from(entry.names || [], ([id, name]) => ({ id, name }));
        return rows.filter(item => item.id != null).map(item => ({ id: String(item.id), name: String(item.name || "Modelo sem nome") }));
    }
    function endpoint(source, personal, entry, modelId) {
        const params = new URLSearchParams(entry.params || "");
        params.set("from", entry.from); params.set("to", entry.to);
        // Mantém departmentId e userId já validados no recorte. Tenant nunca é parâmetro.
        params.set("modelId", modelId);
        return source === "HISTORICO"
            ? `/v1/observability/metrics/historical${personal ? "/me" : ""}?${params}`
            : `/v1/observability/metrics/${personal ? "me/" : ""}usage?${params}`;
    }
    function normalizeSeries(source, response) {
        return (response.series || []).map(row => ({ period: row.period, requests: row.requests,
            input: row.inputTokens, output: row.outputTokens,
            cost: source === "HISTORICO" ? row.estimatedCostUsd : row.estimatedCost,
            unpriced: row.unpricedRecords || 0 }));
    }
    return { value, points, geometry, segments, catalog, endpoint, normalizeSeries };
});
