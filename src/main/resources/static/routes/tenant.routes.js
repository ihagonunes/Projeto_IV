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

/** GET /tenant/editar → renderiza a tela de edição contínua da empresa (SPI-52) */
router.get("/tenant/editar", wrap(async (req, res) => {
    res.render("tenant/views/editar-empresa", {
        titulo: "Editar Dados da Empresa",
        bodyClass: "bg-light auth-bg"
    });
}));

/** GET /tenant/identidade-visual → renderiza a tela de identidade visual / white-label (SPI-53) */
router.get("/tenant/identidade-visual", wrap(async (req, res) => {
    res.render("tenant/views/identidade-visual", {
        titulo: "Identidade Visual & Branding",
        bodyClass: "bg-light auth-bg"
    });
}));

// ---------------------------------------------------------------------------
// Rotas de API-PROXY (chamam o Spring Boot via controller)
// ---------------------------------------------------------------------------

/** GET /api/tenant → lista tenants */
router.get("/api/tenant", wrap(tenantController.listar));

/** POST /api/tenant → cria novo tenant (onboarding público) */
router.post("/api/tenant", wrap(tenantController.criar));

/** GET /api/tenant/me → busca tenant atual */
router.get("/api/tenant/me", wrap(tenantController.obterAtual));

/** PATCH /api/tenant/me → atualiza dados cadastrais do tenant (SPI-52) */
router.patch("/api/tenant/me", wrap(tenantController.atualizar));

/** GET /api/tenant/branding → busca identidade visual */
router.get("/api/tenant/branding", wrap(tenantController.obterBranding));

/** PATCH /api/tenant/branding → atualiza identidade visual (SPI-53) */
router.patch("/api/tenant/branding", wrap(tenantController.atualizarBranding));

/** PATCH /api/tenant/status → altera status ativo/inativo do tenant (SPI-57) */
router.patch("/api/tenant/status", wrap(tenantController.alterarStatus));

module.exports = router;
