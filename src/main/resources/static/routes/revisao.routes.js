const express = require("express");
const wrap = require("express-async-error-wrapper");
const revisaoController = require("../modules/revisao/revisao.controller");

const router = express.Router();

// ---------------------------------------------------------------------------
// Rotas de RENDER (páginas)
// ---------------------------------------------------------------------------

/** GET /onboarding/revisao → step de revisão final do onboarding */
router.get("/onboarding/revisao", wrap(async (req, res) => {
    res.render("revisao/views/revisao", {
        titulo: "Revisão Final",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

// ---------------------------------------------------------------------------
// Rotas de API-PROXY (chamam o Spring Boot via controller)
// ---------------------------------------------------------------------------

/** GET /api/revisao → obtém resumo para revisão */
router.get("/api/revisao", wrap(revisaoController.obterResumo));

/** POST /api/revisao/confirmar → confirma e finaliza onboarding */
router.post("/api/revisao/confirmar", wrap(revisaoController.confirmar));

module.exports = router;
