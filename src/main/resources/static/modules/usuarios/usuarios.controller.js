const api = require("../../config/api");

/**
 * Controller do módulo Usuarios.
 * Proxy puro para o Spring Boot — sem validação ou lógica de negócio.
 * Segue os paths do UserController.java (PATCH /api/v1/usuarios/...).
 */

/** GET /api/usuarios → lista usuários com filtros opcionais (SPI-62) */
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

/** PATCH /api/usuarios/:id → atualiza departamento, nível e permissão (SPI-63) */
async function atualizar(req, res, next) {
    try {
        const response = await api.patch(`/usuarios/${req.params.id}`, req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** PATCH /api/usuarios/:id/deactivate → desativa acesso do usuário (SPI-61) */
async function desativar(req, res, next) {
    try {
        const response = await api.patch(`/usuarios/${req.params.id}/deactivate`);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** PATCH /api/usuarios/:id/reactivate → reativa acesso do usuário (SPI-61) */
async function reativar(req, res, next) {
    try {
        const response = await api.patch(`/usuarios/${req.params.id}/reactivate`);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** POST /api/usuarios/:id/resend-invite → reenvia convite (SPI-59) */
async function reenviarConvite(req, res, next) {
    try {
        const response = await api.post(`/usuarios/${req.params.id}/resend-invite`);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { listar, criar, importar, atualizar, desativar, reativar, reenviarConvite };
