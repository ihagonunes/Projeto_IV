"use strict";
const layouts = require("./dashboards.layouts");
// SPI-92: composição visual, sem regras de negócio, dados ou autorização.
function render(req, res) {
    const profile = req.params.profile || res.locals.dashboardProfile || "colaborador";
    if (!Object.hasOwn(layouts, profile)) return res.sendStatus(404);
    return res.render("dashboard/views/index", {
        titulo: layouts[profile].title,
        bodyClass: "auth-bg dashboard-demo dashboard-layout",
        profile, panel: layouts[profile]
    });
}
module.exports = { render };
