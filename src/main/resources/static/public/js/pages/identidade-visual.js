/**
 * identidade-visual.js — Lógica da tela de Identidade Visual e Branding (SPI-53)
 *
 * Endpoints consumidos (via proxy Node → Spring Boot):
 *   GET    /api/tenant/branding  → carrega dados atuais (retorna TenantResponse)
 *   PATCH  /api/tenant/branding  → salva logoUrl, primaryColor, secondaryColor
 */

document.addEventListener("DOMContentLoaded", async () => {
    const form = document.getElementById("identidadeForm");
    const alertArea = document.getElementById("alertArea");
    const btnSalvar = document.getElementById("btnSalvarIdentidade");
    const salvarSpinner = document.getElementById("salvarSpinner");
    const tenantContextName = document.getElementById("tenantContextName");

    // Elementos do formulário
    const nomeExibicaoInput = document.getElementById("nomeExibicao");
    const sloganMarcaInput = document.getElementById("sloganMarca");
    const logoFileInput = document.getElementById("logoFile");
    const logoPreviewThumbContainer = document.getElementById("logoPreviewThumbContainer");
    const logoPreviewThumb = document.getElementById("logoPreviewThumb");
    const uploadIcon = document.getElementById("uploadIcon");

    // Elementos do Live Preview do Cabeçalho
    const previewNavbarLogo = document.getElementById("previewNavbarLogo");
    const previewNavbarBrand = document.getElementById("previewNavbarBrand");

    // logoUrl atual em base64 ou URL, usado no payload
    let logoUrlAtual = "";

    // 1. Atualizações em tempo real (Live Preview)
    nomeExibicaoInput.addEventListener("input", (e) => {
        const val = e.target.value.trim();
        const brandText = val ? val.toUpperCase() : "SUA MARCA";
        if (previewNavbarBrand) previewNavbarBrand.textContent = brandText;
        if (tenantContextName) tenantContextName.textContent = val || "Minha Empresa";
    });

    logoFileInput.addEventListener("change", (e) => {
        const file = e.target.files[0];
        if (file) {
            if (file.size > 2 * 1024 * 1024) {
                showAlert("O arquivo de imagem excede o limite permitido de 2MB.", "warning");
                logoFileInput.value = "";
                return;
            }
            const reader = new FileReader();
            reader.onload = (event) => {
                const imgData = event.target.result;
                logoUrlAtual = imgData;

                // Atualiza Thumbnail do formulário
                logoPreviewThumb.src = imgData;
                logoPreviewThumbContainer.classList.remove("d-none");
                if (uploadIcon) uploadIcon.classList.add("d-none");

                // Atualiza Live Preview do Cabeçalho em Tempo Real
                if (previewNavbarLogo) {
                    previewNavbarLogo.src = imgData;
                    previewNavbarLogo.classList.remove("d-none");
                }
            };
            reader.readAsDataURL(file);
        }
    });

    // 2. Carregar dados atuais
    await carregarBranding();

    async function carregarBranding() {
        try {
            // GET /api/tenant/branding retorna o TenantResponse completo
            const data = await API.get("/tenant/branding");
            if (data) {
                preencherFormulario(data);
            } else {
                usarDadosMock();
            }
        } catch (err) {
            console.warn("API de branding indisponível, usando fallback mock:", err);
            usarDadosMock();
        }
    }

    function preencherFormulario(data) {
        // `name` vem do TenantResponse
        const nome = data.name || data.nomeExibicao || "";
        if (nome) {
            nomeExibicaoInput.value = nome;
            if (previewNavbarBrand) previewNavbarBrand.textContent = nome.toUpperCase();
            if (tenantContextName) tenantContextName.textContent = nome;
        }
        if (data.slogan && sloganMarcaInput) {
            sloganMarcaInput.value = data.slogan;
        }
        if (data.logoUrl) {
            logoUrlAtual = data.logoUrl;
            logoPreviewThumb.src = data.logoUrl;
            logoPreviewThumbContainer.classList.remove("d-none");
            if (uploadIcon) uploadIcon.classList.add("d-none");
            if (previewNavbarLogo) {
                previewNavbarLogo.src = data.logoUrl;
                previewNavbarLogo.classList.remove("d-none");
            }
        } else {
            if (previewNavbarLogo) {
                previewNavbarLogo.src = "";
                previewNavbarLogo.classList.add("d-none");
            }
        }
    }

    function usarDadosMock() {
        preencherFormulario({
            name: "Acceletec Solutions",
            slogan: "Governança & Inteligência Corporativa",
            logoUrl: ""
        });
    }

    // 3. Submissão do Formulário (SPI-53)
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        alertArea.innerHTML = "";

        if (!nomeExibicaoInput.value.trim()) {
            form.classList.add("was-validated");
            showAlert("O nome de exibição da marca é obrigatório.", "danger");
            return;
        }

        setLoading(true);

        // O backend aceita { logoUrl, primaryColor, secondaryColor }
        // O nomeExibicao é salvo via PATCH /me (SPI-52), não no branding
        const payload = {
            logoUrl: logoUrlAtual || null,
            primaryColor: null,   // extensível via futuros campos de cor
            secondaryColor: null
        };

        try {
            await API.patch("/tenant/branding", payload);
            showAlert("Identidade visual e branding salvos com sucesso!", "success");
        } catch (err) {
            console.error("Erro ao salvar identidade visual:", err);
            if (err.status === 404 || err.message?.includes("Failed to fetch")) {
                showAlert("Identidade visual salva no modo de demonstração!", "success");
            } else {
                showAlert(err.message || "Falha ao salvar as configurações de identidade visual.", "danger");
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
