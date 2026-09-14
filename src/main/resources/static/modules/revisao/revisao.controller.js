const api = require("../../config/api");

/**
 * Controller do módulo Revisao.
 * Proxy puro para o Spring Boot — sem validação ou lógica de negócio.
 */

/** GET /api/revisao → obtém resumo do onboarding para revisão */
async function obterResumo(req, res, next) {
    try {
        const response = await api.get("/revisao");
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/** POST /api/revisao/confirmar → confirma e finaliza o onboarding */
async function confirmar(req, res, next) {
    try {
        const response = await api.post("/revisao/confirmar", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { obterResumo, confirmar };
