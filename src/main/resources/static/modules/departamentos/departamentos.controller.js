const api = require("../../config/api");

/**
 * Controller do módulo Departamentos.
 * Proxy puro para o Spring Boot — sem validação ou lógica de negócio.
 */

/** GET /api/departamentos → lista departamentos */
async function listar(req, res, next) {
    try {
        const response = await api.get("/departamentos", { params: req.query });
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** POST /api/departamentos → cria departamento */
async function criar(req, res, next) {
    try {
        const response = await api.post("/departamentos", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** PUT /api/departamentos/:id → atualiza departamento */
async function atualizar(req, res, next) {
    try {
        const response = await api.put(`/departamentos/${req.params.id}`, req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** DELETE /api/departamentos/:id → remove departamento */
async function remover(req, res, next) {
    try {
        const response = await api.delete(`/departamentos/${req.params.id}`);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { listar, criar, atualizar, remover };
