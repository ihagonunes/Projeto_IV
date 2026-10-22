"use strict";
// Único ponto de substituição por API: no futuro utilizar ../../config/api (Axios),
// propagando o contexto autenticado pelo contrato definido no backend.
// Nunca usar fixtures como fallback silencioso para falhas de uma API real.
const mocks = require("./mocks/dashboards.mock");
async function getDashboard(selection) {
    return mocks.getDashboard(selection);
}
module.exports = { getDashboard };
