"use strict";
const layouts = require("./dashboards.layouts");
const { reason } = require("../../public/js/pages/dashboard-panels-core");
// SPI-92: composição visual, sem regras de negócio, dados ou autorização.
function render(req, res) {
    const profile = req.params.profile || res.locals.dashboardProfile || "colaborador";
    if (!Object.hasOwn(layouts, profile)) return res.sendStatus(404);
    return res.render("dashboard/views/index", {
        titulo: layouts[profile].title,
        bodyClass: "auth-bg dashboard-demo dashboard-layout",
        profile, panel: layouts[profile], indicatorReason: reason,
        canManageBudget: profile === "executivo" && res.locals.permissions?.has("SPENDING_BUDGET:MANAGE"),
        canManageSpending: profile === "executivo" && res.locals.permissions?.has("SPENDING_BUDGET:MANAGE"),
        canManageSpendingAlert: profile === "gestor" && res.locals.permissions?.has("SPENDING_ALERT:MANAGE"),
        demoReturn: profile === "colaborador" && process.env.NODE_ENV !== "production"
            && process.env.QSOFT_DASHBOARD_DEMO === "true"
    });
}
module.exports = { render };
