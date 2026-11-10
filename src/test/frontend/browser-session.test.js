const test = require("node:test");
const assert = require("node:assert/strict");
const express = require("express");
const cookieParser = require("cookie-parser");
const { createBrowserSession } = require("../../main/resources/static/modules/auth/browser-session");

async function fixture(run, access = {}) {
    const validated = [];
    const api = {
        async post(path, body) {
            assert.equal(path, "/auth/login");
            if (body.password !== "fixture") throw { response: { status: 401 } };
            return { data: { token: "signed-fixture-token", tokenType: "Bearer" } };
        },
        async get(path, config) {
            assert.equal(path, "/usuarios/me/permissoes");
            validated.push(config.headers.Authorization);
            if (config.headers.Authorization !== "Bearer signed-fixture-token") throw { response: { status: 401 } };
            return { data: access };
        }
    };
    const session = createBrowserSession(api), app = express();
    app.use(cookieParser()); app.use(session.router);
    app.get("/dashboard", session.requireSession, (req, res) => res.json({ profile: res.locals.dashboardProfile }));
    app.get("/admin/convites", session.requireSession, session.requirePermission("INVITE:MANAGE"), (req, res) => res.sendStatus(200));
    const server = app.listen(0, "127.0.0.1");
    await new Promise(resolve => server.once("listening", resolve));
    const url = `http://127.0.0.1:${server.address().port}`;
    const login = (extra = {}, password = "fixture") => fetch(url + "/api/v1/auth/login", {
        method: "POST", headers: { "content-type": "application/json", "x-qsoft-request": "1", ...extra },
        body: JSON.stringify({ email: "fixture@example.test", password }), redirect: "manual"
    });
    try { await run({ url, login, validated, session }); }
    finally { await new Promise(resolve => server.close(resolve)); }
}

test("login mantém JWT somente em cookie HttpOnly, Strict, 30min e sem cache", async () => fixture(async ({ login }) => {
    const response = await login();
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { authenticated: true });
    const cookie = response.headers.get("set-cookie");
    assert.match(cookie, /HttpOnly/); assert.match(cookie, /SameSite=Strict/);
    assert.match(cookie, /Max-Age=1800/); assert.match(cookie, /Path=\//);
    assert.equal(response.headers.get("cache-control"), "no-store");
}));

test("cookie em produção exige Secure", async () => {
    const original = process.env.NODE_ENV;
    try {
        process.env.NODE_ENV = "production";
        await fixture(async ({ login }) => assert.match((await login()).headers.get("set-cookie"), /; Secure/));
    } finally {
        if (original === undefined) delete process.env.NODE_ENV; else process.env.NODE_ENV = original;
    }
});

test("dashboard escolhe perfil pelas concessões atuais validadas no backend", async () => {
    for (const [papel, profile] of [["COLLABORATOR", "colaborador"], ["LEADER", "lider"],
        ["MANAGER", "gestor"], ["EXECUTIVE", "executivo"], ["TENANT_ADMIN", "administrador"]]) {
        await fixture(async ({ url }) => {
            const response = await fetch(url + "/dashboard?role=TENANT_ADMIN", {
                headers: { cookie: "qsoft_access=signed-fixture-token" }
            });
            assert.deepEqual(await response.json(), { profile });
        }, { concessoes: [{ papel: "COLLABORATOR" }, { papel }] });
    }
});

test("administrador não abre nenhum perfil de dashboard por URL direta", async () => {
    const session = createBrowserSession({});
    let destination, continued = false;
    session.requireDashboard({}, { locals: { dashboardProfile: 'administrador' }, redirect: value => destination = value }, () => continued = true);
    assert.equal(destination, '/onboarding');
    assert.equal(continued, false);
    session.requireDashboard({}, { locals: { dashboardProfile: 'executivo' } }, () => continued = true);
    assert.equal(continued, true);
});

test("shells administrativos exigem sessão e permissão específica", async () => {
    await fixture(async ({ url }) => {
        const response = await fetch(url + "/admin/convites", {
            headers: { cookie: "qsoft_access=signed-fixture-token" }, redirect: "manual"
        });
        assert.equal(response.status, 200);
    }, { permissoes: ["INVITE:MANAGE"], concessoes: [{ papel: "MANAGER" }] });

    await fixture(async ({ url }) => {
        let response = await fetch(url + "/admin/convites", {
            headers: { cookie: "qsoft_access=signed-fixture-token" }, redirect: "manual"
        });
        assert.equal(response.status, 302); assert.equal(response.headers.get("location"), "/onboarding");
        response = await fetch(url + "/admin/convites", { redirect: "manual" });
        assert.equal(response.status, 302); assert.equal(response.headers.get("location"), "/login");
    }, { permissoes: [], concessoes: [{ papel: "COLLABORATOR" }] });
});

test("cadastro e branding são páginas administrativas", () => {
    const session = createBrowserSession({});
    let destination, continued = false;
    session.requireAdministration({}, { locals: { dashboardProfile: 'executivo' }, redirect: value => destination = value }, () => continued = true);
    assert.equal(destination, '/onboarding');
    assert.equal(continued, false);
    session.requireAdministration({}, { locals: { dashboardProfile: 'administrador' } }, () => continued = true);
    assert.equal(continued, true);
});

test("cookie é conferido no backend e cookie adulterado não abre dashboard", async () => fixture(async ({ url, validated }) => {
    for (const cookie of ["", "qsoft_access=forged-token"]) {
        const response = await fetch(url + "/dashboard", { headers: { cookie }, redirect: "manual" });
        assert.equal(response.status, 302); assert.equal(response.headers.get("location"), "/login");
    }
    const response = await fetch(url + "/dashboard", { headers: { cookie: "qsoft_access=signed-fixture-token" } });
    assert.equal(response.status, 200);
    assert.ok(validated.includes("Bearer signed-fixture-token"));
}));

test("cookie tem precedência sobre Bearer enviado pelo cliente e não é repassado como Cookie", async () => fixture(async ({ session }) => {
    const headers = session.headersFor({ headers: { authorization: "Bearer another-user", cookie: "private" }, cookies: { qsoft_access: "signed-fixture-token" } });
    assert.equal(headers.authorization, "Bearer signed-fixture-token");
    assert.equal(headers.cookie, undefined);
}));

test("login e logout bloqueiam CSRF sem header ou com origem estranha", async () => fixture(async ({ url, login }) => {
    assert.equal((await login({ origin: "https://attacker.example" })).status, 403);
    assert.equal((await login({ "sec-fetch-site": "cross-site" })).status, 403);
    const logout = await fetch(url + "/api/v1/auth/logout", { method: "POST", headers: { cookie: "qsoft_access=signed-fixture-token" } });
    assert.equal(logout.status, 403);
}));

test("logout expira cookie e falha de login não retorna token", async () => fixture(async ({ url, login }) => {
    const bad = await login({}, "wrong");
    assert.equal(bad.status, 401); assert.ok(!(await bad.text()).includes("signed-fixture-token"));
    const response = await fetch(url + "/api/v1/auth/logout", { method: "POST", headers: { "x-qsoft-request": "1", cookie: "qsoft_access=signed-fixture-token" } });
    assert.equal(response.status, 204);
    assert.match(response.headers.get("set-cookie"), /qsoft_access=;/);
    assert.match(response.headers.get("set-cookie"), /Expires=Thu, 01 Jan 1970/);
}));

test("cliente remove JWT legado, autentica por cookie e não monta Bearer a partir de storage", async () => {
    const fs = require("node:fs"), path = require("node:path"), vm = require("node:vm");
    const values = new Map([["authToken", "old-token"]]);
    const local = { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
    const calls = [];
    const context = vm.createContext({ window: { location: {} }, localStorage: local, sessionStorage: { removeItem() {} },
        fetch: async (url, config) => { calls.push({ url, config }); return { ok: true, text: async () => '{"authenticated":true}' }; } });
    for (const file of ["api-client.js", "auth.js"]) vm.runInContext(fs.readFileSync(path.join(__dirname, "../../main/resources/static/public/js/core", file), "utf8"), context);
    assert.equal(values.has("authToken"), false);
    await context.window.Auth.login({ email: "fixture@example.test", password: "fixture" });
    assert.equal(context.window.Auth.isAuthenticated(), true);
    assert.equal(context.window.Auth.getToken(), null);
    assert.equal(values.has("authToken"), false);
    assert.equal(calls[0].config.credentials, "same-origin");
    assert.equal(calls[0].config.headers.Authorization, undefined);
    assert.equal(calls[0].config.headers["X-Qsoft-Request"], "1");
});

test("demo troca sessão apenas após revalidar a conta salva, sem devolver JWT", async () => {
    const previous = process.env.QSOFT_DASHBOARD_DEMO;
    process.env.QSOFT_DASHBOARD_DEMO = "true";
    const payload = Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 86400 })).toString("base64url");
    const token = "header." + payload + ".signature";
    let role = "COLLABORATOR";
    const api = {
        post: async () => ({ data: { token } }),
        get: async (_path, { headers }) => {
            assert.equal(headers.Authorization, "Bearer " + token);
            return { data: { concessoes: [{ papel: role }] } };
        }
    };
    const app = express();
    app.use(cookieParser(), createBrowserSession(api).router);
    const server = app.listen(0, "127.0.0.1");
    await new Promise(resolve => server.once("listening", resolve));
    const base = "http://127.0.0.1:" + server.address().port + "/api/v1/auth/";
    try {
        const headers = { "Content-Type": "application/json", "X-Qsoft-Request": "1" };
        const login = await fetch(base + "login", { method: "POST", headers, body: "{}" });
        const saved = login.headers.getSetCookie().find(cookie => cookie.startsWith("qsoft_demo_colaborador="));
        assert.match(saved, /Path=\/api\/v1\/auth\/profiles.*HttpOnly.*SameSite=Strict/);
        assert.deepEqual(await login.json(), { authenticated: true });
        const cookie = saved.split(";")[0];
        const list = await fetch(base + "profiles", { headers: { cookie } });
        assert.deepEqual(await list.json(), { profiles: [{ profile: "colaborador", active: false }] });
        const change = requestHeaders => fetch(base + "profiles", {
            method: "POST", headers: { ...requestHeaders, cookie },
            body: JSON.stringify({ profile: "colaborador" })
        });
        assert.equal((await change({ "Content-Type": "application/json" })).status, 403);
        const selected = await change(headers);
        assert.deepEqual(await selected.json(), { destination: "/dashboard" });
        assert.match(selected.headers.get("set-cookie"), /^qsoft_access=/);
        role = "MANAGER";
        const revoked = await change(headers);
        assert.equal(revoked.status, 409);
        assert.doesNotMatch(revoked.headers.get("set-cookie"), /qsoft_access=/);
    } finally {
        if (previous === undefined) delete process.env.QSOFT_DASHBOARD_DEMO;
        else process.env.QSOFT_DASHBOARD_DEMO = previous;
        await new Promise(resolve => server.close(resolve));
    }
});
