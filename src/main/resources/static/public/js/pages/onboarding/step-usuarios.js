/**
 * step-usuarios.js — Gerencia a escolha do método de cadastro e população dinâmica de departamentos
 */

document.addEventListener("DOMContentLoaded", async () => {
    // Definir default se o usuário passar direto pelo hub
    const saved = sessionStorage.getItem("onboarding_usuarios");
    if (!saved) {
        sessionStorage.setItem("onboarding_usuarios", JSON.stringify({
            metodo: "Convite"
        }));
    }

    const importCard = document.querySelector('a[href="/onboarding/usuarios/importacao"]');
    const inviteCard = document.querySelector('a[href="/convites"]');
    const selectDepUsuario = document.getElementById("departamentoUsuario");

    if (importCard) {
        importCard.addEventListener("click", () => {
            sessionStorage.setItem("onboarding_usuarios", JSON.stringify({
                metodo: "Importação via CSV em Massa"
            }));
        });
    }

    if (inviteCard) {
        inviteCard.addEventListener("click", () => {
            sessionStorage.setItem("onboarding_usuarios", JSON.stringify({
                metodo: "Convite"
            }));
        });
    }

    // Se estiver na tela de Cadastro Manual, carrega os departamentos do Tenant
    if (selectDepUsuario) {
        await carregarDepartamentosParaUsuario();
    }

    async function carregarDepartamentosParaUsuario() {
        try {
            const apiObj = (typeof window !== 'undefined' && window.API) ? window.API : (typeof API !== 'undefined' ? API : null);
            let data = null;
            if (apiObj && typeof apiObj.get === 'function') {
                data = await apiObj.get("/departamentos");
            }
            const lista = Array.isArray(data) && data.length > 0 ? data : (data?.content || []);
            if (lista.length > 0) {
                popularSelect(lista);
            } else {
                throw new Error("Nenhum departamento via API");
            }
        } catch (err) {
            console.warn("API de departamentos não respondeu, usando fallback:", err);
            const savedDep = JSON.parse(sessionStorage.getItem("onboarding_departamentos") || "{}");
            const lista = (savedDep.lista || ["Tecnologia da Informação", "Recursos Humanos", "Financeiro"]).map((nome, idx) => ({ id: idx + 1, nome }));
            popularSelect(lista);
        }
    }

    function popularSelect(lista) {
        selectDepUsuario.innerHTML = "";
        if (!lista || lista.length === 0) {
            selectDepUsuario.innerHTML = `<option value="" disabled selected>Nenhum departamento encontrado...</option>`;
            return;
        }

        selectDepUsuario.innerHTML = `<option value="" disabled selected>Selecione um departamento...</option>`;
        lista.forEach(dep => {
            const option = document.createElement("option");
            option.value = dep.id || dep.nome;
            option.textContent = dep.nome || dep.nomeDepartamento;
            selectDepUsuario.appendChild(option);
        });
    }
});
