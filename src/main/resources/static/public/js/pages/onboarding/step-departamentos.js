/**
 * step-departamentos.js — Gerencia o carregamento dinâmico por tenant_id e edição de departamentos
 */

document.addEventListener("DOMContentLoaded", async () => {
    const selectDepartamento = document.getElementById("selecionarDepartamento");
    const tabelaDepartamentos = document.getElementById("departamentosTableBody");
    const form = document.getElementById("departamentoForm");

    // Inputs do formulário de edição/cadastro
    const nomeInput = document.getElementById("nomeDepartamento");
    const liderInput = document.getElementById("liderDepartamento");
    const orcamentoInput = document.getElementById("orcamentoMensal");
    const limiteInput = document.getElementById("limiteAlerta");
    const emailAlertaInput = document.getElementById("emailAlerta");
    const descricaoInput = document.getElementById("descricao");

    let departamentosCache = [];

    // Carregar departamentos do backend para o Tenant logado
    await carregarDepartamentos();

    async function carregarDepartamentos() {
        try {
            const apiObj = (typeof window !== 'undefined' && window.API) ? window.API : (typeof API !== 'undefined' ? API : null);
            let data = null;
            if (apiObj && typeof apiObj.get === 'function') {
                data = await apiObj.get("/departamentos");
            }
            departamentosCache = Array.isArray(data) ? data : (data?.content || []);
            
            // Salvar no sessionStorage para manter o estado do wizard
            sessionStorage.setItem("onboarding_departamentos", JSON.stringify({
                total: departamentosCache.length,
                lista: departamentosCache.map(d => d.nome || d.nomeDepartamento)
            }));

            if (selectDepartamento) popularSelect(departamentosCache);
            if (tabelaDepartamentos) popularTabela(departamentosCache);
        } catch (err) {
            console.warn("API de departamentos indisponível, usando estado padrão:", err);
            departamentosCache = [
                { id: 1, nome: "Tecnologia da Informação", lider: "Carlos Silva", orcamento: 50000, limite: 80, emailAlerta: "ti@empresa.com", descricao: "Infra e Desenvolvimento" },
                { id: 2, nome: "Recursos Humanos", lider: "Ana Costa", orcamento: 20000, limite: 85, emailAlerta: "rh@empresa.com", descricao: "Gestão de Pessoas" }
            ];

            sessionStorage.setItem("onboarding_departamentos", JSON.stringify({
                total: departamentosCache.length,
                lista: departamentosCache.map(d => d.nome)
            }));

            if (selectDepartamento) popularSelect(departamentosCache);
            if (tabelaDepartamentos) popularTabela(departamentosCache);
        }
    }

    function popularSelect(lista) {
        selectDepartamento.innerHTML = "";
        if (!lista || lista.length === 0) {
            selectDepartamento.innerHTML = `<option value="" disabled selected>Nenhum departamento cadastrado ainda...</option>`;
            return;
        }

        selectDepartamento.innerHTML = `<option value="" disabled selected>Selecione um departamento para editar...</option>`;
        lista.forEach(dep => {
            const option = document.createElement("option");
            option.value = dep.id || dep.nome;
            option.textContent = dep.nome || dep.nomeDepartamento;
            selectDepartamento.appendChild(option);
        });
    }

    function popularTabela(lista) {
        tabelaDepartamentos.innerHTML = "";
        if (!lista || lista.length === 0) {
            tabelaDepartamentos.innerHTML = `<tr><td colspan="5" class="text-center text-muted py-3">Nenhum departamento cadastrado para esta empresa.</td></tr>`;
            return;
        }

        lista.forEach(dep => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${dep.nome || dep.nomeDepartamento}</td>
                <td>${dep.lider || "—"}</td>
                <td>R$ ${(dep.orcamento || 0).toLocaleString("pt-BR", { minimumFractionDigits: 2 })}</td>
                <td>${dep.limite || 80}%</td>
                <td class="text-end">
                    <a href="/onboarding/departamentos/form?id=${dep.id}" class="btn btn-sm btn-outline-warning me-1"><i class="bi bi-pencil"></i></a>
                </td>
            `;
            tabelaDepartamentos.appendChild(tr);
        });
    }

    // Ao selecionar departamento para edição, autopreencher formulário
    if (selectDepartamento) {
        selectDepartamento.addEventListener("change", (e) => {
            const depId = e.target.value;
            const dep = departamentosCache.find(d => String(d.id) === String(depId) || d.nome === depId);
            if (dep) {
                if (nomeInput) nomeInput.value = dep.nome || dep.nomeDepartamento || "";
                if (liderInput) liderInput.value = dep.lider || "";
                if (orcamentoInput) orcamentoInput.value = dep.orcamento || "";
                if (limiteInput) limiteInput.value = dep.limite || "";
                if (emailAlertaInput) emailAlertaInput.value = dep.emailAlerta || "";
                if (descricaoInput) descricaoInput.value = dep.descricao || "";
            }
        });
    }
});
