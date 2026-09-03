const express = require("express");
const wrap = require("express-async-error-wrapper");
const iaController = require("./ia.controller");

const router = express.Router();

// ---------------------------------------------------------------------------
// Rotas de RENDER (páginas)
// ---------------------------------------------------------------------------

/** GET /onboarding/ia → step de configuração de IA */
router.get("/onboarding/ia", wrap(async (req, res) => {
    res.render("ia/views/ia", {
        titulo: "Configuração de IA",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

// ---------------------------------------------------------------------------
// Rotas de API-PROXY (chamam o Spring Boot via controller)
// ---------------------------------------------------------------------------

/** GET /api/ia → obtém configurações de IA */
router.get("/api/ia", wrap(iaController.obterConfig));

/** POST /api/ia → salva configurações de IA */
router.post("/api/ia", wrap(iaController.salvarConfig));

module.exports = router;
