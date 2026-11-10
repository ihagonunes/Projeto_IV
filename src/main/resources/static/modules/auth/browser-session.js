"use strict";
const express = require("express");
const COOKIE = "qsoft_access";
const PROFILE_PATH = "/api/v1/auth/profiles";
const PROFILE_NAMES = new Map([
    ["TENANT_ADMIN", "administrador"], ["EXECUTIVE", "executivo"],
    ["MANAGER", "gestor"], ["LEADER", "lider"], ["COLLABORATOR", "colaborador"]
]);
const profileFor = access => {
    const roles = new Set((access.concessoes || []).map(grant => grant.papel));
    return [...PROFILE_NAMES].find(([role]) => roles.has(role))?.[1];
};
const profileCookie = profile => "qsoft_demo_" + profile;
const demoEnabled = req => process.env.NODE_ENV !== "production"
    && process.env.QSOFT_DASHBOARD_DEMO === "true"
    && ["127.0.0.1", "::1", "::ffff:127.0.0.1"].includes(req.socket.localAddress)
    && ["localhost", "127.0.0.1", "::1"].includes(req.hostname);
const lifetime = token => {
    const payload = JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8"));
    const remaining = payload.exp * 1000 - Date.now();
    if (!Number.isFinite(remaining) || remaining <= 0) throw new Error("Sessão vencida");
    return Math.min(remaining, 7 * 24 * 60 * 60 * 1000);
};

// O BFF transporta o JWT; assinatura, expiração, usuário e tenant são validados pelo Spring.
function createBrowserSession(api) {
    const router = express.Router();
    const cookieOptions = () => ({ httpOnly: true, sameSite: "strict", secure: process.env.NODE_ENV === "production", path: "/" });
    router.use((req, res, next) => { res.locals.demoProfilesEnabled = demoEnabled(req); next(); });
    const profileOptions = () => ({ ...cookieOptions(), path: PROFILE_PATH });
    const verify = async token => (await api.get("/usuarios/me/permissoes", {
        headers: { Authorization: `Bearer ${token}` }
    })).data;
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
        return verify(token);
    }
    async function requireSession(req, res, next) {
        try {
            const access = await identity(req);
            if (access) {
                res.locals.permissions = new Set(access.permissoes || []);
                res.locals.dashboardProfile = profileFor(access) || "colaborador";
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
            let maxAge = 30 * 60 * 1000;
            if (demoEnabled(req)) {
                const profile = profileFor(await verify(token));
                maxAge = lifetime(token);
                if (profile) res.cookie(profileCookie(profile), token, { ...profileOptions(), maxAge });
            }
            res.cookie(COOKIE, token, { ...cookieOptions(), maxAge });
            return res.json({ authenticated: true });
        } catch (error) {
            if (!demoEnabled(req)) clear(res);
            const status = error.response?.status;
            return res.status([400, 401, 403, 429].includes(status) ? status : 502)
                .json({ message: status === 429 ? "Muitas tentativas. Aguarde antes de tentar novamente." : "Não foi possível autenticar. Confira os dados e tente novamente." });
        }
    });
    router.get(PROFILE_PATH, (req, res) => {
        if (!demoEnabled(req)) return res.sendStatus(404);
        res.set("Cache-Control", "no-store");
        const active = req.cookies?.[COOKIE];
        const profiles = [...PROFILE_NAMES.values()].filter(profile => req.cookies?.[profileCookie(profile)])
            .map(profile => ({ profile, active: req.cookies[profileCookie(profile)] === active }));
        return res.json({ profiles });
    });
    router.post(PROFILE_PATH, protect, express.json({ limit: "1kb" }), async (req, res) => {
        if (!demoEnabled(req)) return res.sendStatus(404);
        res.set("Cache-Control", "no-store");
        const profile = req.body?.profile;
        if (![...PROFILE_NAMES.values()].includes(profile)) return res.sendStatus(400);
        const key = profileCookie(profile);
        const token = req.cookies?.[key];
        if (!token) return res.status(409).json({ message: "Entre nessa conta para salvá-la." });
        try {
            const access = await verify(token);
            if (profileFor(access) !== profile) {
                res.clearCookie(key, profileOptions());
                return res.status(409).json({ message: "O perfil mudou. Entre novamente nessa conta." });
            }
            res.cookie(COOKIE, token, { ...cookieOptions(), maxAge: lifetime(token) });
            return res.json({ destination: profile === "administrador" ? "/onboarding" : "/dashboard" });
        } catch (error) {
            if (![401, 403].includes(error.response?.status)) {
                return res.status(503).json({ message: "Não foi possível validar a conta. Tente novamente." });
            }
            res.clearCookie(key, profileOptions());
            return res.status(409).json({ message: "A sessão venceu. Entre novamente nessa conta." });
        }
    });
    router.post("/api/v1/auth/logout", protect, (req, res) => {
        clear(res);
        if (demoEnabled(req)) {
            for (const profile of PROFILE_NAMES.values()) res.clearCookie(profileCookie(profile), profileOptions());
        }
        res.set("Cache-Control", "no-store"); res.sendStatus(204);
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
