const express = require("express");
const wrap = require("express-async-error-wrapper");
const departamentosController = require("../modules/departamentos/departamentos.controller");

const router = express.Router();

// ---------------------------------------------------------------------------
// Rotas de RENDER (páginas)
// ---------------------------------------------------------------------------

/** GET /onboarding/departamentos/hub → hub de escolha */
router.get("/onboarding/departamentos/hub", wrap(async (req, res) => {
    res.render("departamentos/views/hub", {
        titulo: "Departamentos",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

/** GET /onboarding/departamentos/form → formulário edição */
router.get("/onboarding/departamentos/form", wrap(async (req, res) => {
    res.render("departamentos/views/form", {
        titulo: "Editar Departamento",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

/** GET /onboarding/departamentos/listar → listagem */
router.get("/onboarding/departamentos/listar", wrap(async (req, res) => {
    res.render("departamentos/views/listar", {
        titulo: "Listar Departamentos",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

/** GET /onboarding/departamentos/add → adicionar novo */
router.get("/onboarding/departamentos/add", wrap(async (req, res) => {
    res.render("departamentos/views/add", {
        titulo: "Adicionar Departamento",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

/** GET /onboarding/departamentos/orcamentos → orçamento mensal (SPI-121) */
router.get("/onboarding/departamentos/orcamentos", wrap(async (req, res) => {
    res.render("departamentos/views/orcamentos", {
        titulo: "Orçamento por Departamento",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

// ---------------------------------------------------------------------------
// Rotas de API-PROXY (chamam o Spring Boot via controller)
// ---------------------------------------------------------------------------

/** GET /api/departamentos → lista */
router.get("/api/departamentos", wrap(departamentosController.listar));

/** POST /api/departamentos → cria */
router.post("/api/departamentos", wrap(departamentosController.criar));

/** PUT /api/departamentos/:id → atualiza */
router.put("/api/departamentos/:id", wrap(departamentosController.atualizar));

/** DELETE /api/departamentos/:id → remove */
router.delete("/api/departamentos/:id", wrap(departamentosController.remover));

module.exports = router;
