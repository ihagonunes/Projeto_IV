const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const modulePath = path.resolve(__dirname, "../../main/resources/static/config/environment.js");

test("ambiente explícito substitui alvo herdado e é compartilhado pelo frontend", () => {
    const folder = fs.mkdtempSync(path.join(os.tmpdir(), "qsoft environment "));
    const file = path.join(folder, "chosen.env");
    try {
        fs.writeFileSync(file, "SPRING_API_URL=http://localhost:8081\nAPI_PROXY_TARGET=http://localhost:8081\n");
        const result = spawnSync(process.execPath, ["-e", `require(${JSON.stringify(modulePath)});console.log(process.env.SPRING_API_URL+'|'+process.env.API_PROXY_TARGET);`], {
            cwd: folder, env: { ...process.env, QSOFT_ENV_FILE: file, SPRING_API_URL: "http://localhost:8080" }, encoding: "utf8"
        });
        assert.equal(result.status, 0, result.stderr);
        assert.match(result.stdout, /http:\/\/localhost:8081\|http:\/\/localhost:8081/);
    } finally { fs.unlinkSync(file); fs.rmdirSync(folder); }
});

test("arquivo explícito ausente impede iniciar com configuração residual", () => {
    const result = spawnSync(process.execPath, ["-e", `require(${JSON.stringify(modulePath)});`], {
        env: { ...process.env, QSOFT_ENV_FILE: path.join(os.tmpdir(), "qsoft-file-that-does-not-exist.env") }, encoding: "utf8"
    });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Não foi possível carregar/);
});
