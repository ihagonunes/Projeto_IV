/* SPI-144: mapeamento puro das respostas de engajamento para os cards. */
(function (root, factory) {
    const core = factory();
    if (typeof module === "object" && module.exports) module.exports = core;
    else root.AtlasEngagement = core;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
    "use strict";
    const MISSING = "Sem dado";
    const QUADRANTS = [
        ["TREINADO_ATIVO", "Treinado e ativo"],
        ["TREINADO_INATIVO", "Treinado e inativo"],
        ["NAO_TREINADO_ATIVO", "Não treinado e ativo"],
        ["NAO_TREINADO_INATIVO", "Não treinado e inativo"]
    ];
    const DATA_STATUS = {
        COMPLETE: "Dados completos",
        PARTIAL: "Dados parciais",
        INSUFFICIENT_DATA: "Dados insuficientes"
    };
    function isMissing(value) {
        return value === null || value === undefined || value === "";
    }
    function numberLabel(value, missing = MISSING) {
        return isMissing(value) ? missing : Number(value).toLocaleString("pt-BR");
    }
    function percentLabel(value, missing = MISSING) {
        return isMissing(value) ? missing
            : `${Number(value).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
    }
    function dataStatusLabel(status) {
        return DATA_STATUS[status] || "Situação dos dados desconhecida";
    }
    function sourceLabel(sourceKind) {
        if (sourceKind === "HISTORICO") return "Histórico";
        if (sourceKind === "OPERACIONAL") return "Operacional";
        return "Fonte desconhecida";
    }
    function monthLabel(month) {
        return typeof month === "string" && /^\d{4}-\d{2}$/.test(month)
            ? `${month.slice(5, 7)}/${month.slice(0, 4)}` : MISSING;
    }
    function creditsStatusLabel(status) {
        if (status === "FECHADO") return "Fechado (snapshot imutável)";
        if (status === "PROVISORIO") return "Provisório (mês corrente)";
        return "Situação desconhecida";
    }
    function monthFromDate(date) {
        if (typeof date === "string" && /^\d{4}-\d{2}-\d{2}$/.test(date)) return date.slice(0, 7);
        const now = new Date();
        return `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
    }
    function coverageLabel(coverage) {
        if (!coverage) return "Cobertura indisponível.";
        return `Vínculos: ${numberLabel(coverage.eligibleLinked)} de ${numberLabel(coverage.eligibleTotal)} elegíveis; `
            + `status não mapeados: ${numberLabel(coverage.unmappedLicenseStatus)} de licença e `
            + `${numberLabel(coverage.unmappedTrainingStatus)} de treinamento.`;
    }
    function engagementQuery({ fonte, ate, departmentId } = {}) {
        const params = new URLSearchParams();
        if (fonte) params.set("fonte", fonte);
        if (ate) params.set("ate", ate);
        if (departmentId && !String(departmentId).startsWith("historical:")) {
            params.set("departmentId", departmentId);
        }
        return params.toString();
    }
    function creditsQuery({ fonte, mes, departmentId } = {}) {
        const params = new URLSearchParams();
        if (fonte) params.set("fonte", fonte);
        if (mes) params.set("mes", mes);
        if (departmentId && !String(departmentId).startsWith("historical:")) {
            params.set("departmentId", departmentId);
        }
        return params.toString();
    }
    function mapAdoption(report) {
        if (!report) return null;
        const eligible = Number(report.elegiveis);
        const hasBase = Number.isFinite(eligible) && eligible > 0;
        const insufficient = report.dataStatus === "INSUFFICIENT_DATA" || !hasBase;
        return {
            sourceKind: report.sourceKind,
            sourceLabel: sourceLabel(report.sourceKind),
            ate: report.ate || null,
            windowDays: isMissing(report.janelaDias) ? null : report.janelaDias,
            rateLabel: percentLabel(report.taxaAdocao),
            activeLabel: numberLabel(report.ativos),
            eligibleLabel: numberLabel(report.elegiveis),
            inactiveLabel: numberLabel(report.inativos),
            idleLicensesLabel: numberLabel(report.licencasOciosas),
            statusLabel: dataStatusLabel(report.dataStatus),
            insufficient,
            note: insufficient
                ? "Sem base suficiente para calcular a adoção. Valores ausentes não representam zero."
                : report.dataStatus === "PARTIAL"
                    ? "A fonte de licenças está incompleta; o indicador é parcial."
                    : "",
            donut: hasBase ? {
                active: Number(report.ativos) || 0,
                inactive: Number(report.inativos) || 0,
                rate: Math.max(0, Math.min(100, Number(report.taxaAdocao) || 0)),
                label: `${numberLabel(report.ativos)} ativos / ${numberLabel(report.inativos)} inativos`
            } : null,
            departments: (report.porDepartamento || []).map(item => ({
                id: item.departmentId || null,
                eligibleLabel: numberLabel(item.elegiveis),
                activeLabel: numberLabel(item.ativos),
                rateLabel: percentLabel(item.taxaAdocao)
            })),
            coverageLabel: coverageLabel(report.cobertura)
        };
    }
    function mapTraining(report) {
        if (!report) return null;
        const nominal = report.listaNominal === true;
        return {
            sourceKind: report.sourceKind,
            sourceLabel: sourceLabel(report.sourceKind),
            ate: report.ate || null,
            nominal,
            nominalNote: nominal
                ? "A lista nominal respeita USER:READ e o recorte do seu perfil."
                : "A lista nominal não está disponível para o seu perfil; apenas as contagens são exibidas.",
            quadrants: QUADRANTS.map(([key, label]) => {
                const quadrant = (report.quadrantes || {})[key] || {};
                return {
                    key,
                    label,
                    totalLabel: numberLabel(quadrant.total),
                    truncated: quadrant.truncado === true,
                    users: (quadrant.usuarios || []).map(user => ({
                        id: user.userId || null,
                        name: user.nome || "Usuário sem nome",
                        departmentId: user.departmentId || null
                    }))
                };
            }),
            coverageLabel: coverageLabel(report.cobertura)
        };
    }
    function averageComponent(items, key) {
        const values = items.map(item => item[key])
            .filter(value => value !== null && value !== undefined).map(Number);
        if (!values.length || values.length !== items.length) return null;
        return values.reduce((sum, value) => sum + value, 0) / values.length;
    }
    function averageLicense(items) {
        const applicable = items.filter(item => item.licenseApplicability !== "NOT_APPLICABLE");
        if (!applicable.length) return "Não aplicável";
        const values = applicable.map(item => item.license)
            .filter(value => value !== null && value !== undefined).map(Number);
        return values.length === applicable.length
            ? percentLabel(values.reduce((sum, value) => sum + value, 0) / values.length)
            : MISSING;
    }
    function mapCredits(report, personal) {
        if (!report) return null;
        const items = Array.isArray(report.itens) ? report.itens : [];
        const base = {
            personal: Boolean(personal),
            sourceLabel: sourceLabel(report.sourceKind),
            monthLabel: monthLabel(report.mes),
            statusLabel: creditsStatusLabel(report.status),
            version: report.calculationVersion || null,
            coverageLabel: coverageLabel(report.cobertura),
            note: items.length ? "" : "Sem dado de AI Credits para o mês selecionado."
        };
        if (personal) {
            const item = items[0] || null;
            return Object.assign({}, base, {
                item: item ? {
                    name: item.nome || "Meu AI Credits",
                    scoreLabel: percentLabel(item.score),
                    usageLabel: percentLabel(item.usage),
                    trainingLabel: percentLabel(item.training),
                    licenseLabel: item.licenseApplicability === "NOT_APPLICABLE"
                        ? "Não aplicável" : percentLabel(item.license),
                    licenseApplicability: item.licenseApplicability || null,
                    dataStatusLabel: dataStatusLabel(item.dataStatus)
                } : null,
                insufficient: !item || item.dataStatus === "INSUFFICIENT_DATA"
            });
        }
        const scores = items.map(item => item.score)
            .filter(value => value !== null && value !== undefined).map(Number);
        const buckets = [
            { label: "0 a 24,99", count: 0 },
            { label: "25 a 49,99", count: 0 },
            { label: "50 a 74,99", count: 0 },
            { label: "75 a 100", count: 0 }
        ];
        for (const score of scores) {
            if (score < 25) buckets[0].count += 1;
            else if (score < 50) buckets[1].count += 1;
            else if (score < 75) buckets[2].count += 1;
            else buckets[3].count += 1;
        }
        const average = scores.length
            ? scores.reduce((sum, value) => sum + value, 0) / scores.length : null;
        return Object.assign({}, base, {
            totalLabel: items.length ? numberLabel(items.length) : MISSING,
            averageLabel: percentLabel(average),
            insufficient: items.length === 0,
            partial: items.some(item => item.dataStatus !== "COMPLETE"),
            components: {
                usage: percentLabel(averageComponent(items, "usage")),
                training: percentLabel(averageComponent(items, "training")),
                license: averageLicense(items)
            },
            buckets: items.length ? buckets.map(bucket => ({
                label: bucket.label,
                count: bucket.count,
                countLabel: numberLabel(bucket.count),
                shareLabel: `${Math.round((bucket.count / items.length) * 100)}%`
            })) : []
        });
    }
    return {
        MISSING,
        QUADRANTS,
        numberLabel,
        percentLabel,
        dataStatusLabel,
        sourceLabel,
        monthLabel,
        monthFromDate,
        coverageLabel,
        engagementQuery,
        creditsQuery,
        mapAdoption,
        mapTraining,
        mapCredits
    };
});
