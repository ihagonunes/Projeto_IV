// auth.js
// Login, persistência de token/sessão e guarda de rotas, conforme ADR-30.
// Autenticação: POST /api/v1/auth/login (tenantSlug + email + password) → JWT Bearer.
(function () {
    "use strict";

    var API = window.API;
    var STORAGE_USER = "qsoft.user";

    function getToken() {
        return API.getToken();
    }

    function setUser(user) {
        try { localStorage.setItem(STORAGE_USER, JSON.stringify(user || null)); } catch (e) { /* ignore */ }
    }

    function getUser() {
        try { return JSON.parse(localStorage.getItem(STORAGE_USER) || "null"); } catch (e) { return null; }
    }

    function isAuthenticated() {
        return !!getToken();
    }

    async function login(credentials) {
        var body = {
            email: (credentials && credentials.email) || "",
            password: (credentials && credentials.password) || ""
        };
        // tenantSlug só é enviado quando é conhecido no frontend (usuário logando em um tenant).
        var tenantSlug = credentials && credentials.tenantSlug;
        if (tenantSlug) {
            body.tenantSlug = tenantSlug;
        }

        var response = await API.post("/auth/login", body);

        // O contrato do backend retorna o JWT; aceita os nomes mais comuns para resiliência.
        var token = response && (response.accessToken || response.token || response.access_token);
        if (!token) {
            throw new Error("O servidor não retornou um token de acesso.");
        }

        API.setToken(token);
        setUser(response && response.user ? response.user : null);
        return response;
    }

    function logout() {
        API.clearToken();
        try { localStorage.removeItem(STORAGE_USER); } catch (e) { /* ignore */ }
        window.location.href = "/login";
    }

    function requireAuth() {
        if (!isAuthenticated()) {
            window.location.href = "/login";
            return false;
        }
        return true;
    }

    window.Auth = {
        login: login,
        logout: logout,
        getToken: getToken,
        getUser: getUser,
        isAuthenticated: isAuthenticated,
        requireAuth: requireAuth
    };
})();
