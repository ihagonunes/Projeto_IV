const express = require("express");
const wrap = require("express-async-error-wrapper");
const tenantController = require("../modules/tenant/tenant.controller");

const router = express.Router();

// ---------------------------------------------------------------------------
// Rotas de RENDER (páginas)
// ---------------------------------------------------------------------------


/** GET /tenant → renderiza o step de cadastro do tenant */
router.get("/tenant", wrap(async (req, res) => {
    res.render("tenant/views/tenant", {
        titulo: "Configuração da Empresa",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));



// ---------------------------------------------------------------------------
// Rotas de API-PROXY (chamam o Spring Boot via controller)
// ---------------------------------------------------------------------------

/** GET /api/tenant → lista tenants */
router.get("/api/tenant", wrap(tenantController.listar));

/** POST /api/tenant → cria novo tenant */
router.post("/api/tenant", wrap(tenantController.criar));

module.exports = router;
