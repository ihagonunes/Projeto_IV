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

module.exports = { listar, criar };
