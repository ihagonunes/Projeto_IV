const axios = require("axios");

require("dotenv").config({ encoding: "utf8" });

/**
 * Instância centralizada do axios apontando para o back Spring Boot.
 * Toda chamada HTTP para o back deve usar esta instância.
 * A URL base é lida da variável de ambiente SPRING_API_URL.
 * Nenhuma rota ou controller deve hardcodar a URL do back.
 */
const api = axios.create({
    baseURL: process.env.SPRING_API_URL || "http://localhost:8080",
    headers: {
        "Content-Type": "application/json"
    },
    timeout: 10000
});

module.exports = api;
