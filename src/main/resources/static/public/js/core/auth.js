// auth.js
// Login, persistência de token/sessão e guarda de rotas, conforme ADR-30.
// Autenticação: POST /api/v1/auth/login (tenantSlug + email + password) → JWT Bearer.
(function () {
    "use strict";

    // Usa o ApiClient exposto por api-client.js (window.ApiClient).
    // O token é gerenciado internamente via localStorage/sessionStorage pelo ApiClient.
    var STORAGE_TOKEN = "authToken";
    var STORAGE_USER  = "qsoft.user";

    function getToken() {
        return localStorage.getItem(STORAGE_TOKEN) || sessionStorage.getItem(STORAGE_TOKEN) || null;
    }

    function setToken(token) {
        try { localStorage.setItem(STORAGE_TOKEN, token); } catch (e) { /* ignore */ }
    }

    function clearToken() {
        try {
            localStorage.removeItem(STORAGE_TOKEN);
            sessionStorage.removeItem(STORAGE_TOKEN);
        } catch (e) { /* ignore */ }
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
            email:    (credentials && credentials.email)    || "",
            password: (credentials && credentials.password) || ""
        };
        // tenantSlug: do chamador ou do slug injetado pelo BFF (window.QSOFT_TENANT_SLUG).
        // O backend exige tenantSlug no login (ADR-30).
        var tenantSlug = (credentials && credentials.tenantSlug) || window.QSOFT_TENANT_SLUG;
        if (tenantSlug) {
            body.tenantSlug = tenantSlug;
        }

        var response = await ApiClient.post("/auth/login", body);

        // O contrato do backend retorna o JWT; aceita os nomes mais comuns para resiliência.
        var token = response && (response.accessToken || response.token || response.access_token);
        if (!token) {
            throw new Error("O servidor não retornou um token de acesso.");
        }

        setToken(token);
        setUser(response && response.user ? response.user : null);
        return response;
    }

    function logout() {
        clearToken();
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
        login:           login,
        logout:          logout,
        getToken:        getToken,
        getUser:         getUser,
        isAuthenticated: isAuthenticated,
        requireAuth:     requireAuth
    };
})();