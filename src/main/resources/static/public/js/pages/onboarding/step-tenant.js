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

    // Salvar na sessão ao submeter
    form.addEventListener("submit", (e) => {
        const data = {
            nomeEmpresa: nomeEmpresaInput ? nomeEmpresaInput.value.trim() : "",
            cnpj: cnpjInput ? cnpjInput.value.trim() : "",
            setor: setorInput ? setorInput.value : "",
            porteEmpresa: porteEmpresaInput ? porteEmpresaInput.value : "",
            fusoHorario: fusoHorarioInput ? fusoHorarioInput.value : "GMT-3",
            idioma: idiomaInput ? idiomaInput.value : "pt-BR"
        };
        sessionStorage.setItem("onboarding_tenant", JSON.stringify(data));
    });
});
