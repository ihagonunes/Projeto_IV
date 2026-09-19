const api = require("../../config/api");

/**
 * Controller do módulo Tenant.
 * Proxy puro para o Spring Boot — sem validação ou lógica de negócio.
 * Os endpoints seguem os mesmos paths do TenantController.java (PATCH /api/v1/tenants/me/...).
 */

/** GET /api/tenant → lista tenants */
async function listar(req, res, next) {
    try {
        const response = await api.get("/tenants", { params: req.query });
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** POST /api/tenant → cria um novo tenant (onboarding público) */
async function criar(req, res, next) {
    try {
        const response = await api.post("/tenants", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** GET /api/tenant/me → busca o tenant atual */
async function obterAtual(req, res, next) {
    try {
        const response = await api.get("/tenants/me");
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** PATCH /api/tenant/me → atualiza dados cadastrais do tenant (SPI-52) */
async function atualizar(req, res, next) {
    try {
        const response = await api.patch("/tenants/me", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** GET /api/tenant/branding → busca identidade visual do tenant (SPI-53) */
async function obterBranding(req, res, next) {
    try {
        const response = await api.get("/tenants/me");
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** PATCH /api/tenant/branding → atualiza identidade visual do tenant (SPI-53) */
async function atualizarBranding(req, res, next) {
    try {
        const response = await api.patch("/tenants/me/branding", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** PATCH /api/tenant/status → altera status ativo/inativo do tenant (SPI-57) */
async function alterarStatus(req, res, next) {
    try {
        const response = await api.patch("/tenants/me/status", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { listar, criar, obterAtual, atualizar, obterBranding, atualizarBranding, alterarStatus };
