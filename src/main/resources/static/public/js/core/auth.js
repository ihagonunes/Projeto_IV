// auth.js
// Login, persistência de token/sessão e guarda de rotas, conforme ADR-30.
// Login no BFF estabelece cookie HttpOnly; qsoft.session é apenas uma guarda visual.
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
        try { return localStorage.getItem("qsoft.session") === "1"; } catch (e) { return false; }
    }

    async function login(credentials) {
        // A rota de login do BFF nunca repassa o cookie anterior ao Spring.
        API.clearToken();
        try { localStorage.removeItem("qsoft.session"); } catch (e) { /* ignore */ }
        try { localStorage.removeItem(STORAGE_USER); } catch (e) { /* ignore */ }

        var body = {
            email: (credentials && credentials.email) || "",
            password: (credentials && credentials.password) || ""
        };
        // tenantSlug: do chamador (ex.: onboarding) ou do slug injetado pelo BFF
        // (window.QSOFT_TENANT_SLUG). Opcional conforme ADR-32.
        var tenantSlug = (credentials && credentials.tenantSlug) || window.QSOFT_TENANT_SLUG;
        if (tenantSlug) {
            body.tenantSlug = tenantSlug;
        }

        var response = await API.post("/auth/login", body);


        if (!response || !response.authenticated) throw new Error("A sessão não foi estabelecida.");
        try { localStorage.setItem("qsoft.session", "1"); } catch (e) { /* guarda visual; API valida cookie */ }
        setUser(response && response.user ? response.user : null);
        return response;
    }

    async function logout() {
        API.clearToken();
        try { localStorage.removeItem(STORAGE_USER); } catch (e) { /* ignore */ }
        try { sessionStorage.removeItem(STORAGE_USER); } catch (e) { /* ignore */ }
        try { localStorage.removeItem("qsoft.session"); } catch (e) { /* ignore */ }
        // Só redirecionar depois de o servidor expirar o cookie HttpOnly.
        await API.post("/auth/logout");
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
        try { localStorage.removeItem("qsoft.session"); } catch (e) { /* ignore */ }
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
