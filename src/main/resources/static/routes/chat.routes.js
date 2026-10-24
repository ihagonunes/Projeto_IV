"use strict";
const express = require("express");

// A prévia é pública e contém exclusivamente fixtures locais, sem chamadas à API.
module.exports = function chatRoutes(requireSession) {
    const router = express.Router();
    const render = preview => (req, res) => res.render("chat/views/index", {
        titulo: "Chat de IA", bodyClass: "atlas-chat-page", preview
    });
    router.get("/chat/previa", render(true));
    router.get("/chat", requireSession, render(false));
    return router;
};
