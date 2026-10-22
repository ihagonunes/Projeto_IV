const express = require("express");
const wrap = require("express-async-error-wrapper");
const dashboards = require("../modules/dashboard/dashboards.controller");
const router = express.Router();

// Rotas de desenvolvimento: somente fixtures, sem acesso a dados reais.
// Não modifica a autenticação nem o dashboard integrado em /dashboard.
router.use("/dashboards", (req, res, next) => {
    if (process.env.NODE_ENV === "production") return res.sendStatus(404);
    next();
});
router.get("/dashboards", (req, res) => res.redirect("/dashboards/colaborador"));
router.get("/dashboards/:profile", wrap(dashboards.render));
module.exports = router;
