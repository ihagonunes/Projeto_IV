const api = require("../../config/api");

/**
 * Controller do módulo IA.
 * Proxy puro para o Spring Boot — sem validação ou lógica de negócio.
 */

/** GET /api/ia → obtém configurações de IA do tenant */
async function obterConfig(req, res, next) {
    try {
        const response = await api.get("/ia", { params: req.query });
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** POST /api/ia → salva configurações de IA (credenciais e modelos) */
async function salvarConfig(req, res, next) {
    try {
        const response = await api.post("/ia", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { obterConfig, salvarConfig };
