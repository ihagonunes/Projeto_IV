const express = require("express");
const wrap = require("express-async-error-wrapper");
const usuariosController = require("../modules/usuarios/usuarios.controller");

const router = express.Router();

// ---------------------------------------------------------------------------
// Rotas de RENDER (páginas)
// ---------------------------------------------------------------------------

/** GET /admin/usuarios → painel admin de gestão de usuários */
router.get("/admin/usuarios", wrap(async (req, res) => {
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

/** GET /api/usuarios → lista usuários */
router.get("/api/usuarios", wrap(usuariosController.listar));

/** POST /api/usuarios → cria usuário */
router.post("/api/usuarios", wrap(usuariosController.criar));

/** POST /api/usuarios/importar → importa planilha */
router.post("/api/usuarios/importar", wrap(usuariosController.importar));

/** PUT /api/usuarios/:id → atualiza nível/permissão */
router.put("/api/usuarios/:id", wrap(usuariosController.atualizar));

module.exports = router;
