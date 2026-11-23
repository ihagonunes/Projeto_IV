#!/usr/bin/env node
/**
 * Seed descartável para o E2E dos agentes (BE-07).
 *
 * Cria/garante provider STUB, modelo stub-echo, política do departamento e
 * orçamento do mês corrente. Usa apenas a API pública do backend; não grava
 * segredo no código nem imprime token/senha.
 *
 * Uso:
 *   E2E_ADMIN_EMAIL=... E2E_ADMIN_PASSWORD=... \
 *   E2E_EXECUTIVE_EMAIL=... E2E_EXECUTIVE_PASSWORD=... \
 *   node scripts/e2e/seed-agents-e2e.mjs [--dry-run]
 */
const BASE_URL = (process.env.E2E_BASE_URL || "http://127.0.0.1:8081").replace(/\/+$/, "");
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL || "";
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || "";
const EXECUTIVE_EMAIL = process.env.E2E_EXECUTIVE_EMAIL || "";
const EXECUTIVE_PASSWORD = process.env.E2E_EXECUTIVE_PASSWORD || "";
const DEPARTMENT_ID = process.env.E2E_DEPARTMENT_ID || "";
const DRY_RUN = process.argv.includes("--dry-run");

const PROVIDER_NAME = "e2e-stub";
const MODEL_NAME = "e2e-stub-echo";
const EXTERNAL_NAME = "stub-echo";

function month() {
    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit"
    }).format(new Date());
}

async function api(token, method, path, body) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${BASE_URL}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body)
    });
    const text = await response.text();
    let data = null;
    if (text) {
        try { data = JSON.parse(text); } catch { data = text; }
    }
    if (!response.ok) {
        const code = data && data.code ? ` (${data.code})` : "";
        throw new Error(`${method} ${path} falhou: HTTP ${response.status}${code}`);
    }
    return data;
}

async function login(email, password, label) {
    if (!email || !password) {
        throw new Error(`Informe ${label} (E2E_${label.toUpperCase()}_EMAIL/PASSWORD).`);
    }
    const data = await api(null, "POST", "/api/v1/auth/login", { email, password });
    if (!data || !data.token) throw new Error(`Login ${label} sem token.`);
    return data.token;
}

async function pickDepartment(adminToken) {
    if (DEPARTMENT_ID) return DEPARTMENT_ID;
    const departments = await api(adminToken, "GET", "/api/v1/departamentos");
    if (Array.isArray(departments) && departments.length) {
        return departments[0].id || departments[0].departmentId;
    }
    const created = await api(adminToken, "POST", "/api/v1/departamentos", {
        name: "E2E Agentes"
    });
    return created.id;
}

async function ensureProvider(adminToken) {
    const providers = await api(adminToken, "GET", "/api/v1/ai-gateway/providers");
    const existing = (providers || []).find(provider => provider.name === PROVIDER_NAME);
    if (existing) return existing.id;
    const created = await api(adminToken, "POST", "/api/v1/ai-gateway/providers", {
        name: PROVIDER_NAME,
        kind: "STUB"
    });
    return created.id;
}

async function ensureModel(adminToken, providerId) {
    const models = await api(adminToken, "GET", "/api/v1/ai-gateway/models");
    const existing = (models || []).find(model => model.name === MODEL_NAME);
    if (existing) return existing.id;
    const created = await api(adminToken, "POST", "/api/v1/ai-gateway/models", {
        providerId,
        name: MODEL_NAME,
        externalName: EXTERNAL_NAME,
        inputPricePer1k: 0.01,
        outputPricePer1k: 0.03
    });
    return created.id;
}

function spendingPlan(departmentId, modelId) {
    return {
        amount: 10000.00,
        departments: [{ departmentId, percentage: 100.00 }],
        allocations: [],
        weights: { COLLABORATOR: 1, LEADER: 1, MANAGER: 1, EXECUTIVE: 1 },
        rates: [{ modelId, currency: "BRL", exchangeRate: 1, reference: "E2E" }]
    };
}

async function main() {
    const targetMonth = month();
    if (DRY_RUN) {
        console.log(`[dry-run] base=${BASE_URL} mes=${targetMonth}`);
        console.log(`[dry-run] garante departamento, provider ${PROVIDER_NAME}, modelo ${MODEL_NAME}, política e orçamento.`);
        return;
    }

    const adminToken = await login(ADMIN_EMAIL, ADMIN_PASSWORD, "admin");
    const departmentId = await pickDepartment(adminToken);
    const providerId = await ensureProvider(adminToken);
    const modelId = await ensureModel(adminToken, providerId);
    await api(adminToken, "PUT", `/api/v1/ai-gateway/departments/${departmentId}/model-policy`, {
        allowedModelIds: [modelId],
        defaultModelId: modelId
    });

    const executiveToken = await login(
        EXECUTIVE_EMAIL || ADMIN_EMAIL, EXECUTIVE_PASSWORD || "", "executive");
    const suggestion = await api(executiveToken, "POST", "/api/v1/orcamento-ia/sugestao",
        spendingPlan(departmentId, modelId));
    await api(executiveToken, "PUT", `/api/v1/orcamento-ia/${targetMonth}`, suggestion);

    console.log(JSON.stringify({
        baseUrl: BASE_URL,
        month: targetMonth,
        departmentId,
        providerId,
        modelId
    }, null, 2));
}

main().catch(error => {
    console.error(`seed-agents-e2e: ${error.message}`);
    process.exit(1);
});
