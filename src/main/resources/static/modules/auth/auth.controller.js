const api = require("../../config/api");

/**
 * Controller do módulo Auth.
 * Toda chamada usa a instância centralizada do axios (config/api.js).
 * Nenhuma validação de regra de negócio aqui — apenas repasse ao Spring Boot.
 */

/**
 * POST /api/auth/login
 * Repassa credenciais para o Spring Boot e devolve a resposta ao front.
 */
async function login(req, res, next) {
    try {
        const response = await api.post("/auth/login", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/**
 * POST /api/auth/redefinir-senha
 * Repassa solicitação de redefinição de senha ao Spring Boot.
 */
async function redefinirSenha(req, res, next) {
    try {
        const response = await api.post("/auth/redefinir-senha", req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { login, redefinirSenha };
