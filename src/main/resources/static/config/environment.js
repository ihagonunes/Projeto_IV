"use strict";

// BUGFIX-DB-001: seleção explícita não pode cair silenciosamente no ambiente antigo.
const selected = process.env.QSOFT_ENV_FILE;
const result = require("dotenv").config({ path: selected || ".env", encoding: "utf8", override: Boolean(selected) });
if (selected && result.error) throw new Error("Não foi possível carregar o arquivo de ambiente selecionado.");
module.exports = result;
