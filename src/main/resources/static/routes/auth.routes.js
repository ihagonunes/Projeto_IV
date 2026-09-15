const express = require("express");
const wrap = require("express-async-error-wrapper");
const authController = require("../modules/auth/auth.controller");

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

/** GET /onboarding → tela de boas-vindas ao onboarding */
router.get("/onboarding", wrap(async (req, res) => {
    res.render("auth/views/onboarding", {
        titulo: "Onboarding",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

/** GET /sessao-expirada → renderiza a tela de sessão expirada (SPI-66) */
router.get("/sessao-expirada", wrap(async (req, res) => {
    res.render("auth/views/sessao-expirada", {
        titulo: "Sessão Expirada",
        bodyClass: "bg-light auth-bg"
    });
}));

/** GET /aceitar-convite → tela pública de aceite de convite (SPI-30) */
router.get("/aceitar-convite", wrap(async (req, res) => {
    res.render("auth/views/aceite-convite", {
        titulo: "Aceitar Convite",
        bodyClass: "bg-light auth-bg",
        tenantNome: req.query.tenant || null
    });
}));

/** POST /api/auth/login → proxy para Spring Boot */
router.post("/api/auth/login", wrap(authController.login));

/** POST /api/auth/redefinir-senha → proxy para Spring Boot */
router.post("/api/auth/redefinir-senha", wrap(authController.redefinirSenha));

/** POST /api/auth/aceitar-convite → proxy para Spring Boot (SPI-30) */
router.post("/api/auth/aceitar-convite", wrap(authController.aceitarConvite));

module.exports = router;
