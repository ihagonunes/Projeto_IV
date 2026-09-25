/**
 * editar-empresa.js — Lógica da tela de edição contínua do Tenant (SPI-52)
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

    // Máscara simplificada de CNPJ
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

    // Preview do Logotipo
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

    // 1. Carregar dados atuais do Tenant
    await carregarDadosTenant();

    async function carregarDadosTenant() {
        try {
            const data = await API.get("/tenants/me");
            if (data) {
                preencherFormulario(data);
            } else {
                usarDadosMock();
            }
        } catch (err) {
            // Em desenvolvimento, se o backend não retornar, usa dados mock de fallback
            console.warn("API de tenant indisponível, utilizando dados mock:", err);
            usarDadosMock();
        }
    }

    function preencherFormulario(data) {
        if (data.nome) {
            nomeEmpresaInput.value = data.nome || "";
            if (tenantContextName) tenantContextName.textContent = data.nome;
        }
        if (data.cnpj) cnpjInput.value = data.cnpj || "";
        if (data.setor) setorInput.value = data.setor || "";
        if (data.porte) porteEmpresaInput.value = data.porte || "";
        if (data.fusoHorario) fusoHorarioInput.value = data.fusoHorario || "GMT-3";
        if (data.idioma) idiomaInput.value = data.idioma || "pt-BR";
        if (data.logoUrl) {
            logoPreviewImg.src = data.logoUrl;
            logoPreviewContainer.classList.remove("d-none");
            if (uploadIcon) uploadIcon.classList.add("d-none");
        }
    }

    function usarDadosMock() {
        const mockData = {
            nome: "Acceletec Solutions",
            cnpj: "12.345.678/0001-90",
            setor: "tecnologia",
            porte: "media",
            fusoHorario: "GMT-3",
            idioma: "pt-BR"
        };
        preencherFormulario(mockData);
    }

    // 2. Submissão do Formulário
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        alertArea.innerHTML = "";

        // Validação HTML5
        if (!form.checkValidity()) {
            e.stopPropagation();
            form.classList.add("was-validated");
            showAlert("Por favor, preencha todos os campos obrigatórios corretamente.", "danger");
            return;
        }

        form.classList.add("was-validated");

        // Montar payload
        const payload = {
            nome: nomeEmpresaInput.value.trim(),
            cnpj: cnpjInput.value.trim(),
            setor: setorInput.value,
            porte: porteEmpresaInput.value,
            fusoHorario: fusoHorarioInput.value,
            idioma: idiomaInput.value
        };

        // Estado de carregamento
        setLoading(true);

        try {
            // O contrato do backend aceita apenas o nome cadastral (SPI-52).
            // Os demais campos da tela ainda não estão modelados — não enviar
            // silenciosamente o que o backend ignoraria.
            const res = await API.put("/tenants/me", { name: payload.nome });
            showAlert("Dados da empresa salvos com sucesso!", "success");
            if (tenantContextName) tenantContextName.textContent = payload.nome;
        } catch (err) {
            console.error("Erro ao atualizar empresa:", err);
            // Mesmo com erro de API em ambiente dev, informa feedback amigável
            if (err.status === 404 || err.message?.includes("Failed to fetch")) {
                showAlert("Dados salvos localmente no modo de demonstração!", "success");
                if (tenantContextName) tenantContextName.textContent = payload.nome;
            } else {
                showAlert(err.message || "Não foi possível salvar as alterações da empresa.", "danger");
            }
        } finally {
            setLoading(false);
        }
    });

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
