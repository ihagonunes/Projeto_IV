const express = require("express");
const wrap = require("express-async-error-wrapper");
const authController = require("./auth.controller");

const router = express.Router();

// ---------------------------------------------------------------------------
// Rotas de RENDER (páginas)
// ---------------------------------------------------------------------------

/** Rota raiz → redireciona para /login */
router.get("/", wrap(async (req, res) => {
    res.redirect("/login");
}));

/** GET /login → renderiza a tela de login */
router.get("/login", wrap(async (req, res) => {
    res.render("auth/views/login", {
        titulo: "Login",
        bodyClass: "bg-light auth-bg"
    });
}));

/** GET /redefinir-senha → renderiza a tela de redefinição de senha */
router.get("/redefinir-senha", wrap(async (req, res) => {
    res.render("auth/views/redefinir-senha", {
        titulo: "Redefinir Senha",
        bodyClass: "bg-light auth-bg"
    });
}));

// ---------------------------------------------------------------------------
// Rotas de API-PROXY (chamam o Spring Boot via controller)
// ---------------------------------------------------------------------------

/** POST /api/auth/login → proxy para Spring Boot */
router.post("/api/auth/login", wrap(authController.login));

/** POST /api/auth/redefinir-senha → proxy para Spring Boot */
router.post("/api/auth/redefinir-senha", wrap(authController.redefinirSenha));

module.exports = router;
