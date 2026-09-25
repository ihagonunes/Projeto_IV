const api = require("../../config/api");

/**
 * Controller do módulo Usuarios.
 * Proxy puro para o Spring Boot — sem validação ou lógica de negócio.
 */

/** GET /api/usuarios → lista usuários */
async function listar(req, res, next) {
    try {
        const response = await api.get("/usuarios", { params: req.query });
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** POST /api/usuarios → cria usuário manualmente */
async function criar(req, res, next) {
    try {
        const response = await api.post("/usuarios", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** POST /api/usuarios/importar → importa usuários via planilha */
async function importar(req, res, next) {
    try {
        const response = await api.post("/usuarios/importar", req.body, {
            headers: { "Content-Type": req.headers["content-type"] }
        });
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** PUT /api/usuarios/:id → atualiza nível/permissão do usuário */
async function atualizar(req, res, next) {
    try {
        const response = await api.patch(`/usuarios/${req.params.id}`, req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { listar, criar, importar, atualizar };
