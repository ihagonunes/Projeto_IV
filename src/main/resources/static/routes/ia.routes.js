const express = require("express");
const wrap = require("express-async-error-wrapper");
const iaController = require("../modules/ia/ia.controller");

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

/** GET /api/ia/provedores → lista provedores globais da plataforma */
router.get("/api/ia/provedores", wrap(iaController.listarProvedores));

/** GET /api/ia/modelos → lista catálogo de modelos de IA */
router.get("/api/ia/modelos", wrap(iaController.listarModelos));

/** PATCH /api/ia/modelos/:id/status → atualiza status Ativo/Inativo do modelo no banco */
router.patch("/api/ia/modelos/:id/status", wrap(iaController.atualizarStatusModelo));

module.exports = router;
