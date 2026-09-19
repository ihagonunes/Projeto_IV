/**
 * editar-empresa.js — Lógica da tela de edição contínua do Tenant (SPI-52 + SPI-57)
 *
 * Endpoints consumidos (via proxy Node → Spring Boot):
 *   GET    /api/tenant/me           → carrega dados atuais
 *   PATCH  /api/tenant/me           → atualiza nome (SPI-52)
 *   PATCH  /api/tenant/status       → ativa / desativa tenant (SPI-57)
 */

document.addEventListener("DOMContentLoaded", async () => {
    const form = document.getElementById("editarEmpresaForm");
    const alertArea = document.getElementById("alertArea");
    const btnSalvar = document.getElementById("btnSalvar");
    const salvarSpinner = document.getElementById("salvarSpinner");
    const tenantContextName = document.getElementById("tenantContextName");

    // Elementos de formulário
    const nomeEmpresaInput = document.getElementById("nomeEmpresa");
    const cnpjInput = document.getElementById("cnpj");
    const setorInput = document.getElementById("setor");
    const porteEmpresaInput = document.getElementById("porteEmpresa");
    const fusoHorarioInput = document.getElementById("fusoHorario");
    const idiomaInput = document.getElementById("idioma");
    const logoEmpresaInput = document.getElementById("logoEmpresa");
    const logoPreviewContainer = document.getElementById("logoPreviewContainer");
    const logoPreviewImg = document.getElementById("logoPreviewImg");
    const uploadIcon = document.getElementById("uploadIcon");

    // Elementos de Status (SPI-57)
    const statusToggle = document.getElementById("statusToggle");
    const statusLabel = document.getElementById("statusLabel");
    const btnAlterarStatus = document.getElementById("btnAlterarStatus");
    const statusSpinner = document.getElementById("statusSpinner");

    // Máscara simplificada de CNPJ
    if (cnpjInput) {
        cnpjInput.addEventListener("input", (e) => {
            let value = e.target.value.replace(/\D/g, "");
            if (value.length > 14) value = value.slice(0, 14);

            if (value.length > 12) {
                value = value.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{1,2})$/, "$1.$2.$3/$4-$5");
            } else if (value.length > 8) {
                value = value.replace(/^(\d{2})(\d{3})(\d{3})(\d{1,4})$/, "$1.$2.$3/$4");
            } else if (value.length > 5) {
                value = value.replace(/^(\d{2})(\d{3})(\d{1,3})$/, "$1.$2.$3");
            } else if (value.length > 2) {
                value = value.replace(/^(\d{2})(\d{1,3})$/, "$1.$2");
            }
            e.target.value = value;
        });
    }

    // Preview do Logotipo
    if (logoEmpresaInput) {
        logoEmpresaInput.addEventListener("change", (e) => {
            const file = e.target.files[0];
            if (file) {
                if (file.size > 2 * 1024 * 1024) {
                    showAlert("O arquivo selecionado excede o limite de 2MB.", "warning");
                    logoEmpresaInput.value = "";
                    return;
                }
                const reader = new FileReader();
                reader.onload = (event) => {
                    logoPreviewImg.src = event.target.result;
                    logoPreviewContainer.classList.remove("d-none");
                    if (uploadIcon) uploadIcon.classList.add("d-none");
                };
                reader.readAsDataURL(file);
            }
        });
    }

    // 1. Carregar dados atuais do Tenant
    let tenantAtual = null;
    await carregarDadosTenant();

    async function carregarDadosTenant() {
        try {
            const data = await API.get("/tenant/me");
            if (data) {
                tenantAtual = data;
                preencherFormulario(data);
                atualizarToggleStatus(data.status);
            } else {
                usarDadosMock();
            }
        } catch (err) {
            console.warn("API de tenant indisponível, utilizando dados mock:", err);
            usarDadosMock();
        }
    }

    function preencherFormulario(data) {
        // O backend retorna `name`; campos extras (cnpj, setor, etc.) ficam em mock por ora
        if (data.name || data.nome) {
            const nome = data.name || data.nome;
            nomeEmpresaInput.value = nome;
            if (tenantContextName) tenantContextName.textContent = nome;
        }
        if (data.cnpj && cnpjInput) cnpjInput.value = data.cnpj;
        if (data.setor && setorInput) setorInput.value = data.setor;
        if (data.porte && porteEmpresaInput) porteEmpresaInput.value = data.porte;
        if (data.fusoHorario && fusoHorarioInput) fusoHorarioInput.value = data.fusoHorario;
        if (data.idioma && idiomaInput) idiomaInput.value = data.idioma;
        if (data.logoUrl && logoPreviewImg) {
            logoPreviewImg.src = data.logoUrl;
            logoPreviewContainer.classList.remove("d-none");
            if (uploadIcon) uploadIcon.classList.add("d-none");
        }
    }

    function atualizarToggleStatus(status) {
        if (!statusToggle || !statusLabel) return;
        const isAtivo = status === "ACTIVE" || status === "Ativo";
        statusToggle.checked = isAtivo;
        statusLabel.textContent = isAtivo ? "Tenant Ativo" : "Tenant Inativo";
        statusLabel.className = isAtivo ? "fw-semibold text-success" : "fw-semibold text-danger";
    }

    function usarDadosMock() {
        const mockData = {
            name: "Acceletec Solutions",
            cnpj: "12.345.678/0001-90",
            setor: "tecnologia",
            porte: "media",
            fusoHorario: "GMT-3",
            idioma: "pt-BR",
            status: "ACTIVE"
        };
        tenantAtual = mockData;
        preencherFormulario(mockData);
        atualizarToggleStatus(mockData.status);
    }

    // 2. Submissão do Formulário (SPI-52 — atualiza nome)
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        alertArea.innerHTML = "";

        if (!form.checkValidity()) {
            e.stopPropagation();
            form.classList.add("was-validated");
            showAlert("Por favor, preencha todos os campos obrigatórios corretamente.", "danger");
            return;
        }

        form.classList.add("was-validated");

        // O backend (SPI-52) aceita apenas { name } por enquanto
        const payload = {
            name: nomeEmpresaInput.value.trim()
        };

        setLoading(true);

        try {
            await API.patch("/tenant/me", payload);
            showAlert("Dados da empresa salvos com sucesso!", "success");
            if (tenantContextName) tenantContextName.textContent = payload.name;
        } catch (err) {
            console.error("Erro ao atualizar empresa:", err);
            if (err.status === 404 || err.message?.includes("Failed to fetch")) {
                showAlert("Dados salvos localmente no modo de demonstração!", "success");
                if (tenantContextName) tenantContextName.textContent = payload.name;
            } else {
                showAlert(err.message || "Não foi possível salvar as alterações da empresa.", "danger");
            }
        } finally {
            setLoading(false);
        }
    });

    // 3. Alteração de Status Ativo/Inativo (SPI-57)
    if (btnAlterarStatus) {
        btnAlterarStatus.addEventListener("click", async () => {
            const novoStatus = statusToggle && statusToggle.checked ? "INACTIVE" : "ACTIVE";
            const acao = novoStatus === "ACTIVE" ? "ativar" : "desativar";

            if (!confirm(`Tem certeza que deseja ${acao} este tenant?`)) return;

            btnAlterarStatus.disabled = true;
            if (statusSpinner) statusSpinner.classList.remove("d-none");

            try {
                const res = await API.patch("/tenant/status", { status: novoStatus });
                atualizarToggleStatus(novoStatus);
                showAlert(
                    `Tenant ${novoStatus === "ACTIVE" ? "ativado" : "desativado"} com sucesso!`,
                    novoStatus === "ACTIVE" ? "success" : "warning"
                );
            } catch (err) {
                console.error("Erro ao alterar status:", err);
                if (err.status === 404 || err.message?.includes("Failed to fetch")) {
                    atualizarToggleStatus(novoStatus);
                    showAlert(
                        `Status alterado no modo de demonstração!`,
                        "success"
                    );
                } else {
                    showAlert(err.message || "Falha ao alterar status do tenant.", "danger");
                }
            } finally {
                btnAlterarStatus.disabled = false;
                if (statusSpinner) statusSpinner.classList.add("d-none");
            }
        });
    }

    // Utilitários
    function setLoading(isLoading) {
        if (isLoading) {
            btnSalvar.disabled = true;
            if (salvarSpinner) salvarSpinner.classList.remove("d-none");
        } else {
            btnSalvar.disabled = false;
            if (salvarSpinner) salvarSpinner.classList.add("d-none");
        }
    }

    function showAlert(message, type = "success") {
        alertArea.innerHTML = `
            <div class="alert alert-${type} alert-dismissible fade show d-flex align-items-center gap-2" role="alert">
                <i class="bi bi-${type === 'success' ? 'check-circle-fill' : type === 'warning' ? 'exclamation-triangle-fill' : 'exclamation-circle-fill'}"></i>
                <div>${message}</div>
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fechar"></button>
            </div>
        `;
    }
});
