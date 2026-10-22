"use strict";
const provider = require("./dashboards.provider");
const profiles = { colaborador: "Colaborador", lider: "Líder", gestor: "Gestor", executivo: "Executivo", agentes: "Análise de agentes" };
// Seletores de apresentação, não autorização. Não aceitamos tenant/user via query.
async function render(req, res, next) {
    const profile = req.params.profile;
    if (!Object.hasOwn(profiles, profile)) return res.sendStatus(404);
    const selection = {
        profile,
        period: ["semanal", "mensal"].includes(req.query.periodo) ? req.query.periodo : "semanal",
        state: ["normal", "vazio", "erro"].includes(req.query.estado) ? req.query.estado : "normal",
        agent: ["politicas-demo", "orcamento-demo"].includes(req.query.agente) ? req.query.agente : ""
    };
    try {
        const dashboard = await provider.getDashboard(selection);
        return res.render("dashboard/views/prototype", {
            titulo: profiles[profile], bodyClass: "auth-bg dashboard-demo",
            dashboard, selection, error: false,
            serialized: JSON.stringify(dashboard).replace(/</g, "\\u003c").replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029")
        });
    } catch (err) {
        if (!err.demo) return next(err);
        return res.status(503).render("dashboard/views/prototype", {
            titulo: profiles[profile], bodyClass: "auth-bg dashboard-demo",
            dashboard: null, selection, error: true, serialized: "null"
        });
    }
}
module.exports = { render };
