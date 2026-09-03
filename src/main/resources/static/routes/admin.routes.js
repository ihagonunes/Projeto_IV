const express = require("express");
const wrap = require("express-async-error-wrapper");
const adminController = require("../modules/admin/admin.controller");

const router = express.Router();

// ---------------------------------------------------------------------------
// Rotas de RENDER (páginas)
// ---------------------------------------------------------------------------

/** GET /admin/convites → renderiza a página de gestão de convites */
router.get("/admin/convites", wrap(async (req, res) => {
    res.render("admin/views/convites", {
        titulo: "Emissão de Convites - Administração",
        bodyClass: "bg-light auth-bg"
    });
}));

/** GET /onboarding/admin → renderiza o step do administrador principal */
router.get("/onboarding/admin", wrap(async (req, res) => {
    res.render("admin/views/step-admin", {
        titulo: "Administrador Principal",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

// ---------------------------------------------------------------------------
// Rotas de API-PROXY (chamam o Spring Boot via controller)
// ---------------------------------------------------------------------------

/** GET /api/admin/convites → lista convites */
router.get("/api/admin/convites", wrap(adminController.listarConvites));

/** POST /api/admin/convites → emite convite */
router.post("/api/admin/convites", wrap(adminController.criarConvite));

/** DELETE /api/admin/convites/:id → revoga convite */
router.delete("/api/admin/convites/:id", wrap(adminController.revogarConvite));

module.exports = router;
