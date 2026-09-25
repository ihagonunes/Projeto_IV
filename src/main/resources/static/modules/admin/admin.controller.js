const api = require("../../config/api");

/**
 * Controller do módulo Admin.
 * Proxy puro para o Spring Boot — sem validação ou lógica de negócio.
 */

/** GET /api/convites → lista convites emitidos */
async function listarConvites(req, res, next) {
    try {
        const response = await api.get("/convites", { params: req.query });
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** POST /api/convites → emite novo convite */
async function criarConvite(req, res, next) {
    try {
        const response = await api.post("/convites", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** DELETE /api/convites/:id → revoga convite */
async function revogarConvite(req, res, next) {
    try {
        const response = await api.delete(`/convites/${req.params.id}`);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { listarConvites, criarConvite, revogarConvite };
