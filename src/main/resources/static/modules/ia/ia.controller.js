const api = require("../../config/api");

/**
 * Controller do módulo IA — BFF puro para o Spring Boot.
 *
 * Endpoints canônicos do Spring Boot (módulo aigateway):
 *   GET    /api/v1/ai-gateway/providers          → lista provedores globais
 *   GET    /api/v1/ai-gateway/models             → lista catálogo de modelos
 *   PATCH  /api/v1/ai-gateway/models/:id/status  → atualiza status do modelo
 *
 * Regra: sem lógica de negócio, sem validação, sem fallback fabricado.
 * Se o Spring Boot não responde, next(err) propaga o erro — nunca inventamos dados.
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

/**
 * GET /api/ia/provedores
 * Repassa para GET /api/v1/ai-gateway/providers no Spring Boot.
 * O módulo aigateway do Java é o único responsável por ler ai_gateway_providers no PostgreSQL.
 */
async function listarProvedores(req, res, next) {
    try {
        const response = await api.get("/ai-gateway/providers");
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/**
 * GET /api/ia/modelos
 * Repassa para GET /api/v1/ai-gateway/models no Spring Boot.
 * O módulo aigateway do Java é o único responsável por ler ai_gateway_models no PostgreSQL.
 */
async function listarModelos(req, res, next) {
    try {
        const response = await api.get("/ai-gateway/models");
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

/**
 * PATCH /api/ia/modelos/:id/status
 * Repassa para PATCH /api/v1/ai-gateway/models/:id/status no Spring Boot.
 * A gravação no banco (coluna status de ai_gateway_models) é feita exclusivamente pelo Java.
 */
async function atualizarStatusModelo(req, res, next) {
    try {
        const response = await api.patch(`/ai-gateway/models/${req.params.id}/status`, req.body);
        res.status(response.status).json(response.data);
    } catch (err) {
        next(err);
    }
}

module.exports = { obterConfig, salvarConfig, listarProvedores, listarModelos, atualizarStatusModelo };
