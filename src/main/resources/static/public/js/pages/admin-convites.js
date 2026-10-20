// public/js/pages/admin-convites.js
// Gestão de convites ligada ao backend (A9/SPEC-01 §7.2).
// A interface é guiada por GET /api/v1/usuarios/me/permissoes; o backend
// continua sendo a única fonte de autorização (ADR-34).

document.addEventListener("DOMContentLoaded", () => {
    const client = window.ApiClient;

    let convites = [];
    let departamentos = {};
    let papeis = {};
    let podeGerenciar = false;

    const form = document.getElementById("formEmitirConvite");
    const inputEmail = document.getElementById("emailConvite");
    const selectDepartamento = document.getElementById("departamentoConvite");
    const selectPapel = document.getElementById("papelConvite");
    const btnEnviar = document.getElementById("btnEnviarConvite");
    const submitSpinner = document.getElementById("submitSpinner");
    const btnLimpar = document.getElementById("btnLimparForm");
    const tableBody = document.getElementById("convitesTableBody");
    const searchInput = document.getElementById("searchInput");
    const statusFilter = document.getElementById("statusFilter");
    const emptyState = document.getElementById("emptyState");
    const totalBadge = document.getElementById("totalConvitesBadge");
    const alertArea = document.getElementById("alertArea");
    const accessDeniedBanner = document.getElementById("accessDeniedBanner");
    const roleBadgeText = document.getElementById("roleBadgeText");
    const roleBadgeContainer = document.getElementById("roleBadgeContainer");
    const linkPanel = document.getElementById("inviteLinkPanel");
    const linkInput = document.getElementById("inviteLinkInput");
    const btnCopiarLink = document.getElementById("btnCopiarLink");

    const STATUS_LABEL = {
        PENDING: "Pendente",
        ACCEPTED: "Aceito",
        REVOKED: "Cancelado"
    };

    const STATUS_CLASS = {
        PENDING: "status-badge pending",
        ACCEPTED: "status-badge accepted",
        REVOKED: "status-badge cancelled"
    };

    function showNotification(message, type = "success") {
        if (!alertArea) return;
        const alertDiv = document.createElement("div");
        alertDiv.className = `alert alert-${type} alert-dismissible fade show alert-toast shadow-sm`;
        alertDiv.role = "alert";
        alertDiv.innerHTML = `
            <div class="d-flex align-items-center"><span data-message></span></div>
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fechar"></button>`;
        // textContent: e-mail/mensagem do servidor nunca viram HTML (XSS).
        alertDiv.querySelector("[data-message]").textContent = message;
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
        if (err?.status === 403) {
            return "Você não tem permissão para gerenciar convites.";
        }
        return err?.message || padrao;
    }

    function atualizarPermissaoInterface() {
        if (roleBadgeText) {
            roleBadgeText.textContent = podeGerenciar
                ? "Perfil: gestor de convites"
                : "Perfil: sem permissão de convites";
        }
        if (roleBadgeContainer) {
            roleBadgeContainer.className = podeGerenciar
                ? "admin-header-badge" : "admin-header-badge non-admin";
        }
        if (accessDeniedBanner) {
            accessDeniedBanner.classList.toggle("d-none", podeGerenciar);
        }
        if (form) {
            Array.from(form.elements).forEach((element) => {
                element.disabled = !podeGerenciar;
            });
        }
    }

    async function carregarPermissoes() {
        try {
            const acesso = await client.get("/v1/usuarios/me/permissoes");
            const permissoes = acesso?.permissoes || [];
            podeGerenciar = permissoes.includes("INVITE:MANAGE");
            const papeisDoAtor = (acesso?.concessoes || []).map((c) => c.papel);
            if (roleBadgeText && papeisDoAtor.length) {
                roleBadgeText.textContent = `Perfil: ${papeisDoAtor.join(", ")}`;
            }
        } catch (err) {
            podeGerenciar = false;
            if (err?.status === 401) window.location.href = "/sessao-expirada";
        }
        atualizarPermissaoInterface();
    }

    async function carregarDepartamentos() {
        try {
            const resposta = await client.get("/v1/departamentos");
            const lista = Array.isArray(resposta) ? resposta : (resposta?.content || []);
            lista.forEach((dep) => {
                departamentos[dep.id] = dep.name;
                const option = document.createElement("option");
                option.value = dep.id;
                option.textContent = dep.name;
                selectDepartamento.appendChild(option);
            });
            selectDepartamento.options[0].textContent = "Selecione um departamento...";
        } catch (err) {
            selectDepartamento.options[0].textContent = "Departamentos indisponíveis";
        }
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
                    selectPapel.appendChild(option);
                });
            selectPapel.options[0].textContent = "Selecione o papel...";
        } catch (err) {
            selectPapel.options[0].textContent = "Papéis indisponíveis";
        }
    }

    async function carregarConvites() {
        try {
            const lista = await client.get("/v1/convites");
            convites = Array.isArray(lista) ? lista : [];
            renderizarTabela();
        } catch (err) {
            convites = [];
            renderizarTabela();
            showNotification(tratarErro(err, "Não foi possível carregar os convites."), "danger");
        }
    }

    function validarFormulario() {
        let valido = true;
        const email = inputEmail.value.trim();
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        inputEmail.classList.toggle("is-invalid", !email || !emailRegex.test(email));
        if (!email || !emailRegex.test(email)) valido = false;

        const papel = papeis[selectPapel.value];
        const exigeDepartamento = papel && papel.escopo === "DEPARTMENT";
        const semDepartamento = exigeDepartamento && !selectDepartamento.value;
        selectDepartamento.classList.toggle("is-invalid", semDepartamento);
        if (semDepartamento) valido = false;

        selectPapel.classList.toggle("is-invalid", !selectPapel.value);
        if (!selectPapel.value) valido = false;
        return valido;
    }

    [inputEmail, selectDepartamento, selectPapel].forEach((field) => {
        if (!field) return;
        field.addEventListener("input", () => field.classList.remove("is-invalid"));
        field.addEventListener("change", () => field.classList.remove("is-invalid"));
    });

    function linkDoConvite(email, token) {
        return `${window.location.origin}/aceitar-convite?token=${encodeURIComponent(token)}`
            + `&email=${encodeURIComponent(email || "")}`;
    }

    function mostrarLink(email, token) {
        if (!linkPanel || !linkInput) return;
        linkInput.value = linkDoConvite(email, token);
        linkPanel.classList.remove("d-none");
    }

    if (btnCopiarLink) {
        btnCopiarLink.addEventListener("click", async () => {
            try {
                await navigator.clipboard.writeText(linkInput.value);
                showNotification("Link copiado para a área de transferência.", "success");
            } catch (e) {
                linkInput.select();
                showNotification("Copie o link manualmente.", "info");
            }
        });
    }

    if (form) {
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (!podeGerenciar || !validarFormulario()) {
                showNotification("Preencha corretamente os campos obrigatórios.", "warning");
                return;
            }

            btnEnviar.disabled = true;
            submitSpinner.classList.remove("d-none");
            const papel = papeis[selectPapel.value];
            try {
                const resposta = await client.post("/v1/convites", {
                    email: inputEmail.value.trim(),
                    roleKey: selectPapel.value,
                    departmentId: papel?.escopo === "DEPARTMENT" ? selectDepartamento.value : null
                });
                mostrarLink(resposta.email, resposta.token);
                showNotification(`Convite emitido para ${resposta.email}.`, "success");
                form.reset();
                selectPapel.value = "";
                selectDepartamento.value = "";
                await carregarConvites();
            } catch (err) {
                showNotification(tratarErro(err, "Não foi possível emitir o convite."), "danger");
            } finally {
                btnEnviar.disabled = false;
                submitSpinner.classList.add("d-none");
            }
        });
    }

    if (btnLimpar) {
        btnLimpar.addEventListener("click", () => {
            form.reset();
            [inputEmail, selectDepartamento, selectPapel]
                .forEach((field) => field.classList.remove("is-invalid"));
        });
    }

    function formatarData(isoString) {
        if (!isoString) return "—";
        const data = new Date(isoString);
        if (Number.isNaN(data.getTime())) return "—";
        return data.toLocaleDateString("pt-BR", {
            day: "2-digit", month: "2-digit", year: "numeric",
            hour: "2-digit", minute: "2-digit"
        });
    }

    function renderizarTabela() {
        const termo = (searchInput?.value || "").toLowerCase().trim();
        const status = statusFilter?.value || "TODOS";

        const filtrados = convites.filter((convite) => {
            const nomeDepartamento = departamentos[convite.departmentId] || "";
            const bateuBusca = String(convite.email || "").toLowerCase().includes(termo)
                || nomeDepartamento.toLowerCase().includes(termo);
            const bateuStatus = status === "TODOS" || convite.status === status;
            return bateuBusca && bateuStatus;
        });

        if (totalBadge) {
            totalBadge.textContent = `${filtrados.length} `
                + `${filtrados.length === 1 ? "convite" : "convites"}`;
        }
        if (!tableBody) return;
        tableBody.innerHTML = "";

        if (!filtrados.length) {
            if (emptyState) emptyState.classList.remove("d-none");
            return;
        }
        if (emptyState) emptyState.classList.add("d-none");

        filtrados.forEach((convite) => {
            const acoes = convite.status === "PENDING"
                ? `<button class="btn btn-action btn-action-resend me-1" data-acao="reenviar" data-id="${convite.id}">Reenviar</button>
                   <button class="btn btn-action btn-action-cancel" data-acao="cancelar" data-id="${convite.id}">Cancelar</button>`
                : '<span class="text-muted small">—</span>';
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td class="fw-medium text-dark">${escapar(convite.email)}</td>
                <td>${escapar(departamentos[convite.departmentId] || "—")}</td>
                <td>${escapar(convite.hierarchyLevel || "—")}</td>
                <td>${formatarData(convite.createdAt)}</td>
                <td><span class="${STATUS_CLASS[convite.status] || "status-badge pending"}">
                    ${STATUS_LABEL[convite.status] || convite.status}</span></td>
                <td class="text-end">${acoes}</td>`;
            tableBody.appendChild(tr);
        });
    }

    tableBody?.addEventListener("click", async (event) => {
        const botao = event.target.closest("button[data-acao]");
        if (!botao) return;
        const id = botao.dataset.id;
        const convite = convites.find((item) => item.id === id);
        if (!convite) return;

        if (botao.dataset.acao === "reenviar") {
            try {
                const resposta = await client.post(`/v1/convites/${id}/reenviar`);
                mostrarLink(resposta.email, resposta.token);
                showNotification(`Convite reenviado para ${resposta.email}.`, "info");
                await carregarConvites();
            } catch (err) {
                showNotification(tratarErro(err, "Não foi possível reenviar o convite."), "danger");
            }
        } else if (botao.dataset.acao === "cancelar") {
            if (!window.confirm(`Cancelar o convite de ${convite.email}?`)) return;
            try {
                await client.delete(`/v1/convites/${id}`);
                showNotification(`Convite de ${convite.email} cancelado.`, "warning");
                await carregarConvites();
            } catch (err) {
                showNotification(tratarErro(err, "Não foi possível cancelar o convite."), "danger");
            }
        }
    });

    if (searchInput) searchInput.addEventListener("input", renderizarTabela);
    if (statusFilter) statusFilter.addEventListener("change", renderizarTabela);

    carregarPermissoes()
        .then(async () => {
            await Promise.all([carregarDepartamentos(), carregarPapeis()]);
            await carregarConvites();
        });
});
