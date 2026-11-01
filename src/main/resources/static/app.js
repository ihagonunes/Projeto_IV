//****************************************************************
// Logo ao abrir o projeto podem aparecer alguns erros nesse
// código, bem como nas dependências do Solution Explorer!
// É normal que isso ocorra, até que o processo de carregamento
// (exibido no canto inferior esquerdo) tenha avançado!
//****************************************************************

//****************************************************************
// Para utilizar o view engine ejs, adicionamos a linha
// "ejs": "^2.6.1", às dependências do arquivo package.json
// Isso significa que queremos a versão 2.6.1 do ejs, ou uma
// superior, mas compatível!
// https://docs.npmjs.com/files/package.json
//
// Depois de alterado o arquivo, é preciso clicar com o botão
// direito sobre a dependência, no Solution Explorer, e clicar
// na opção "Install Missing npm Package(s)"
//****************************************************************

const express = require("express");
const path = require("path");
const wrap = require("express-async-error-wrapper");
const cookieParser = require("cookie-parser"); // https://stackoverflow.com/a/16209531/3569421
const http = require("http");

require("./config/environment");

// Configura o cache, para armazenar as 200 últimas páginas
// já processadas, por ordem de uso
const ejs = require("ejs");

const LRU = require("lru-cache");
const cache = new LRU({
    max: 200
});

// Ver comentários no pacote https://github.com/tech-espm/labs-teem :)
if (!("remove" in cache)) {
    if (("delete" in cache))
        cache.remove = cache.delete;
    else if (("del" in cache))
        cache.remove = cache.delete;
}
if (!("reset" in cache)) {
    if (("clear" in cache))
        cache.reset = cache.clear;
}
ejs.cache = cache;

const app = express();

// Slug do tenant exposto ao frontend (login/onboarding) como global,
// injetado pelas views (window.QSOFT_TENANT_SLUG). Configurável via env.
app.locals.qsoftTenantSlug = process.env.QSOFT_TENANT_SLUG || "";

// Não queremos o header X-Powered-By
app.disable("x-powered-by");
// Não queremos o header ETag nas views
app.disable("etag");

// Explica para o express qual será o diretório de onde serviremos os
// arquivos estáticos (js, css, imagens etc...)
app.use("/public", express.static(path.join(__dirname, "public"), {
    cacheControl: true,
    etag: false,
    maxAge: "30d"
}));

app.use(cookieParser());
const browserSession = require("./modules/auth/browser-session").createBrowserSession(require("./config/api"));
app.use(browserSession.router);

// BFF: repassa chamadas /api/* para o backend Spring Boot (Monólito Modular).
// O frontend consome a API REST /api/v1 de forma same-origin, sem acesso direto a MYSQL.
// O target é configurável via API_PROXY_TARGET (padrão http://localhost:8080).
const API_PROXY_TARGET = new URL(process.env.API_PROXY_TARGET || "http://localhost:8080");

app.use("/api", (req, res) => {
    const headers = browserSession.headersFor(req);
    headers.host = API_PROXY_TARGET.host;
    const token = req.cookies[browserSession.cookieName];
    if (token && !['GET', 'HEAD', 'OPTIONS'].includes(req.method)) {
        let allowed = false;
        browserSession.protect(req, res, () => { allowed = true; });
        if (!allowed) return;
    }

    const proxyReq = http.request({
        hostname: API_PROXY_TARGET.hostname,
        port: API_PROXY_TARGET.port || 80,
        path: req.originalUrl,
        method: req.method,
        headers: headers
    }, (proxyRes) => {
        proxyRes.headers["cache-control"] = "no-store";
        // A allowlist CORS da API Bearer não autoriza leitura cross-origin do cookie do BFF.
        delete proxyRes.headers["access-control-allow-origin"];
        delete proxyRes.headers["access-control-allow-credentials"];
        if (proxyRes.statusCode === 401 && token) browserSession.clear(res);
        res.writeHead(proxyRes.statusCode, proxyRes.headers);
        proxyRes.pipe(res);
    });

    proxyReq.on("error", () => {
        if (res.headersSent) {
            res.end();
            return;
        }
        res.status(502).json({
            error: "Bad Gateway",
            message: "Não foi possível alcançar o backend da plataforma."
        });
    });

    req.pipe(proxyReq);
});

app.use(express.json({ limit: 10485760, inflate: true, strict: false })); // http://expressjs.com/en/api.html#express.json
app.use(express.urlencoded({ extended: true })); // http://expressjs.com/en/api.html#express.urlencoded

// Configura os diretórios de views: layout global + views de cada módulo.
// O Express procura o template primeiro em views/ e depois em modules/.
app.set("views", [
    path.join(__dirname, "views"),
    path.join(__dirname, "modules")
]);

// Define o view engine como o ejs e importa o express-ejs-layouts
// https://www.npmjs.com/package/express-ejs-layouts, pois o ejs não
// suporta layouts nativamente: https://www.npmjs.com/package/ejs#layouts
app.set("view engine", "ejs");
app.use(require("express-ejs-layouts"));

// Nosso middleware para evitar cache das páginas e api
// (deixa depois do static, pois os arquivos static devem usar cache e coisas)
app.use((req, res, next) => {
    res.header("Cache-Control", "private, no-cache, no-store, must-revalidate");
    res.header("Expires", "-1");
    res.header("Pragma", "no-cache");
    next();
});

// ---------------------------------------------------------------------------
// Módulos — cada módulo é autônomo (routes + controller + views).
// NENHUMA rota de API ou lógica de negócio deve ser adicionada aqui.
// Novas funcionalidades = novo arquivo em modules/ e um app.use abaixo.
// ---------------------------------------------------------------------------
app.get("/onboarding", browserSession.requireSession);
app.use("/", require("./routes/auth.routes"));
app.get(["/tenant", "/tenant/editar", "/tenant/identidade-visual"], browserSession.requireSession, browserSession.requireAdministration);
app.use("/", require("./routes/tenant.routes"));
app.use(["/convites", "/admin/convites"], browserSession.requireSession, browserSession.requirePermission("INVITE:MANAGE"));
app.use("/admin/usuarios", browserSession.requireSession, browserSession.requirePermission("USER:MANAGE"));
app.use("/onboarding/ia", browserSession.requireSession, browserSession.requirePermission("AI_TOOL:MANAGE"));
app.use(["/onboarding/admin", "/onboarding/departamentos", "/onboarding/usuarios", "/onboarding/revisao"],
    browserSession.requireSession, browserSession.requirePermission("TENANT:MANAGE"));
app.use("/", require("./routes/admin.routes"));
app.use("/", require("./routes/departamentos.routes"));
app.use("/", require("./routes/usuarios.routes"));
app.use("/", require("./routes/ia.routes"));
app.use("/", require("./routes/revisao.routes"));
app.use(["/dashboard", "/dashboard/:profile"], browserSession.requireSession, browserSession.requireDashboard);
app.use("/", require("./routes/dashboard.routes"));
// SPI-136: rotas de apresentação do chat; contratos Spring permanecem inalterados.
app.use("/", require("./routes/chat.routes")(browserSession.requireSession));


// Depois de registrados todos os caminhos das rotas e seus
// tratadores, registramos os tratadores que serão chamados
// caso nenhum dos tratadores anteriores tenha devolvido alguma
// resposta
//
// O Express diferencia um tratador regular (como esse aqui) de um tratador
// de erros, como os dois abaixo, pela quantidade de parâmetros!!!
//
// Isso é possível, porque em JavaScript, f.length retorna a quantidade
// de parâmetros declarados na função f!!!
//
// Registra os tratadores de erro
app.use((err, req, res, next) => {
    res.status(err.status || 500);

    res.render("erro", { mensagem: err.message });
});

// Persistência é responsabilidade do backend Spring Boot (Monólito Modular).
// O acesso a dados do frontend ocorre exclusivamente via API REST /api/v1 (proxy acima).
// Nenhum pool de conexão MySQL/PostgreSQL é criado neste processo Node.

const ip = process.env.IP || "127.0.0.1";
const porta = parseInt(process.env.PORT) || 3000;

const server = app.listen(porta, ip, () => {
    console.log(`Servidor Express executando em ${ip}:${porta}`);
});
