// api-client.js
// Wrapper padrão de Fetch para o backend Spring (Monólito Modular).
// Dois usos coexistem após o merge:
//   - window.ApiClient: usado pelos módulos do frontend (fornecido pela refatoração);
//   - window.API: usado pelo fluxo de autenticação (auth.js / login.js).
// Ambos usam cookie HttpOnly same-origin; nenhum JWT é persistido em storage.

// ---------------------------------------------------------------------------
// window.ApiClient — wrapper usado nos módulos (views/pages do frontend)
// ---------------------------------------------------------------------------
class ApiClient {
    static baseUrl = '/api';

    static async request(endpoint, options = {}) {
        const url = endpoint.startsWith('http') ? endpoint : `${this.baseUrl}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;

        const defaultHeaders = {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        };

        defaultHeaders['X-Qsoft-Request'] = '1';

        const config = {
            ...options,
            credentials: 'same-origin',
            headers: {
                ...defaultHeaders,
                ...options.headers
            }
        };

        try {
            const response = await fetch(url, config);

            let data = null;
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                data = await response.json();
            } else {
                data = await response.text();
            }

            if (!response.ok) {
                if (response.status === 401 && window.Auth && window.Auth.handleSessionExpired) {
                    window.Auth.handleSessionExpired();
                }
                const errorMessage = (data && data.message) || (data && data.error) || `Erro HTTP ${response.status}: ${response.statusText}`;
                const error = new Error(errorMessage);
                error.status = response.status;
                error.data = data;
                throw error;
            }

            return data;
        } catch (err) {
            console.warn(`[ApiClient] Falha na requisição para ${url}:`, err.message);
            throw err;
        }
    }

    static async get(endpoint, headers = {}) {
        return this.request(endpoint, { method: 'GET', headers });
    }

    static async post(endpoint, body = {}, headers = {}) {
        return this.request(endpoint, {
            method: 'POST',
            headers,
            body: JSON.stringify(body)
        });
    }

    static async put(endpoint, body = {}, headers = {}) {
        return this.request(endpoint, {
            method: 'PUT',
            headers,
            body: JSON.stringify(body)
        });
    }

    static async delete(endpoint, headers = {}) {
        return this.request(endpoint, { method: 'DELETE', headers });
    }
}

window.ApiClient = ApiClient;

// ---------------------------------------------------------------------------
// window.API — wrapper de fetch para o fluxo de autenticação (auth.js / login.js)
// Base /api/v1 (encaminhada pelo BFF em /api/*) e JWT Bearer (ADR-28/ADR-30).
// O Spring continua recebendo Bearer, traduzido pelo BFF a partir do cookie.
// ---------------------------------------------------------------------------
(function () {
    "use strict";

    var API_BASE_URL = (window.QSOFT_API_BASE_URL || "/api/v1").replace(/\/+$/, "");

    var STORAGE_TOKEN = "authToken";

    function getStoredToken() {
        return null; // JWT somente no cookie HttpOnly, nunca acessível ao JavaScript.
    }

    function setStoredToken(token) {
        clearStoredToken(); // Compatibilidade: não voltar a persistir JWT no navegador.
    }

    function clearStoredToken() {
        try { localStorage.removeItem(STORAGE_TOKEN); } catch (e) { /* ignore */ }
        try { sessionStorage.removeItem(STORAGE_TOKEN); } catch (e) { /* ignore */ }
    }

    async function request(path, options) {
        options = options || {};
        var method = options.method || "GET";
        var body = options.body;
        var extraHeaders = options.headers || {};

        var headers = { "Accept": "application/json", "X-Qsoft-Request": "1" };
        var token = getStoredToken();
        if (token) {
            headers["Authorization"] = "Bearer " + token;
        }
        Object.keys(extraHeaders).forEach(function (key) {
            headers[key] = extraHeaders[key];
        });

        var init = { method: method, headers: headers, credentials: "same-origin" };
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

    clearStoredToken(); // Remove tokens persistidos pela implementação anterior.
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
