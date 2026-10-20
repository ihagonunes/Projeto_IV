/**
 * step-tenant.js — Gerencia o preenchimento e salvamento do passo 0 (Empresa) do onboarding
 */

document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("tenantForm");
    if (!form) return;

    const nomeEmpresaInput = document.getElementById("nomeEmpresa");
    const cnpjInput = document.getElementById("cnpj");
    const setorInput = document.getElementById("setor");
    const porteEmpresaInput = document.getElementById("porteEmpresa");
    const fusoHorarioInput = document.getElementById("fusoHorario");
    const idiomaInput = document.getElementById("idioma");
    const adminNomeInput = document.getElementById("adminNome");
    const adminEmailInput = document.getElementById("adminEmail");
    const adminSenhaInput = document.getElementById("adminSenha");
    const slugInput = document.getElementById("tenantSlug");
    const botaoCriar = document.getElementById("btnCriarTenant");
    const mensagem = document.getElementById("tenantMensagem");

    function avisar(texto, tipo) {
        if (!mensagem) return;
        mensagem.textContent = texto || "";
        mensagem.className = texto ? `alert alert-${tipo || "info"} mt-3` : "d-none";
    }

    function slugificar(valor) {
        return String(valor || "")
            .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, "-")
            .replace(/^-+|-+$/g, "")
            .slice(0, 100);
    }

    if (nomeEmpresaInput && slugInput) {
        nomeEmpresaInput.addEventListener("blur", () => {
            if (!slugInput.value.trim()) slugInput.value = slugificar(nomeEmpresaInput.value);
        });
    }

    // Máscara CNPJ
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

    // Carregar dados salvos anteriormente na sessão
    const saved = sessionStorage.getItem("onboarding_tenant");
    if (saved) {
        try {
            const data = JSON.parse(saved);
            if (data.nomeEmpresa && nomeEmpresaInput) nomeEmpresaInput.value = data.nomeEmpresa;
            if (data.cnpj && cnpjInput) cnpjInput.value = data.cnpj;
            if (data.setor && setorInput) setorInput.value = data.setor;
            if (data.porteEmpresa && porteEmpresaInput) porteEmpresaInput.value = data.porteEmpresa;
            if (data.fusoHorario && fusoHorarioInput) fusoHorarioInput.value = data.fusoHorario;
            if (data.idioma && idiomaInput) idiomaInput.value = data.idioma;
        } catch (e) {
            console.error("Erro ao carregar dados do tenant:", e);
        }
    }

    // Cria o tenant (com o admin) e autentica o admin (SPEC-01 §7.1 / A9).
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        form.classList.add("was-validated");
        if (!form.checkValidity()) {
            avisar("Preencha todos os campos obrigatórios.", "warning");
            return;
        }

        const nomeEmpresa = nomeEmpresaInput.value.trim();
        const adminNome = adminNomeInput.value.trim();
        const adminEmail = adminEmailInput.value.trim();
        const adminSenha = adminSenhaInput.value;
        const slug = (slugInput.value.trim() || slugificar(nomeEmpresa)).toLowerCase();
        const emailDomain = adminEmail.includes("@") ? adminEmail.split("@")[1] : "";

        if (!slug || !/^[a-z0-9][a-z0-9-]{1,99}$/.test(slug)) {
            avisar("Informe um identificador (slug) válido.", "warning");
            return;
        }
        if (!emailDomain) {
            avisar("Informe um e-mail de administrador válido.", "warning");
            return;
        }

        if (botaoCriar) botaoCriar.disabled = true;
        avisar("");
        try {
            await window.API.post("/tenants", {
                slug,
                name: nomeEmpresa,
                emailDomain,
                adminName: adminNome,
                adminEmail,
                adminPassword: adminSenha
            });

            const login = await window.API.post("/auth/login", {
                tenantSlug: slug,
                email: adminEmail,
                password: adminSenha
            });
            const token = login?.token || login?.accessToken || login?.access_token;
            if (token) {
                localStorage.setItem("authToken", token);
            }

            sessionStorage.setItem("onboarding_tenant", JSON.stringify({
                nomeEmpresa,
                cnpj: cnpjInput ? cnpjInput.value.trim() : "",
                setor: setorInput ? setorInput.value : "",
                porteEmpresa: porteEmpresaInput ? porteEmpresaInput.value : "",
                fusoHorario: fusoHorarioInput ? fusoHorarioInput.value : "GMT-3",
                idioma: idiomaInput ? idiomaInput.value : "pt-BR",
                slug: slug
            }));
            sessionStorage.setItem("onboarding_admin", JSON.stringify({
                nome: adminNome,
                email: adminEmail
            }));

            window.location.href = "/onboarding/admin";
        } catch (err) {
            avisar(err?.message || "Não foi possível criar a empresa. Tente novamente.", "danger");
        } finally {
            if (botaoCriar) botaoCriar.disabled = false;
        }
    });
});
