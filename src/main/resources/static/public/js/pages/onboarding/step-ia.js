/* SPI-87: catálogo administrativo real; sem dados fictícios e sem segredo. */
(function () {
    "use strict";
    const client = window.ApiClient;
    const message = document.getElementById("iaMessage");
    const restricted = document.getElementById("iaRestricted");
    const content = document.getElementById("iaContent");
    const providersBody = document.getElementById("provedoresTableBody");
    const modelsBody = document.getElementById("modelosTableBody");
    const modelsMessage = document.getElementById("modelsMessage");
    const policyDepartment = document.getElementById("policyDepartment");
    const policyDefault = document.getElementById("policyDefault");
    const policyModels = document.getElementById("policyModels");
    const policySave = document.getElementById("policySave");
    const policyMessage = document.getElementById("policyMessage");
    if (!providersBody || !modelsBody || !policyDepartment) return;
    if (!client) {
        setMessage("Cliente HTTP indisponível. Recarregue a página.", "danger");
        return;
    }

    let providers = [];
    let models = [];
    let departments = [];
    const policies = new Map();
    let selectedDepartment = null;

    function setMessage(text, kind) {
        if (!message) return;
        message.textContent = text || "";
        message.className = text ? `atlas-form-message text-${kind || "muted"}` : "atlas-form-message";
    }
    function showRestricted() {
        if (restricted) restricted.hidden = false;
        if (content) content.hidden = true;
        setMessage("", "muted");
    }
    function errorMessage(error, fallback) {
        return (error && error.data && error.data.message) || fallback;
    }
    function option(value, label) {
        const item = document.createElement("option");
        item.value = value;
        item.textContent = label;
        return item;
    }
    function cell(value) {
        const item = document.createElement("td");
        item.textContent = value == null || value === "" ? "—" : value;
        return item;
    }
    function input(value, type, field, className) {
        const item = document.createElement("input");
        item.type = type;
        item.value = value == null ? "" : value;
        item.className = className || "form-control custom-input form-control-sm";
        item.dataset.field = field;
        if (type === "number") {
            item.min = "0";
            item.step = "0.000001";
        }
        return item;
    }
    function providerName(id) {
        const provider = providers.find(item => item.id === id);
        return provider ? provider.name : "Provider não encontrado";
    }
    function authorizedDepartments(modelId) {
        const names = departments
            .filter(department => {
                const policy = policies.get(department.id);
                return policy && (policy.allowedModelIds || []).includes(modelId);
            })
            .map(department => department.name);
        return names.length ? names.join(", ") : "Nenhum";
    }
    function renderProviders() {
        providersBody.replaceChildren();
        if (!providers.length) {
            const row = document.createElement("tr");
            const empty = cell("Nenhum provedor cadastrado.");
            empty.colSpan = 5;
            row.appendChild(empty);
            providersBody.appendChild(row);
            return;
        }
        for (const provider of providers) {
            const row = document.createElement("tr");
            row.appendChild(cell(provider.name));
            row.appendChild(cell(provider.kind || "STUB"));
            row.appendChild(cell(provider.baseUrl || "—"));
            row.appendChild(cell(provider.credentialRef
                ? `Variável: ${provider.credentialRef}` : "Sem credencial"));
            row.appendChild(cell(provider.status));
            providersBody.appendChild(row);
        }
    }
    async function changeStatus(model, select) {
        const previous = model.status;
        select.disabled = true;
        try {
            const updated = await client.patch(
                `/v1/ai-gateway/models/${encodeURIComponent(model.id)}/status`,
                { status: select.value });
            model.status = updated.status;
            select.value = model.status;
            modelsMessage.textContent = `Status de ${model.name} atualizado para ${model.status}.`;
        } catch (error) {
            select.value = previous;
            modelsMessage.textContent = errorMessage(error,
                "Não foi possível alterar o status do modelo.");
        } finally {
            select.disabled = false;
        }
    }
    function readPrice(inputElement, available) {
        const raw = inputElement.value.trim();
        if (!raw) {
            if (available) throw new Error("Preço de entrada e de saída são obrigatórios para modelo disponível.");
            return null;
        }
        const value = Number(raw);
        if (!Number.isFinite(value) || value < 0) {
            throw new Error("O preço não pode ser negativo.");
        }
        return value;
    }
    async function saveModel(model, row) {
        const nameInput = row.querySelector('[data-field="name"]');
        const externalInput = row.querySelector('[data-field="externalName"]');
        const inputPrice = row.querySelector('[data-field="inputPrice"]');
        const outputPrice = row.querySelector('[data-field="outputPrice"]');
        const save = row.querySelector('[data-action="save"]');
        let body;
        try {
            body = {
                name: nameInput.value.trim(),
                externalName: externalInput.value.trim() || null,
                inputPricePer1k: readPrice(inputPrice, model.status === "AVAILABLE"),
                outputPricePer1k: readPrice(outputPrice, model.status === "AVAILABLE")
            };
            if (!body.name) throw new Error("O nome do modelo é obrigatório.");
        } catch (error) {
            modelsMessage.textContent = error.message;
            return;
        }
        save.disabled = true;
        try {
            const updated = await client.put(
                `/v1/ai-gateway/models/${encodeURIComponent(model.id)}`, body);
            Object.assign(model, updated);
            modelsMessage.textContent = `Modelo ${model.name} salvo.`;
            renderModels();
        } catch (error) {
            modelsMessage.textContent = errorMessage(error,
                "Não foi possível salvar o modelo.");
        } finally {
            save.disabled = false;
        }
    }
    function renderModels() {
        modelsBody.replaceChildren();
        if (!models.length) {
            const row = document.createElement("tr");
            const empty = cell("Nenhum modelo cadastrado.");
            empty.colSpan = 8;
            row.appendChild(empty);
            modelsBody.appendChild(row);
            return;
        }
        for (const model of models) {
            const row = document.createElement("tr");
            row.dataset.modelId = model.id;

            const nameCell = document.createElement("td");
            nameCell.appendChild(input(model.name, "text", "name"));
            row.appendChild(nameCell);
            row.appendChild(cell(providerName(model.providerId)));
            row.appendChild(cell(authorizedDepartments(model.id)));

            const statusCell = document.createElement("td");
            const status = document.createElement("select");
            status.className = "form-select custom-input form-select-sm";
            status.dataset.field = "status";
            status.appendChild(option("AVAILABLE", "Disponível"));
            status.appendChild(option("DISABLED", "Desativado"));
            status.value = model.status;
            status.addEventListener("change", () => changeStatus(model, status));
            statusCell.appendChild(status);
            row.appendChild(statusCell);

            const externalCell = document.createElement("td");
            externalCell.appendChild(input(model.externalName, "text", "externalName"));
            row.appendChild(externalCell);

            const inputPriceCell = document.createElement("td");
            inputPriceCell.appendChild(input(model.inputPricePer1k, "number", "inputPrice"));
            row.appendChild(inputPriceCell);
            const outputPriceCell = document.createElement("td");
            outputPriceCell.appendChild(input(model.outputPricePer1k, "number", "outputPrice"));
            row.appendChild(outputPriceCell);

            const actionCell = document.createElement("td");
            const save = document.createElement("button");
            save.type = "button";
            save.className = "btn btn-orange btn-pill btn-sm";
            save.textContent = "Salvar";
            save.dataset.action = "save";
            save.addEventListener("click", () => saveModel(model, row));
            actionCell.appendChild(save);
            row.appendChild(actionCell);
            modelsBody.appendChild(row);
        }
    }
    function checkedModelIds() {
        return Array.from(policyModels.querySelectorAll('input[type="checkbox"]:checked'))
            .map(item => item.value);
    }
    function refreshDefaultOptions() {
        const allowed = checkedModelIds();
        const previous = policyDefault.value;
        policyDefault.replaceChildren();
        policyDefault.appendChild(option("", allowed.length ? "Selecione o padrão" : "Sem modelos permitidos"));
        for (const id of allowed) {
            const model = models.find(item => item.id === id);
            policyDefault.appendChild(option(id, model ? model.name : id));
        }
        policyDefault.disabled = allowed.length === 0;
        policyDefault.value = allowed.includes(previous) ? previous : (allowed[0] || "");
    }
    function renderPolicy(departmentId) {
        selectedDepartment = departmentId;
        if (!departmentId) {
            policyModels.replaceChildren();
            policyDefault.replaceChildren();
            policyDefault.disabled = true;
            policySave.disabled = true;
            return;
        }
        const policy = policies.get(departmentId)
            || { allowedModelIds: [], defaultModelId: null };
        policyModels.replaceChildren();
        for (const model of models) {
            const label = document.createElement("label");
            label.className = "form-check d-block";
            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.className = "form-check-input me-2";
            checkbox.value = model.id;
            checkbox.checked = (policy.allowedModelIds || []).includes(model.id);
            checkbox.addEventListener("change", refreshDefaultOptions);
            label.appendChild(checkbox);
            label.appendChild(document.createTextNode(`${model.name} (${model.status})`));
            policyModels.appendChild(label);
        }
        const allowed = policy.allowedModelIds || [];
        policyDefault.replaceChildren();
        policyDefault.appendChild(option("", allowed.length ? "Selecione o padrão" : "Sem modelos permitidos"));
        for (const id of allowed) {
            const model = models.find(item => item.id === id);
            policyDefault.appendChild(option(id, model ? model.name : id));
        }
        policyDefault.disabled = allowed.length === 0;
        policyDefault.value = policy.defaultModelId || (allowed[0] || "");
        policySave.disabled = false;
    }
    async function loadPolicy(departmentId) {
        try {
            const policy = await client.get(
                `/v1/ai-gateway/departments/${encodeURIComponent(departmentId)}/model-policy`);
            policies.set(departmentId, {
                allowedModelIds: policy.allowedModelIds || [],
                defaultModelId: policy.defaultModelId || null
            });
        } catch (error) {
            // 403/404 não derrubam o restante do catálogo; a política fica vazia.
            policies.set(departmentId, { allowedModelIds: [], defaultModelId: null });
            if (error.status !== 403 && error.status !== 404) {
                policyMessage.textContent =
                    "Não foi possível carregar a política de um departamento.";
            }
        }
    }
    function renderPolicyDepartmentSelect() {
        policyDepartment.replaceChildren();
        if (!departments.length) {
            policyDepartment.appendChild(option("", "Nenhum departamento"));
            policyDepartment.disabled = true;
            policySave.disabled = true;
            return;
        }
        for (const department of departments) {
            policyDepartment.appendChild(option(department.id, department.name));
        }
        policyDepartment.disabled = false;
        const keep = departments.some(item => item.id === selectedDepartment);
        selectedDepartment = keep ? selectedDepartment : departments[0].id;
        policyDepartment.value = selectedDepartment;
        renderPolicy(selectedDepartment);
    }
    async function savePolicy() {
        const departmentId = policyDepartment.value;
        if (!departmentId) return;
        const allowed = checkedModelIds();
        const selectedDefault = policyDefault.value || null;
        if (allowed.length === 0 && selectedDefault) {
            policyMessage.textContent = "Sem modelos permitidos, o padrão deve ficar vazio.";
            return;
        }
        if (allowed.length > 0 && !selectedDefault) {
            policyMessage.textContent = "Selecione o modelo padrão entre os permitidos.";
            return;
        }
        if (selectedDefault && !allowed.includes(selectedDefault)) {
            policyMessage.textContent = "O modelo padrão deve pertencer aos modelos permitidos.";
            return;
        }
        policySave.disabled = true;
        policyMessage.textContent = "Salvando política…";
        try {
            await client.put(
                `/v1/ai-gateway/departments/${encodeURIComponent(departmentId)}/model-policy`,
                { allowedModelIds: allowed, defaultModelId: selectedDefault });
            policies.set(departmentId, {
                allowedModelIds: allowed,
                defaultModelId: selectedDefault
            });
            policyMessage.textContent = "Política salva.";
        } catch (error) {
            policyMessage.textContent = errorMessage(error,
                "Não foi possível salvar a política.");
        } finally {
            policySave.disabled = false;
        }
    }
    function updateReviewSummary() {
        try {
            sessionStorage.setItem("onboarding_ia", JSON.stringify({
                provedor: providers.map(item => item.name).join(", ") || "Nenhum provedor",
                modelos: models.filter(item => item.status === "AVAILABLE")
                    .map(item => item.name).join(", ") || "Nenhum modelo ativo"
            }));
        } catch (error) {
            // A revisão do onboarding não é bloqueada por storage indisponível.
        }
    }
    async function load() {
        setMessage("Carregando catálogo…", "muted");
        modelsMessage.textContent = "";
        policyMessage.textContent = "";
        try {
            const access = await client.get("/v1/usuarios/me/permissoes");
            const permissions = access && Array.isArray(access.permissoes) ? access.permissoes : [];
            if (!permissions.includes("AI_TOOL:MANAGE")) {
                showRestricted();
                return;
            }
            const [providerData, modelData, departmentData] = await Promise.all([
                client.get("/v1/ai-gateway/providers"),
                client.get("/v1/ai-gateway/models"),
                client.get("/v1/departamentos")
            ]);
            providers = Array.isArray(providerData) ? providerData : [];
            models = Array.isArray(modelData) ? modelData : [];
            departments = Array.isArray(departmentData)
                ? departmentData : ((departmentData && departmentData.content) || []);
            renderProviders();
            renderModels();
            renderPolicyDepartmentSelect();
            await Promise.all(departments.map(item => loadPolicy(item.id)));
            renderModels();
            renderPolicy(selectedDepartment);
            content.hidden = false;
            setMessage("", "muted");
            updateReviewSummary();
        } catch (error) {
            if (error && (error.status === 401 || error.status === 403)) {
                showRestricted();
                return;
            }
            content.hidden = true;
            setMessage(errorMessage(error,
                "Não foi possível carregar o catálogo. Verifique a conexão e tente novamente."),
                "danger");
        }
    }

    policyDepartment.addEventListener("change", () => renderPolicy(policyDepartment.value));
    policySave.addEventListener("click", savePolicy);
    load();
})();
