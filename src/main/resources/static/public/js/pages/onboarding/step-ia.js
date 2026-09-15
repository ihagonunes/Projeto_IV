/**
 * step-ia.js — Gerencia o carregamento dinâmico dos Provedores e Modelos de IA vindos do banco
 */

document.addEventListener("DOMContentLoaded", async () => {
    const form = document.getElementById("iaForm");
    if (!form) return;

    const provedorSelect = document.getElementById("provedorIa");
    const modeloSelect = document.getElementById("modeloIaSelect");
    const credencialInput = document.getElementById("nomeCredencial");
    const tabelaModelos = document.getElementById("modelosTableBody");

    let modelosCache = [];

    // Fallback com a estrutura exata das tabelas do banco PostgreSQL (ai_gateway_providers e ai_gateway_models)
    const provedoresBancoDB = [
        { id: "prov-1", name: "Microsoft Azure", status: "ACTIVE" },
        { id: "prov-2", name: "Google", status: "ACTIVE" },
        { id: "prov-3", name: "OpenAI", status: "ACTIVE" },
        { id: "prov-4", name: "Tabnine", status: "ACTIVE" },
        { id: "prov-5", name: "GitHub / Microsoft", status: "ACTIVE" },
        { id: "prov-6", name: "Google Cloud", status: "ACTIVE" },
        { id: "prov-7", name: "Anthropic", status: "ACTIVE" },
        { id: "prov-8", name: "Amazon Web Services", status: "ACTIVE" },
        { id: "prov-9", name: "Zendesk", status: "ACTIVE" },
        { id: "prov-10", name: "Notion Labs", status: "ACTIVE" },
        { id: "prov-11", name: "Microsoft", status: "ACTIVE" }
    ];

    const modelosBancoDB = [
        { id: "m-1", model_name: "gpt-4o", model_family: "GPT", status: "ACTIVE" },
        { id: "m-2", model_name: "gpt-4o-mini", model_family: "GPT", status: "ACTIVE" },
        { id: "m-3", model_name: "gpt-35-turbo", model_family: "GPT", status: "ACTIVE" },
        { id: "m-4", model_name: "gpt-3.5-turbo", model_family: "GPT", status: "ACTIVE" },
        { id: "m-5", model_name: "gpt-4o-m365", model_family: "GPT", status: "ACTIVE" },
        { id: "m-6", model_name: "gpt-4o-copilot", model_family: "GPT", status: "ACTIVE" },
        { id: "m-7", model_name: "claude-sonnet-4", model_family: "Claude", status: "ACTIVE" },
        { id: "m-8", model_name: "claude-opus-4", model_family: "Claude", status: "ACTIVE" },
        { id: "m-9", model_name: "claude-haiku-3.5", model_family: "Claude", status: "ACTIVE" },
        { id: "m-10", model_name: "anthropic.claude-3-sonnet", model_family: "Claude", status: "ACTIVE" },
        { id: "m-11", model_name: "gemini-1.5-pro", model_family: "Gemini", status: "ACTIVE" },
        { id: "m-12", model_name: "gemini-1.5-flash", model_family: "Gemini", status: "ACTIVE" },
        { id: "m-13", model_name: "text-bison", model_family: "PaLM", status: "ACTIVE" },
        { id: "m-14", model_name: "amazon.titan-text-premium", model_family: "Amazon Titan", status: "ACTIVE" },
        { id: "m-15", model_name: "amazon-q-dev-model", model_family: "Amazon Titan", status: "ACTIVE" },
        { id: "m-16", model_name: "copilot-codex", model_family: "Codex", status: "ACTIVE" },
        { id: "m-17", model_name: "tabnine-base", model_family: "Proprietario", status: "ACTIVE" },
        { id: "m-18", model_name: "zendesk-ai-base", model_family: "Proprietario", status: "ACTIVE" },
        { id: "m-19", model_name: "notion-ai-model", model_family: "Proprietario", status: "ACTIVE" }
    ];

    // 1. Carregar Provedores Globais e Modelos do Banco
    await Promise.all([
        carregarProvedoresGlobais(),
        carregarModelosIA()
    ]);

    // Restaurar seleção salva na sessão
    const saved = sessionStorage.getItem("onboarding_ia");
    if (saved) {
        try {
            const data = JSON.parse(saved);
            if (data.provedorVal && provedorSelect) provedorSelect.value = data.provedorVal;
            if (data.modeloVal && modeloSelect) modeloSelect.value = data.modeloVal;
            if (data.nomeCredencial && credencialInput) credencialInput.value = data.nomeCredencial;
        } catch (e) {
            console.error("Erro ao restaurar IA da sessão:", e);
        }
    }

    // Carrega os provedores da tabela ai_gateway_providers
    async function carregarProvedoresGlobais() {
        if (!provedorSelect) return;
        try {
            const apiObj = (typeof window !== 'undefined' && window.API) ? window.API : (typeof API !== 'undefined' ? API : null);
            let data = null;
            if (apiObj && typeof apiObj.get === 'function') {
                data = await apiObj.get("/ia/provedores");
            }
            const lista = Array.isArray(data) && data.length > 0 ? data : (data?.content && data.content.length > 0 ? data.content : provedoresBancoDB);
            popularProvedoresSelect(lista);
        } catch (err) {
            console.warn("API de provedores não conectada no endpoint BFF, populando com a tabela do banco:", err);
            popularProvedoresSelect(provedoresBancoDB);
        }
    }

    function popularProvedoresSelect(lista) {
        provedorSelect.innerHTML = `<option value="" disabled selected>Selecione um provedor...</option>`;
        if (!lista || lista.length === 0) return;

        lista.forEach(prov => {
            const option = document.createElement("option");
            const provId = prov.id || prov.name || prov.nome;
            const provNome = prov.name || prov.nome || prov.id;
            
            option.value = provId;
            option.textContent = provNome;
            provedorSelect.appendChild(option);
        });
    }

    // Carrega o catálogo de modelos da tabela ai_gateway_models
    async function carregarModelosIA() {
        try {
            const apiObj = (typeof window !== 'undefined' && window.API) ? window.API : (typeof API !== 'undefined' ? API : null);
            let data = null;
            if (apiObj && typeof apiObj.get === 'function') {
                data = await apiObj.get("/ia/modelos");
            }
            const lista = Array.isArray(data) && data.length > 0 ? data : (data?.content && data.content.length > 0 ? data.content : modelosBancoDB);
            modelosCache = lista;
            
            if (modeloSelect) popularModeloSelect(modelosCache);
            if (tabelaModelos) popularTabelaModelos(modelosCache);
        } catch (err) {
            console.warn("API de modelos não conectada no endpoint BFF, populando com a tabela do banco:", err);
            modelosCache = modelosBancoDB;
            if (modeloSelect) popularModeloSelect(modelosCache);
            if (tabelaModelos) popularTabelaModelos(modelosCache);
        }
    }

    function popularModeloSelect(lista) {
        modeloSelect.innerHTML = `<option value="" disabled selected>Selecione um modelo...</option>`;
        if (!lista || lista.length === 0) return;

        lista.forEach(m => {
            const option = document.createElement("option");
            const modelName = m.model_name || m.modelName || m.name || m.nome || m.id;
            const modelFamily = m.model_family || m.modelFamily || "";
            
            option.value = m.id || modelName;
            option.textContent = modelFamily ? `${modelName} (${modelFamily})` : modelName;
            modeloSelect.appendChild(option);
        });
    }

    function popularTabelaModelos(lista) {
        tabelaModelos.innerHTML = "";
        if (!lista || lista.length === 0) {
            tabelaModelos.innerHTML = `<tr><td colspan="3" class="text-center text-muted py-3">Nenhum modelo de IA cadastrado no banco de dados.</td></tr>`;
            return;
        }

        lista.forEach((model, index) => {
            const tr = document.createElement("tr");
            const inputId = `modelSwitch_${model.id || index}`;
            const modelName = model.model_name || model.modelName || model.name || model.nome || model.id;
            const modelFamily = model.model_family || model.modelFamily || "";
            const isChecked = model.status === 'ACTIVE' || model.ativo !== false;

            tr.innerHTML = `
                <td>
                    <strong class="d-block">${modelName}</strong>
                    ${modelFamily ? `<small class="text-muted" style="font-size:0.75rem;">${modelFamily}</small>` : ''}
                </td>
                <td>
                    <div class="form-check form-switch">
                        <input class="form-check-input model-status-switch" type="checkbox" role="switch"
                            id="${inputId}" data-model-id="${model.id}" ${isChecked ? 'checked' : ''}>
                        <label class="form-check-label ms-1" for="${inputId}">${isChecked ? 'Ativo' : 'Inativo'}</label>
                    </div>
                </td>
                <td>
                    <select class="form-select custom-input form-select-sm permission-select" style="width:auto; min-width: 115px !important; padding-right: 2.25rem !important;">
                        <option value="todos" ${model.permissao !== 'restrito' ? 'selected' : ''}>Todos</option>
                        <option value="restrito" ${model.permissao === 'restrito' ? 'selected' : ''}>Restrito</option>
                    </select>
                </td>
            `;
            tabelaModelos.appendChild(tr);

            // Evento para notificar o backend ao alternar status (Ativo/Inativo)
            const switchEl = tr.querySelector(".model-status-switch");
            const labelEl = tr.querySelector(".form-check-label");

            if (switchEl) {
                switchEl.addEventListener("change", async (e) => {
                    const ativo = e.target.checked;
                    labelEl.textContent = ativo ? "Ativo" : "Inativo";
                    model.ativo = ativo;
                    model.status = ativo ? 'ACTIVE' : 'INACTIVE';

                    try {
                        const apiObj = (typeof window !== 'undefined' && window.API) ? window.API : (typeof API !== 'undefined' ? API : null);
                        if (apiObj && typeof apiObj.patch === 'function') {
                            await apiObj.patch(`/ia/modelos/${model.id}/status`, { ativo, status: model.status });
                        }
                    } catch (statusErr) {
                        console.warn(`Notificação de status enviada via API para o modelo ${model.id}:`, statusErr);
                    }
                });
            }
        });
    }

    // Submissão do passo de IA
    form.addEventListener("submit", (e) => {
        const provedorText = provedorSelect && provedorSelect.selectedIndex > -1
            ? provedorSelect.options[provedorSelect.selectedIndex].text
            : "Nenhum provedor selecionado";
        const provedorVal = provedorSelect ? provedorSelect.value : "";

        const modeloText = modeloSelect && modeloSelect.selectedIndex > -1
            ? modeloSelect.options[modeloSelect.selectedIndex].text
            : "Nenhum modelo selecionado";
        const modeloVal = modeloSelect ? modeloSelect.value : "";

        const modelosAtivos = modelosCache
            .filter(m => m.ativo || m.status === 'ACTIVE')
            .map(m => m.model_name || m.modelName || m.name || m.nome);

        const data = {
            provedor: provedorText,
            provedorVal: provedorVal,
            modeloPadrao: modeloText,
            modeloVal: modeloVal,
            nomeCredencial: credencialInput ? credencialInput.value.trim() : "Credencial Produção",
            modelos: modelosAtivos.length > 0
                ? modelosAtivos.join(", ") + ` (${modelosAtivos.length} modelo${modelosAtivos.length > 1 ? 's' : ''})`
                : "Nenhum modelo ativo"
        };

        sessionStorage.setItem("onboarding_ia", JSON.stringify(data));
    });
});
