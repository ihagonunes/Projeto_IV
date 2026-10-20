// public/js/pages/admin-usuarios.js
// Gestão de usuários e concessões (A9/SPEC-01 §6/§7). Menus e botões são
// guiados por GET /api/v1/usuarios/me/permissoes; o backend continua decidindo.

document.addEventListener("DOMContentLoaded", () => {
    const client = window.ApiClient;

    let usuarios = [];
    let departamentos = {};
    let papeis = {};
    let podeGerenciarUsuarios = false;
    let podeLerConcessoes = false;
    let podeGerenciarConcessoes = false;
    let usuarioConcessoes = null;

    const tableBody = document.getElementById("usuariosTableBody");
    const searchInput = document.getElementById("searchInput");
    const departamentoFilter = document.getElementById("departamentoFilter");
    const nivelFilter = document.getElementById("nivelFilter");
    const emptyState = document.getElementById("emptyState");
    const totalBadge = document.getElementById("totalUsuariosBadge");
    const alertArea = document.getElementById("alertArea");
    const accessDeniedBanner = document.getElementById("accessDeniedBanner");
    const roleBadgeText = document.getElementById("roleBadgeText");
    const roleBadgeContainer = document.getElementById("roleBadgeContainer");

    const modalElement = document.getElementById("modalEditarUsuario");
    const editModal = modalElement ? new bootstrap.Modal(modalElement) : null;
    const formEditar = document.getElementById("formEditarUsuario");
    const editUserId = document.getElementById("editUserId");
    const editUserName = document.getElementById("editUserName");
    const editNivel = document.getElementById("editNivelHierarquico");
    const editDepartamento = document.getElementById("editDepartamento");
    const btnSalvar = document.getElementById("btnSalvarEdicao");
    const editSpinner = document.getElementById("editSpinner");

    const concessoesModalElement = document.getElementById("modalConcessoes");
    const concessoesModal = concessoesModalElement ? new bootstrap.Modal(concessoesModalElement) : null;
    const concessoesSubtitle = document.getElementById("modalConcessoesSubtitle");
    const concessoesBody = document.getElementById("concessoesTableBody");
    const concessoesMensagem = document.getElementById("concessoesMensagem");
    const formConcessao = document.getElementById("formConcessao");
    const concessaoPapel = document.getElementById("concessaoPapel");
    const concessaoDepartamento = document.getElementById("concessaoDepartamento");

    const NIVEL_LABEL = {
        COLLABORATOR: "Colaborador",
        LEADER: "Líder",
        MANAGER: "Gestor",
        EXECUTIVE: "Executivo"
    };

    function showNotification(message, type = "success") {
        if (!alertArea) return;
        const alertDiv = document.createElement("div");
        alertDiv.className = `alert alert-${type} alert-dismissible fade show alert-toast shadow-sm`;
        alertDiv.role = "alert";
        alertDiv.innerHTML = `
            <div class="d-flex align-items-center"><span>${message}</span></div>
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fechar"></button>`;
        alertArea.appendChild(alertDiv);
        setTimeout(() => {
            alertDiv.classList.remove("show");
            setTimeout(() => alertDiv.remove(), 200);
        }, 5000);
    }

    function escapar(valor) {
        return String(valor ?? "")
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function tratarErro(err, padrao) {
        if (err?.status === 401) {
            window.location.href = "/sessao-expirada";
            return "Sua sessão expirou.";
        }
        if (err?.status === 403) return "Você não tem permissão para esta operação.";
        if (err?.status === 409) return err.message || "Operação conflita com o estado atual.";
        return err?.message || padrao;
    }

    async function carregarPermissoes() {
        try {
            const acesso = await client.get("/v1/usuarios/me/permissoes");
            const permissoes = acesso?.permissoes || [];
            podeGerenciarUsuarios = permissoes.includes("USER:MANAGE");
            podeLerConcessoes = permissoes.includes("ROLE:READ");
            podeGerenciarConcessoes = permissoes.includes("ROLE:MANAGE");
            const papeisDoAtor = (acesso?.concessoes || []).map((c) => c.papel);
            if (roleBadgeText) {
                roleBadgeText.textContent = papeisDoAtor.length
                    ? `Perfil: ${papeisDoAtor.join(", ")}`
                    : "Perfil: usuário";
            }
        } catch (err) {
            if (err?.status === 401) window.location.href = "/sessao-expirada";
        }
        atualizarPermissaoInterface();
    }

    function atualizarPermissaoInterface() {
        if (roleBadgeContainer) {
            roleBadgeContainer.className = podeGerenciarUsuarios
                ? "admin-header-badge" : "admin-header-badge non-admin";
        }
        if (accessDeniedBanner) {
            accessDeniedBanner.classList.toggle("d-none", podeGerenciarUsuarios);
        }
    }

    async function carregarDepartamentos() {
        try {
            const resposta = await client.get("/v1/departamentos");
            const lista = Array.isArray(resposta) ? resposta : (resposta?.content || []);
            lista.forEach((dep) => {
                departamentos[dep.id] = dep.name;
                adicionarOpcao(departamentoFilter, dep.id, dep.name);
                adicionarOpcao(editDepartamento, dep.id, dep.name);
                adicionarOpcao(concessaoDepartamento, dep.id, dep.name);
            });
        } catch (e) {
            // Sem DEPARTMENT:READ o filtro fica apenas em "Todos".
        }
    }

    function adicionarOpcao(select, valor, texto) {
        if (!select) return;
        const option = document.createElement("option");
        option.value = valor;
        option.textContent = texto;
        select.appendChild(option);
    }

    async function carregarPapeis() {
        try {
            const lista = await client.get("/v1/papeis");
            (Array.isArray(lista) ? lista : []).filter((role) => role.concedivel)
                .forEach((role) => {
                    papeis[role.key] = role;
                    const option = document.createElement("option");
                    option.value = role.key;
                    option.textContent = `${role.key} (${role.escopo})`;
                    if (concessaoPapel) concessaoPapel.appendChild(option);
                });
        } catch (e) {
            // Papéis indisponíveis: a concessão fica sem opções.
        }
    }

    async function carregarUsuarios() {
        try {
            const pagina = await client.get("/v1/usuarios?size=100");
            usuarios = Array.isArray(pagina) ? pagina : (pagina?.content || []);
            renderizarTabela();
        } catch (err) {
            usuarios = [];
            renderizarTabela();
            showNotification(tratarErro(err, "Não foi possível carregar os usuários."), "danger");
        }
    }

    function renderizarTabela() {
        const termo = (searchInput?.value || "").toLowerCase().trim();
        const filtroDepto = departamentoFilter?.value || "TODOS";
        const filtroNivel = nivelFilter?.value || "TODOS";

        const filtrados = usuarios.filter((usuario) => {
            const nomeDepartamento = departamentos[usuario.departmentId] || "";
            const bateuBusca = String(usuario.name || "").toLowerCase().includes(termo)
                || String(usuario.email || "").toLowerCase().includes(termo);
            const bateuDepto = filtroDepto === "TODOS" || usuario.departmentId === filtroDepto;
            const bateuNivel = filtroNivel === "TODOS" || usuario.hierarchyLevel === filtroNivel;
            return bateuBusca && bateuDepto && bateuNivel;
        });

        if (totalBadge) {
            totalBadge.textContent = `${filtrados.length} `
                + `${filtrados.length === 1 ? "usuário" : "usuários"}`;
        }
        if (!tableBody) return;
        tableBody.innerHTML = "";

        if (!filtrados.length) {
            if (emptyState) emptyState.classList.remove("d-none");
            return;
        }
        if (emptyState) emptyState.classList.add("d-none");

        filtrados.forEach((usuario) => {
            const acoes = [];
            if (podeGerenciarUsuarios) {
                acoes.push(`<button class="btn btn-action btn-action-edit me-1" data-acao="editar" data-id="${usuario.id}">Editar</button>`);
            }
            if (podeLerConcessoes) {
                acoes.push(`<button class="btn btn-action" data-acao="concessoes" data-id="${usuario.id}">Concessões</button>`);
            }
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>
                    <div class="fw-bold text-dark fs-7">${escapar(usuario.name || "—")}</div>
                    <div class="text-muted small">${escapar(usuario.email)}</div>
                </td>
                <td><span class="text-dark small">${escapar(departamentos[usuario.departmentId] || "—")}</span></td>
                <td><span class="level-badge">${NIVEL_LABEL[usuario.hierarchyLevel] || usuario.hierarchyLevel}</span></td>
                <td><span class="badge ${usuario.status === "ACTIVE" ? "text-bg-success" : "text-bg-secondary"}">
                    ${usuario.status === "ACTIVE" ? "Ativo" : "Inativo"}</span></td>
                <td class="text-end">${acoes.join("") || '<span class="text-muted small">—</span>'}</td>`;
            tableBody.appendChild(tr);
        });
    }

    tableBody?.addEventListener("click", (event) => {
        const botao = event.target.closest("button[data-acao]");
        if (!botao) return;
        const usuario = usuarios.find((item) => item.id === botao.dataset.id);
        if (!usuario) return;
        if (botao.dataset.acao === "editar") abrirModalEdicao(usuario);
        if (botao.dataset.acao === "concessoes") abrirConcessoes(usuario);
    });

    function abrirModalEdicao(usuario) {
        editUserId.value = usuario.id;
        editUserName.value = `${usuario.name || ""} (${usuario.email})`;
        editNivel.value = usuario.hierarchyLevel;
        editDepartamento.value = usuario.departmentId || "";
        if (editModal) editModal.show();
    }

    if (formEditar) {
        formEditar.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (!podeGerenciarUsuarios) {
                showNotification("Seu perfil não possui autorização para editar usuários.", "danger");
                return;
            }
            const id = editUserId.value;
            if (btnSalvar) btnSalvar.disabled = true;
            if (editSpinner) editSpinner.classList.remove("d-none");
            try {
                await client.post(`/v1/usuarios/${id}/nivel`, {
                    hierarchyLevel: editNivel.value
                });
                await client.request(`/v1/usuarios/${id}`, {
                    method: "PATCH",
                    body: JSON.stringify({ departmentId: editDepartamento.value || null })
                });
                if (editModal) editModal.hide();
                showNotification("Usuário atualizado.", "success");
                await carregarUsuarios();
            } catch (err) {
                showNotification(tratarErro(err, "Não foi possível atualizar o usuário."), "danger");
            } finally {
                if (btnSalvar) btnSalvar.disabled = false;
                if (editSpinner) editSpinner.classList.add("d-none");
            }
        });
    }

    function avisarConcessoes(texto, tipo) {
        if (!concessoesMensagem) return;
        concessoesMensagem.textContent = texto || "";
        concessoesMensagem.className = texto ? `alert alert-${tipo || "info"} mb-3` : "d-none";
    }

    async function abrirConcessoes(usuario) {
        usuarioConcessoes = usuario;
        if (concessoesSubtitle) {
            concessoesSubtitle.textContent = `${usuario.name || usuario.email} — ${usuario.email}`;
        }
        avisarConcessoes("");
        if (formConcessao) {
            Array.from(formConcessao.elements).forEach((element) => {
                element.disabled = !podeGerenciarConcessoes;
            });
        }
        if (concessoesModal) concessoesModal.show();
        await carregarConcessoes();
    }

    async function carregarConcessoes() {
        if (!usuarioConcessoes || !concessoesBody) return;
        try {
            const acesso = await client.get(`/v1/usuarios/${usuarioConcessoes.id}/permissoes`);
            const concessoes = acesso?.concessoes || [];
            if (!concessoes.length) {
                concessoesBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">'
                    + "Nenhuma concessão ativa.</td></tr>";
                return;
            }
            concessoesBody.innerHTML = concessoes.map((concessao) => `
                <tr>
                    <td>${concessao.papel}</td>
                    <td>${concessao.escopo}</td>
                    <td>${escapar(departamentos[concessao.departmentId] || "—")}</td>
                    <td>${concessao.origem}</td>
                    <td class="text-end">${podeGerenciarConcessoes
                        ? `<button class="btn btn-sm btn-outline-danger" data-revogar="${concessao.id}">Revogar</button>`
                        : "—"}</td>
                </tr>`).join("");
        } catch (err) {
            concessoesBody.innerHTML = "";
            avisarConcessoes(tratarErro(err, "Não foi possível carregar as concessões."), "danger");
        }
    }

    concessoesBody?.addEventListener("click", async (event) => {
        const botao = event.target.closest("button[data-revogar]");
        if (!botao || !usuarioConcessoes || !podeGerenciarConcessoes) return;
        try {
            await client.delete(
                `/v1/usuarios/${usuarioConcessoes.id}/concessoes/${botao.dataset.revogar}`);
            avisarConcessoes("Concessão revogada.", "success");
            await carregarConcessoes();
        } catch (err) {
            avisarConcessoes(tratarErro(err, "Não foi possível revogar a concessão."), "danger");
        }
    });

    if (formConcessao) {
        formConcessao.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (!usuarioConcessoes || !podeGerenciarConcessoes) return;
            const papel = papeis[concessaoPapel.value];
            const departmentId = papel?.escopo === "DEPARTMENT"
                ? (concessaoDepartamento.value || null)
                : null;
            try {
                await client.post(`/v1/usuarios/${usuarioConcessoes.id}/concessoes`, {
                    roleKey: concessaoPapel.value,
                    departmentId
                });
                avisarConcessoes("Concessão atribuída.", "success");
                await carregarConcessoes();
            } catch (err) {
                avisarConcessoes(tratarErro(err, "Não foi possível atribuir a concessão."), "danger");
            }
        });
    }

    if (searchInput) searchInput.addEventListener("input", renderizarTabela);
    if (departamentoFilter) departamentoFilter.addEventListener("change", renderizarTabela);
    if (nivelFilter) nivelFilter.addEventListener("change", renderizarTabela);

    carregarPermissoes()
        .then(async () => {
            await Promise.all([carregarDepartamentos(), carregarPapeis()]);
            await carregarUsuarios();
        });
});
