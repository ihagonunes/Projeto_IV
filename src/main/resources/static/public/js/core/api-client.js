// api-client.js
// Wrapper de fetch para o backend Java (Spring Boot) — Monólito Modular.
// Consome a API REST /api/v1 (ADR-28) autenticada por JWT Bearer (ADR-30).
// Base URL same-origin, encaminhada pelo BFF Node em /api/* até o Spring.
(function () {
    "use strict";

    var API_BASE_URL = (window.QSOFT_API_BASE_URL || "/api/v1").replace(/\/+$/, "");

    var STORAGE_TOKEN = "qsoft.access_token";

    function getStoredToken() {
        try { return localStorage.getItem(STORAGE_TOKEN) || null; } catch (e) { return null; }
    }

    function setStoredToken(token) {
        try { localStorage.setItem(STORAGE_TOKEN, token); } catch (e) { /* quota/private mode */ }
    }

    function clearStoredToken() {
        try { localStorage.removeItem(STORAGE_TOKEN); } catch (e) { /* ignore */ }
    }

    async function request(path, options) {
        options = options || {};
        var method = options.method || "GET";
        var body = options.body;
        var extraHeaders = options.headers || {};

        var headers = { "Accept": "application/json" };
        var token = getStoredToken();
        if (token) {
            headers["Authorization"] = "Bearer " + token;
        }
        Object.keys(extraHeaders).forEach(function (key) {
            headers[key] = extraHeaders[key];
        });

        var init = { method: method, headers: headers };
        if (body !== undefined && body !== null) {
            headers["Content-Type"] = "application/json";
            init.body = JSON.stringify(body);
        }

        // Aceita path relativo (default) ou URL absoluta.
        var url = (path && /^https?:\/\//i.test(path)) ? path : (API_BASE_URL + (path || ""));

        var res = await fetch(url, init);

        var text = await res.text();
        var data = null;
        if (text) {
            try { data = JSON.parse(text); } catch (e) { data = text; }
        }

        if (!res.ok) {
            var error = new Error(
                (data && data.message) ? data.message : ("Requisição falhou (HTTP " + res.status + ").")
            );
            error.status = res.status;
            error.data = data;
            throw error;
        }

        return data;
    }

    window.API = {
        baseUrl: API_BASE_URL,
        getToken: getStoredToken,
        setToken: setStoredToken,
        clearToken: clearStoredToken,
        request: request,
        get: function (path, options) {
            return request(path, Object.assign({}, options, { method: "GET" }));
        },
        post: function (path, body, options) {
            return request(path, Object.assign({}, options, { method: "POST", body: body || {} }));
        },
        put: function (path, body, options) {
            return request(path, Object.assign({}, options, { method: "PUT", body: body || {} }));
        },
        patch: function (path, body, options) {
            return request(path, Object.assign({}, options, { method: "PATCH", body: body || {} }));
        },
        delete: function (path, options) {
            return request(path, Object.assign({}, options, { method: "DELETE" }));
        }
    };
})();
