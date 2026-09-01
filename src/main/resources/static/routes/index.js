const express = require("express");
const wrap = require("express-async-error-wrapper");
const router = express.Router();

// Root route redirects to login
router.get("/", wrap(async (req, res) => {
    res.redirect("/login");
}));

// Auth routes
router.get("/login", wrap(async (req, res) => {
    res.render("auth/login", {
        titulo: "Login",
        bodyClass: "bg-light auth-bg"
    });
}));

router.get("/redefinir-senha", wrap(async (req, res) => {
    res.render("auth/redefinir-senha", {
        titulo: "Redefinir Senha",
        bodyClass: "bg-light auth-bg"
    });
}));

// Onboarding route
router.get("/onboarding", wrap(async (req, res) => {
    res.render("onboarding/onboarding", {
        titulo: "Onboarding",
        bodyClass: "bg-light auth-bg onboarding-bg"
    });
}));

// Onboarding steps routes
const steps = [
    "step-tenant",
    "step-admin",
    "step-departamentos-hub",
    "step-departamentos-form",
    "step-departamentos-listar",
    "step-departamentos-add",
    "step-usuarios-hub",
    "step-usuarios-importacao",
    "step-usuarios-manual",
    "step-ia",
    "step-revisao"
];

steps.forEach(step => {
    router.get(`/onboarding/steps/${step}`, wrap(async (req, res) => {
        // Find formatted step name for title
        const stepName = step.replace("step-", "").replace("-", " ");
        const title = stepName.charAt(0).toUpperCase() + stepName.slice(1);
        res.render(`onboarding/steps/${step}`, {
            titulo: title,
            bodyClass: "bg-light auth-bg onboarding-bg"
        });
    }));
});

module.exports = router;
