"use strict";
const express = require("express");
const COOKIE = "qsoft_access";

// O BFF transporta o JWT; assinatura, expiração, usuário e tenant são validados pelo Spring.
function createBrowserSession(api) {
    const router = express.Router();
    const cookieOptions = () => ({ httpOnly: true, sameSite: "strict", secure: process.env.NODE_ENV === "production", path: "/" });
    const clear = res => res.clearCookie(COOKIE, cookieOptions());
    function protect(req, res, next) {
        if (!['GET', 'HEAD', 'OPTIONS'].includes(req.method)) {
            const origin = req.get("origin");
            if (req.get("x-qsoft-request") !== "1" || req.get("sec-fetch-site") === "cross-site"
                    || (origin && origin !== (process.env.BFF_PUBLIC_ORIGIN || `${req.protocol}://${req.get("host")}`))) {
                return res.status(403).json({ message: "Origem da requisição não autorizada." });
            }
        }
        next();
    }
    function headersFor(req) {
        const headers = { ...req.headers };
        const token = req.cookies && req.cookies[COOKIE];
        if (token) headers.authorization = `Bearer ${token}`;
        delete headers.cookie;
        return headers;
    }
    async function identity(req) {
        const token = req.cookies && req.cookies[COOKIE];
        if (!token) return null;
        const response = await api.get("/usuarios/me/permissoes", { headers: { Authorization: `Bearer ${token}` } });
        return response.data;
    }
    async function requireSession(req, res, next) {
        try {
            const access = await identity(req);
            if (access) {
                const profiles = [["TENANT_ADMIN", "administrador"], ["EXECUTIVE", "executivo"],
                    ["MANAGER", "gestor"], ["LEADER", "lider"], ["COLLABORATOR", "colaborador"]];
                const roles = new Set((access.concessoes || []).map(grant => grant.papel));
                res.locals.permissions = new Set(access.permissoes || []);
                res.locals.dashboardProfile = profiles.find(([role]) => roles.has(role))?.[1] || "colaborador";
                return next();
            }
            clear(res); return res.redirect("/login");
        } catch (error) {
            if ([401, 403].includes(error.response?.status)) { clear(res); return res.redirect("/login"); }
            return res.status(503).send("Não foi possível validar a sessão. Tente novamente.");
        }
    }
    router.post(["/api/v1/auth/login", "/api/auth/login"], protect, express.json({ limit: "16kb" }), async (req, res) => {
        res.set("Cache-Control", "no-store");
        try {
            const response = await api.post("/auth/login", req.body);
            const token = response.data.token;
            if (!token) throw new Error("Resposta de autenticação inválida");
            res.cookie(COOKIE, token, { ...cookieOptions(), maxAge: 30 * 60 * 1000 });
            return res.json({ authenticated: true });
        } catch (error) {
            clear(res);
            const status = error.response?.status;
            return res.status([400, 401, 403, 429].includes(status) ? status : 502)
                .json({ message: status === 429 ? "Muitas tentativas. Aguarde antes de tentar novamente." : "Não foi possível autenticar. Confira os dados e tente novamente." });
        }
    });
    router.post("/api/v1/auth/logout", protect, (req, res) => {
        clear(res); res.set("Cache-Control", "no-store"); res.sendStatus(204);
    });
    router.get("/api/v1/auth/session", async (req, res) => {
        res.set("Cache-Control", "no-store");
        try {
            if (await identity(req)) return res.json({ authenticated: true });
            clear(res); return res.status(401).json({ authenticated: false });
        } catch (error) {
            if ([401, 403].includes(error.response?.status)) clear(res);
            return res.status([401, 403].includes(error.response?.status) ? 401 : 503).json({ authenticated: false });
        }
    });
    router.use((error, req, res, next) => {
        if (![400, 413].includes(error.status)) return next(error);
        res.set("Cache-Control", "no-store");
        return res.status(error.status).json({ message: "Requisição de autenticação inválida." });
    });
    function requireDashboard(req, res, next) {
        if (res.locals.dashboardProfile === "administrador") return res.redirect("/onboarding");
        return next();
    }
    function requireAdministration(req, res, next) {
        if (res.locals.dashboardProfile !== "administrador") return res.redirect("/onboarding");
        return next();
    }
    function requirePermission(permission) {
        return (req, res, next) => {
            if (res.locals.permissions && res.locals.permissions.has(permission)) return next();
            return res.redirect("/onboarding");
        };
    }
    return { router, protect, clear, requireSession, requireDashboard, requireAdministration, requirePermission, headersFor, cookieName: COOKIE };
}
module.exports = { createBrowserSession };
