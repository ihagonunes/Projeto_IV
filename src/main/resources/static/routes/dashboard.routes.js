const express = require("express");
const dashboards = require("../modules/dashboard/dashboards.controller");
const router = express.Router();
// SPI-92: apresentação apenas. O perfil da URL não concede acesso a dados.
router.get("/dashboard", dashboards.render);
router.get("/dashboard/:profile", dashboards.render);
module.exports = router;
