const api = require("../../config/api");

/**
 * Controller do módulo Tenant.
 * Proxy puro para o Spring Boot — sem validação ou lógica de negócio.
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

/** POST /api/tenant → cria um novo tenant */
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

/** PUT /api/tenant/me → atualiza os dados do tenant */
async function atualizar(req, res, next) {
    try {
        const response = await api.put("/tenants/me", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** GET /api/tenant/identidade-visual → busca identidade visual do tenant */
async function obterIdentidadeVisual(req, res, next) {
    try {
        const response = await api.get("/tenants/me/identidade-visual");
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** PUT /api/tenant/identidade-visual → atualiza identidade visual do tenant */
async function atualizarIdentidadeVisual(req, res, next) {
    try {
        const response = await api.put("/tenants/me/identidade-visual", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { listar, criar, obterAtual, atualizar, obterIdentidadeVisual, atualizarIdentidadeVisual };
