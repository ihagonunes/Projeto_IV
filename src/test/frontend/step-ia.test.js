"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const source = fs.readFileSync(path.join(__dirname,
    "../../main/resources/static/public/js/pages/onboarding/step-ia.js"), "utf8");
const tick = () => new Promise(resolve => setImmediate(resolve));
const settle = async () => { for (let i = 0; i < 8; i++) await tick(); };

function element(tag = "div") {
    const node = {
        tagName: tag, children: [], events: {}, dataset: {}, value: "", checked: false,
        hidden: false, disabled: false, textContent: "", className: "", type: "", min: "", step: "",
        appendChild(child) { this.children.push(child); return child; },
        append(...nodes) { this.children.push(...nodes); },
        replaceChildren(...nodes) { this.children = nodes; },
        addEventListener(event, handler) { this.events[event] = handler; },
        querySelector(selector) { return descendants(this, selector)[0] || null; },
        querySelectorAll(selector) { return descendants(this, selector); }
    };
    return node;
}
function descendants(node, selector, out = []) {
    for (const child of node.children || []) {
        if (matches(child, selector)) out.push(child);
        descendants(child, selector, out);
    }
    return out;
}
function matches(node, selector) {
    const field = /\[data-field="([^"]+)"\]/.exec(selector);
    if (field) return node.dataset && node.dataset.field === field[1];
    const action = /\[data-action="([^"]+)"\]/.exec(selector);
    if (action) return node.dataset && node.dataset.action === action[1];
    if (selector === 'input[type="checkbox"]:checked') {
        return node.tagName === "input" && node.type === "checkbox" && node.checked;
    }
    if (selector === 'input[type="checkbox"]') {
        return node.tagName === "input" && node.type === "checkbox";
    }
    return false;
}

const MODEL_ID = "model-1";
const DEPARTMENT_ID = "dep-1";
const PROVIDER_ID = "provider-1";
const provider = {
    id: PROVIDER_ID, name: "OpenCode Go", kind: "OPENAI_COMPATIBLE",
    baseUrl: "https://opencode.ai/v1", credentialRef: "OPENCODE_SESSION", status: "ACTIVE"
};
const model = {
    id: MODEL_ID, providerId: PROVIDER_ID, name: "modelo-x", externalName: "modelo-x-2026",
    status: "AVAILABLE", inputPricePer1k: "0.005", outputPricePer1k: "0.015"
};
const department = { id: DEPARTMENT_ID, name: "Marketing" };

async function page({ permissions = ["AI_TOOL:MANAGE"], policy, failOn = null } = {}) {
    const elements = new Map();
    const get = id => {
        if (!elements.has(id)) elements.set(id, element());
        return elements.get(id);
    };
    for (const id of ["iaMessage", "iaRestricted", "iaContent", "provedoresTableBody",
        "modelosTableBody", "modelsMessage", "policyDepartment", "policyDefault",
        "policyModels", "policySave", "policyMessage"]) {
        get(id);
    }
    const calls = [];
    const session = { value: null, setItem(key, value) { this.value = { key, value }; } };
    const api = {
        async get(url) {
            calls.push({ method: "GET", url });
            if (failOn && url === failOn) {
                throw Object.assign(new Error("falha"), { status: 500, data: { message: "Falha controlada." } });
            }
            if (url === "/v1/usuarios/me/permissoes") return { permissoes: permissions };
            if (url === "/v1/ai-gateway/providers") return [provider];
            if (url === "/v1/ai-gateway/models") return [model];
            if (url === "/v1/departamentos") return [department];
            if (url === `/v1/ai-gateway/departments/${DEPARTMENT_ID}/model-policy`) {
                return policy || { departmentId: DEPARTMENT_ID, defaultModelId: null, allowedModelIds: [] };
            }
            throw new Error(`URL inesperada: ${url}`);
        },
        async patch(url, body) {
            calls.push({ method: "PATCH", url, body });
            return Object.assign({}, model, { status: body.status });
        },
        async put(url, body) {
            calls.push({ method: "PUT", url, body });
            if (url.includes("/model-policy")) return {};
            return Object.assign({}, model, body);
        }
    };
    const document = {
        getElementById: get,
        createElement: tag => element(tag),
        createTextNode: text => ({ textContent: text })
    };
    vm.runInNewContext(source, {
        document, window: { ApiClient: api }, sessionStorage: session, console, Number, Array,
        Map, Object, String, encodeURIComponent
    });
    await settle();
    return { get, calls, session, elements };
}
function rowByModel(body) {
    return body.children.find(row => row.dataset && row.dataset.modelId === MODEL_ID);
}

test("catálogo: lista providers, modelos e política usando somente ai-gateway", async () => {
    const p = await page({ policy: {
        departmentId: DEPARTMENT_ID, defaultModelId: MODEL_ID, allowedModelIds: [MODEL_ID]
    } });
    assert.ok(p.calls.some(call => call.url === "/v1/ai-gateway/providers"));
    assert.ok(p.calls.some(call => call.url === "/v1/ai-gateway/models"));
    assert.ok(p.calls.some(call => call.url === `/v1/ai-gateway/departments/${DEPARTMENT_ID}/model-policy`));
    assert.ok(!p.calls.some(call => call.url.includes("/api/v1/ia/")));
    assert.equal(p.get("iaContent").hidden, false);

    const providerRow = p.get("provedoresTableBody").children[0];
    assert.equal(providerRow.children[0].textContent, "OpenCode Go");
    assert.equal(providerRow.children[3].textContent, "Variável: OPENCODE_SESSION");
    assert.ok(!JSON.stringify(providerRow).includes("secret"));

    const modelRow = rowByModel(p.get("modelosTableBody"));
    assert.ok(modelRow);
    assert.equal(modelRow.children[1].textContent, "OpenCode Go");
    assert.equal(modelRow.children[2].textContent, "Marketing");
    assert.equal(modelRow.querySelector('[data-field="status"]').value, "AVAILABLE");
    assert.equal(modelRow.querySelector('[data-field="inputPrice"]').value, "0.005");
    assert.equal(modelRow.querySelector('[data-field="outputPrice"]').value, "0.015");

    const checkbox = p.get("policyModels").querySelectorAll('input[type="checkbox"]')[0];
    assert.equal(checkbox.checked, true);
    assert.equal(p.get("policyDefault").value, MODEL_ID);
    assert.ok(p.session.value && p.session.value.key === "onboarding_ia");
});

test("catálogo: altera status e salva preço/nome externo pela API", async () => {
    const p = await page({ policy: {
        departmentId: DEPARTMENT_ID, defaultModelId: MODEL_ID, allowedModelIds: [MODEL_ID]
    } });
    let row = rowByModel(p.get("modelosTableBody"));
    const status = row.querySelector('[data-field="status"]');
    status.value = "DISABLED";
    await status.events.change();
    const patch = p.calls.find(call => call.method === "PATCH");
    assert.equal(patch.url, `/v1/ai-gateway/models/${MODEL_ID}/status`);
    assert.deepEqual(JSON.parse(JSON.stringify(patch.body)), { status: "DISABLED" });

    row = rowByModel(p.get("modelosTableBody"));
    row.querySelector('[data-field="name"]').value = "modelo-editado";
    row.querySelector('[data-field="externalName"]').value = "modelo-editado-2026";
    row.querySelector('[data-field="inputPrice"]').value = "0.010";
    row.querySelector('[data-field="outputPrice"]').value = "0.020";
    await row.querySelector('[data-action="save"]').events.click();
    const put = p.calls.find(call => call.method === "PUT" && call.url === `/v1/ai-gateway/models/${MODEL_ID}`);
    assert.deepEqual(JSON.parse(JSON.stringify(put.body)), {
        name: "modelo-editado", externalName: "modelo-editado-2026",
        inputPricePer1k: 0.010, outputPricePer1k: 0.020
    });
});

test("catálogo: salva política por departamento apenas com padrão entre permitidos", async () => {
    const p = await page();
    const checkbox = p.get("policyModels").querySelectorAll('input[type="checkbox"]')[0];
    checkbox.checked = true;
    checkbox.events.change();
    p.get("policyDefault").value = MODEL_ID;
    await p.get("policySave").events.click();
    const put = p.calls.find(call => call.method === "PUT" && call.url.includes("/model-policy"));
    assert.equal(put.url, `/v1/ai-gateway/departments/${DEPARTMENT_ID}/model-policy`);
    assert.deepEqual(JSON.parse(JSON.stringify(put.body)), { allowedModelIds: [MODEL_ID], defaultModelId: MODEL_ID });
});

test("catálogo: erro da API mostra mensagem e não cria dados fictícios", async () => {
    const p = await page({ failOn: "/v1/ai-gateway/models" });
    assert.equal(p.get("iaContent").hidden, true);
    assert.match(p.get("iaMessage").textContent, /Falha controlada|Não foi possível carregar/);
    assert.equal(p.get("modelosTableBody").children.length, 0);
    assert.ok(!p.calls.some(call => call.url.includes("/api/v1/ia/")));
});

test("catálogo: sem AI_TOOL:MANAGE mostra acesso restrito e não consulta o catálogo", async () => {
    const p = await page({ permissions: [] });
    assert.equal(p.get("iaRestricted").hidden, false);
    assert.equal(p.get("iaContent").hidden, true);
    assert.ok(!p.calls.some(call => call.url === "/v1/ai-gateway/providers"));
});

test("tela do cat�logo n�o pede segredo, n�o usa fallback fict�cio e exp�e os controles reais", () => {
    const view = fs.readFileSync(path.join(__dirname,
        "../../main/resources/static/modules/ia/views/ia.ejs"), "utf8");
    assert.ok(!/type="password"/.test(view));
    assert.ok(!/id="apiKey"/.test(view));
    assert.ok(!/localStorage/.test(view));
    assert.match(view, /id="provedoresTableBody"/);
    assert.match(view, /id="modelosTableBody"/);
    assert.match(view, /id="policyDepartment"/);
    assert.match(view, /id="policyModels"/);
    assert.ok(!/provedoresBancoDB|modelosBancoDB|localStorage/.test(source));
    assert.match(source, /\/v1\/ai-gateway\/providers/);
    assert.match(source, /\/v1\/ai-gateway\/models/);
    assert.match(source, /\/model-policy/);
});

test("view /onboarding/ia renderiza o cat�logo real e n�o cont�m campos de segredo", async () => {
    const express = require("express");
    const layouts = require("express-ejs-layouts");
    const base = path.resolve(__dirname, "../../main/resources/static");
    const app = express();
    app.set("views", [path.join(base, "views"), path.join(base, "modules")]);
    app.set("view engine", "ejs");
    app.use(layouts);
    app.use(require(path.join(base, "routes/ia.routes")));
    const server = app.listen(0, "127.0.0.1");
    await new Promise(resolve => server.once("listening", resolve));
    const url = "http://127.0.0.1:" + server.address().port;
    try {
        const response = await fetch(url + "/onboarding/ia");
        assert.equal(response.status, 200);
        const html = await response.text();
        assert.match(html, /id="provedoresTableBody"/);
        assert.match(html, /id="modelosTableBody"/);
        assert.match(html, /id="policyDepartment"/);
        assert.ok(!/type="password"/.test(html));
        assert.ok(!/id="apiKey"/.test(html));
    } finally {
        await new Promise(resolve => server.close(resolve));
    }
});
