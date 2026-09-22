const express = require("express");
const wrap = require("express-async-error-wrapper");
const usuariosController = require("../modules/usuarios/usuarios.controller");

const router = express.Router();

// ---------------------------------------------------------------------------
// Rotas de RENDER (páginas)
// ---------------------------------------------------------------------------

/** GET /admin/usuarios e /usuarios → painel admin de gestão de usuários */
router.get(["/usuarios", "/admin/usuarios"], wrap(async (req, res) => {
    res.render("usuarios/views/admin-usuarios", {
        titulo: "Gestão de Nível Hierárquico e Permissões - Administração",
        bodyClass: "auth-bg"
    });
}));

/** GET /onboarding/usuarios/hub → hub de cadastro de usuários */
router.get("/onboarding/usuarios/hub", wrap(async (req, res) => {
    res.render("usuarios/views/hub", {
        titulo: "Usuários",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

/** GET /onboarding/usuarios/importacao → importação via planilha */
router.get("/onboarding/usuarios/importacao", wrap(async (req, res) => {
    res.render("usuarios/views/importacao", {
        titulo: "Importação de Usuários",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

/** GET /onboarding/usuarios/manual → cadastro manual */
router.get("/onboarding/usuarios/manual", wrap(async (req, res) => {
    res.render("usuarios/views/manual", {
        titulo: "Cadastro Manual de Usuários",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

// ---------------------------------------------------------------------------
// Rotas de API-PROXY (chamam o Spring Boot via controller)
// ---------------------------------------------------------------------------

/** GET /api/usuarios → lista usuários com filtros opcionais (SPI-62) */
router.get("/api/usuarios", wrap(usuariosController.listar));

/** POST /api/usuarios → cria usuário */
router.post("/api/usuarios", wrap(usuariosController.criar));

/** POST /api/usuarios/importar → importa planilha */
router.post("/api/usuarios/importar", wrap(usuariosController.importar));

/** PATCH /api/usuarios/:id → atualiza departamento, nível e permissão (SPI-63) */
router.patch("/api/usuarios/:id", wrap(usuariosController.atualizar));

/** PATCH /api/usuarios/:id/deactivate → desativa acesso do usuário (SPI-61) */
router.patch("/api/usuarios/:id/deactivate", wrap(usuariosController.desativar));

/** PATCH /api/usuarios/:id/reactivate → reativa acesso do usuário (SPI-61) */
router.patch("/api/usuarios/:id/reactivate", wrap(usuariosController.reativar));

/** POST /api/usuarios/:id/resend-invite → reenvia convite (SPI-59) */
router.post("/api/usuarios/:id/resend-invite", wrap(usuariosController.reenviarConvite));

module.exports = router;
