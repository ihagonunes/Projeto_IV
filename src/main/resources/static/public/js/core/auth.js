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
        // Garante que nenhum token expirado seja enviado na requisição de login.
        // O api-client injeta o Bearer automaticamente se houver token em storage,
        // o que faz o Spring Security rejeitar a requisição com 401 antes de autenticar.
        API.clearToken();
        try { localStorage.removeItem(STORAGE_USER); } catch (e) { /* ignore */ }

        var body = {
            email: (credentials && credentials.email) || "",
            password: (credentials && credentials.password) || ""
        };
        // tenantSlug: do chamador (ex.: onboarding) ou do slug injetado pelo BFF
        // (window.QSOFT_TENANT_SLUG). O backend exige tenantSlug no login (ADR-30).
        var tenantSlug = (credentials && credentials.tenantSlug) || window.QSOFT_TENANT_SLUG || "manalistas";
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

    /**
     * Chamado quando qualquer requisição autenticada retorna 401 (token expirado).
     * Limpa a sessão local e redireciona para /sessao-expirada (SPI-66).
     * Não deve ser chamado em páginas pública (login, aceitar-convite).
     */
    function handleSessionExpired() {
        API.clearToken();
        try { localStorage.removeItem(STORAGE_USER); } catch (e) { /* ignore */ }
        window.location.href = "/sessao-expirada";
    }

    window.Auth = {
        login: login,
        logout: logout,
        getToken: getToken,
        getUser: getUser,
        isAuthenticated: isAuthenticated,
        requireAuth: requireAuth,
        handleSessionExpired: handleSessionExpired
    };
})();
