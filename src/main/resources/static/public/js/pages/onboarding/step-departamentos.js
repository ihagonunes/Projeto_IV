/**
 * step-departamentos.js — Wizard de departamentos ligado ao backend.
 *
 * Consome o CRUD real em `/api/v1/departamentos` via `window.ApiClient`
 * (base `/api` + `/v1/...`), respeitando o contrato do backend:
 *   { id, name, description, leaderUserId, monthlyBudget, status }
 *
 * O tenant vem do JWT no backend — nunca do frontend. A autorização
 * (DEPARTMENT:READ/MANAGE) é aplicada no backend (ADR-34).
 */
document.addEventListener("DOMContentLoaded", async () => {
    const client = window.ApiClient;
    const select = document.getElementById("selecionarDepartamento");
    const tabela = document.getElementById("departamentosTableBody");
    const form = document.getElementById("departamentoForm");
    const nomeInput = document.getElementById("nomeDepartamento");
    const orcamentoInput = document.getElementById("orcamentoMensal");
    const descricaoInput = document.getElementById("descricao");
    const mensagem = document.getElementById("departamentosMensagem");

    const editingId = new URLSearchParams(window.location.search).get("id");
    let cache = [];

    await carregar();

    function avisar(texto, tipo) {
        if (!mensagem) return;
        mensagem.textContent = texto || "";
        mensagem.className = texto
            ? `alert alert-${tipo || "info"} mt-3`
            : "d-none";
    }

    async function carregar() {
        if (!client) {
            avisar("Cliente HTTP não carregado.", "warning");
            return;
        }
        try {
            const resposta = await client.get("/v1/departamentos");
            cache = Array.isArray(resposta) ? resposta : (resposta?.content || []);
            avisar("");
        } catch (err) {
            cache = [];
            avisar(err?.message || "Não foi possível carregar os departamentos.", "warning");
        }
        if (select) popularSelect(cache);
        if (tabela) popularTabela(cache);
        if (editingId) preencherParaEdicao(editingId);
    }

    function popularSelect(lista) {
        select.innerHTML = "";
        if (!lista.length) {
            select.innerHTML = '<option value="" disabled selected>Nenhum departamento cadastrado ainda...</option>';
            return;
        }
        select.innerHTML = '<option value="" disabled selected>Selecione um departamento para editar...</option>';
        lista.forEach((dep) => {
            const option = document.createElement("option");
            option.value = dep.id;
            option.textContent = dep.name;
            select.appendChild(option);
        });
    }

    function popularTabela(lista) {
        tabela.innerHTML = "";
        if (!lista.length) {
            tabela.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">'
                + 'Nenhum departamento cadastrado para esta empresa.</td></tr>';
            return;
        }
        lista.forEach((dep) => {
            const tr = document.createElement("tr");
            const orcamento = dep.monthlyBudget == null
                ? "—"
                : "R$ " + Number(dep.monthlyBudget).toLocaleString("pt-BR", { minimumFractionDigits: 2 });
            tr.innerHTML = `
                <td>${dep.name}</td>
                <td>${dep.description || "—"}</td>
                <td>${orcamento}</td>
                <td><span class="badge ${dep.status === "ACTIVE" ? "text-bg-success" : "text-bg-secondary"}">${dep.status}</span></td>
                <td class="text-end">
                    <a href="/onboarding/departamentos/form?id=${dep.id}" class="btn btn-sm btn-outline-warning me-1"><i class="bi bi-pencil"></i></a>
                    <button type="button" class="btn btn-sm btn-outline-danger" data-acao="excluir" data-id="${dep.id}"><i class="bi bi-trash"></i></button>
                </td>`;
            tabela.appendChild(tr);
        });

        tabela.querySelectorAll('[data-acao="excluir"]').forEach((botao) => {
            botao.addEventListener("click", () => excluir(botao.dataset.id));
        });
    }

    function preencherParaEdicao(id) {
        const dep = cache.find((d) => String(d.id) === String(id));
        if (!dep) return;
        if (nomeInput) nomeInput.value = dep.name || "";
        if (orcamentoInput) orcamentoInput.value = dep.monthlyBudget ?? "";
        if (descricaoInput) descricaoInput.value = dep.description || "";
    }

    if (select) {
        select.addEventListener("change", (e) => preencherParaEdicao(e.target.value));
    }

    async function excluir(id) {
        if (!client || !window.confirm("Remover este departamento?")) return;
        try {
            await client.delete(`/v1/departamentos/${id}`);
            avisar("Departamento removido.", "success");
            await carregar();
        } catch (err) {
            avisar(err?.message || "Não foi possível remover o departamento.", "danger");
        }
    }

    if (form) {
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            form.classList.add("was-validated");
            if (!form.checkValidity()) return;

            const payload = {
                name: nomeInput.value.trim(),
                description: descricaoInput?.value?.trim() || null,
                monthlyBudget: orcamentoInput?.value ? Number(orcamentoInput.value) : null
            };

            try {
                if (editingId) {
                    await client.put(`/v1/departamentos/${editingId}`, payload);
                    avisar("Departamento atualizado.", "success");
                } else {
                    await client.post("/v1/departamentos", payload);
                    avisar("Departamento criado.", "success");
                }
                await carregar();
            } catch (err) {
                avisar(err?.message || "Não foi possível salvar o departamento.", "danger");
            }
        });
    }
});
