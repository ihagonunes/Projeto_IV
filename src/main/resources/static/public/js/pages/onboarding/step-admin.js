/**
 * step-admin.js — Gerencia o preenchimento e salvamento do passo 1 (Administrador) do onboarding
 */

document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("adminForm");
    if (!form) return;

    const nomeInput = document.getElementById("nomeCompleto");
    const emailInput = document.getElementById("emailCorporativo");
    const cargoInput = document.getElementById("cargo");
    const telefoneInput = document.getElementById("telefone");

    // Máscara de Telefone
    if (telefoneInput) {
        telefoneInput.addEventListener("input", (e) => {
            let value = e.target.value.replace(/\D/g, "");
            if (value.length > 11) value = value.slice(0, 11);

            if (value.length > 10) {
                value = value.replace(/^(\d{2})(\d{5})(\d{4})$/, "($1) $2-$3");
            } else if (value.length > 6) {
                value = value.replace(/^(\d{2})(\d{4})(\d{0,4})$/, "($1) $2-$3");
            } else if (value.length > 2) {
                value = value.replace(/^(\d{2})(\d{0,5})$/, "($1) $2");
            }
            e.target.value = value;
        });
    }

    // Carregar dados salvos anteriormente
    const saved = sessionStorage.getItem("onboarding_admin");
    if (saved) {
        try {
            const data = JSON.parse(saved);
            if (data.nome && nomeInput) nomeInput.value = data.nome;
            if (data.email && emailInput) emailInput.value = data.email;
            if (data.cargo && cargoInput) cargoInput.value = data.cargo;
            if (data.telefone && telefoneInput) telefoneInput.value = data.telefone;
        } catch (e) {
            console.error("Erro ao carregar dados do admin:", e);
        }
    }

    // Salvar ao submeter
    form.addEventListener("submit", (e) => {
        const data = {
            nome: nomeInput ? nomeInput.value.trim() : "",
            email: emailInput ? emailInput.value.trim() : "",
            cargo: cargoInput ? cargoInput.value.trim() : "",
            telefone: telefoneInput ? telefoneInput.value.trim() : ""
        };
        sessionStorage.setItem("onboarding_admin", JSON.stringify(data));
    });
});
