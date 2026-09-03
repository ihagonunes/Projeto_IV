const api = require("../../config/api");

/**
 * Controller do módulo Admin.
 * Proxy puro para o Spring Boot — sem validação ou lógica de negócio.
 */

/** GET /api/admin/convites → lista convites emitidos */
async function listarConvites(req, res, next) {
    try {
        const response = await api.get("/admin/convites", { params: req.query });
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** POST /api/admin/convites → emite novo convite */
async function criarConvite(req, res, next) {
    try {
        const response = await api.post("/admin/convites", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** DELETE /api/admin/convites/:id → revoga convite */
async function revogarConvite(req, res, next) {
    try {
        const response = await api.delete(`/admin/convites/${req.params.id}`);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { listarConvites, criarConvite, revogarConvite };
