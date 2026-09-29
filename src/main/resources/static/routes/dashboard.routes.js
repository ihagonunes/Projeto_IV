const express = require("express");
const wrap = require("express-async-error-wrapper");

const router = express.Router();

// ---------------------------------------------------------------------------
// Rotas de RENDER (paginas)
// ---------------------------------------------------------------------------

/**
 * GET /dashboard -> painel de uso e custo (SPI-92/93/95).
 *
 * A pagina apenas renderiza; os dados vem do backend via /api/v1/observability
 * (proxy same-origin do BFF). O escopo (proprio/departamento/tenant) e o filtro
 * por tenant sao aplicados no backend a partir do JWT (SPI-94, ADR-04/ADR-30).
 */
router.get("/dashboard", wrap(async (req, res) => {
    res.render("dashboard/views/index", {
        titulo: "Dashboard",
        bodyClass: "bg-light auth-bg"
    });
}));

module.exports = router;
