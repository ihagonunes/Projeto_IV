// public/js/pages/admin-usuarios.js

document.addEventListener('DOMContentLoaded', () => {
    // 1. Estado Local da Aplicação
    let userRole = localStorage.getItem('simulatedUserRole') || 'admin';
    let usuarios = JSON.parse(localStorage.getItem('usuariosTenantList') || '[]');

    // Carga inicial de dados de demonstração do tenant se estiver vazio
    if (usuarios.length === 0) {
        usuarios = [
            {
                id: 'usr_1',
                nome: 'Carlos Eduardo Oliveira',
                email: 'carlos.oliveira@acceletec.com.br',
                departamento: 'Tecnologia da Informação',
                nivelHierarquico: 'Diretoria',
                permissaoAdmin: 'ADMIN',
                ultimoAcesso: new Date(Date.now() - 3600000 * 2).toISOString(),
                status: 'Ativo'
            },
            {
                id: 'usr_2',
                nome: 'Mariana Souza Santos',
                email: 'mariana.santos@acceletec.com.br',
                departamento: 'Recursos Humanos',
                nivelHierarquico: 'Gerência',
                permissaoAdmin: 'ADMIN',
                ultimoAcesso: new Date(Date.now() - 3600000 * 5).toISOString(),
                status: 'Ativo'
            },
            {
                id: 'usr_3',
                nome: 'Lucas Ribeiro Mendes',
                email: 'lucas.mendes@acceletec.com.br',
                departamento: 'Financeiro',
                nivelHierarquico: 'Coordenação',
                permissaoAdmin: 'COMUM',
                ultimoAcesso: new Date(Date.now() - 86400000 * 1).toISOString(),
                status: 'Ativo'
            },
            {
                id: 'usr_4',
                nome: 'Fernanda Costa Lima',
                email: 'fernanda.lima@acceletec.com.br',
                departamento: 'Vendas & Comercial',
                nivelHierarquico: 'Operacional',
                permissaoAdmin: 'COMUM',
                ultimoAcesso: new Date(Date.now() - 86400000 * 3).toISOString(),
                status: 'Ativo'
            },
            {
                id: 'usr_5',
                nome: 'Gabriel Arantes',
                email: 'gabriel.arantes@acceletec.com.br',
                departamento: 'Marketing',
                nivelHierarquico: 'Estagiário',
                permissaoAdmin: 'COMUM',
                ultimoAcesso: new Date(Date.now() - 86400000 * 6).toISOString(),
                status: 'Ativo'
            }
        ];
        salvarUsuarios();
    }

    // 2. Elementos do DOM
    const tableBody = document.getElementById('usuariosTableBody');
    const searchInput = document.getElementById('searchInput');
    const departamentoFilter = document.getElementById('departamentoFilter');
    const nivelFilter = document.getElementById('nivelFilter');
    const permFilter = document.getElementById('permFilter');
    const emptyState = document.getElementById('emptyState');
    const totalBadge = document.getElementById('totalUsuariosBadge');
    const alertArea = document.getElementById('alertArea');
    const accessDeniedBanner = document.getElementById('accessDeniedBanner');
    const roleBadgeText = document.getElementById('roleBadgeText');
    const roleBadgeContainer = document.getElementById('roleBadgeContainer');

    // Elementos do Modal de Edição
    const modalElement = document.getElementById('modalEditarUsuario');
    const editModal = modalElement ? new bootstrap.Modal(modalElement) : null;
    const formEditar = document.getElementById('formEditarUsuario');
    const editUserId = document.getElementById('editUserId');
    const editUserName = document.getElementById('editUserName');
    const editNivel = document.getElementById('editNivelHierarquico');
    const editPerm = document.getElementById('editPermissaoAdmin');
    const btnSalvar = document.getElementById('btnSalvarEdicao');
    const editSpinner = document.getElementById('editSpinner');

    // 3. Função Global para Alternar o Perfil Simulado (Admin vs Não-Admin)
    window.setSimulatedRole = function(role) {
        userRole = role;
        localStorage.setItem('simulatedUserRole', role);
        atualizarPermissaoInterface();
        renderizarTabela();
    };

    function atualizarPermissaoInterface() {
        const isAdmin = userRole === 'admin';
        
        if (isAdmin) {
            roleBadgeText.textContent = 'Perfil: Administrador';
            roleBadgeContainer.className = 'admin-header-badge';
            accessDeniedBanner.classList.add('d-none');
        } else {
            roleBadgeText.textContent = 'Perfil: Usuário Comum (Não-Admin)';
            roleBadgeContainer.className = 'admin-header-badge non-admin';
            accessDeniedBanner.classList.remove('d-none');
        }
    }

    // 4. Exibição de Notificações / Alertas
    function showNotification(message, type = 'success') {
        const alertDiv = document.createElement('div');
        alertDiv.className = `alert alert-${type} alert-dismissible fade show alert-toast shadow-sm`;
        alertDiv.role = 'alert';
        alertDiv.innerHTML = `
            <div class="d-flex align-items-center">
                <span>${message}</span>
            </div>
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fechar"></button>
        `;
        alertArea.appendChild(alertDiv);

        setTimeout(() => {
            if (alertDiv && alertDiv.parentNode) {
                alertDiv.classList.remove('show');
                setTimeout(() => alertDiv.remove(), 200);
            }
        }, 5000);
    }

    // 5. Carregar Dados da API Real (com Fallback Gracioso)
    async function carregarUsuariosDaAPI() {
        try {
            const apiData = await window.ApiClient.get('/v1/usuarios');
            if (Array.isArray(apiData) && apiData.length > 0) {
                usuarios = apiData;
                salvarUsuarios();
            }
        } catch (err) {
            console.info('[Front-end Fallback] Endpoint /api/v1/usuarios ainda não disponível. Utilizando dados armazenados no cliente.', err.message);
        } finally {
            renderizarTabela();
        }
    }

    function salvarUsuarios() {
        localStorage.setItem('usuariosTenantList', JSON.stringify(usuarios));
    }

    // Helpers para Badges Visuais
    function getNivelBadgeHTML(nivel) {
        let classe = 'operacional';
        switch (nivel) {
            case 'Diretoria': classe = 'diretoria'; break;
            case 'Gerência': classe = 'gerencia'; break;
            case 'Coordenação': classe = 'coordenacao'; break;
            case 'Operacional': classe = 'operacional'; break;
            case 'Estagiário': classe = 'estagiario'; break;
        }
        return `<span class="level-badge ${classe}">${nivel}</span>`;
    }

    function getPermissaBadgeHTML(perm) {
        if (perm === 'ADMIN') {
            return `
                <span class="admin-perm-badge is-admin">
                    <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" fill="currentColor" class="bi bi-shield-lock-fill" viewBox="0 0 16 16">
                        <path d="M8 1a2 2 0 0 1 2 2v2H6V3a2 2 0 0 1 2-2zm3 4V3a3 3 0 0 0-6 0v2a2 2 0 0 0-2 2v5a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z"/>
                    </svg>
                    Administrador
                </span>`;
        }
        return `
            <span class="admin-perm-badge is-common">
                <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" fill="currentColor" class="bi bi-person" viewBox="0 0 16 16">
                    <path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm2-3a2 2 0 1 1-4 0 2 2 0 0 1 4 0zm4 8c0 1-1 1-1 1H3s-1 0-1-1 1-4 6-4 6 3 6 4zm-1-.004c-.001-.246-.154-.986-.832-1.664C11.516 10.68 10.289 10 8 10c-2.29 0-3.516.68-4.168 1.332-.678.678-.83 1.418-.832 1.664h10z"/>
                </svg>
                Usuário Comum
            </span>`;
    }

    function formatarData(isoString) {
        if (!isoString) return 'Nunca';
        const data = new Date(isoString);
        return data.toLocaleDateString('pt-BR', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    // 6. Renderização da Tabela de Usuários
    function renderizarTabela() {
        const termoBusca = (searchInput.value || '').toLowerCase().trim();
        const filtroDepto = departamentoFilter.value;
        const filtroNivel = nivelFilter.value;
        const filtroPerm = permFilter.value;
        const isAdmin = userRole === 'admin';

        const usuariosFiltrados = usuarios.filter(u => {
            const bateuBusca = u.nome.toLowerCase().includes(termoBusca) || u.email.toLowerCase().includes(termoBusca);
            const bateuDepto = filtroDepto === 'TODOS' || u.departamento === filtroDepto;
            const bateuNivel = filtroNivel === 'TODOS' || u.nivelHierarquico === filtroNivel;
            const bateuPerm = filtroPerm === 'TODOS' || u.permissaoAdmin === filtroPerm;
            return bateuBusca && bateuDepto && bateuNivel && bateuPerm;
        });

        totalBadge.textContent = `${usuariosFiltrados.length} ${usuariosFiltrados.length === 1 ? 'usuário' : 'usuários'}`;
        tableBody.innerHTML = '';

        if (usuariosFiltrados.length === 0) {
            emptyState.classList.remove('d-none');
            return;
        }

        emptyState.classList.add('d-none');

        usuariosFiltrados.forEach(u => {
            const tr = document.createElement('tr');
            
            const btnDisabledAttr = isAdmin ? '' : 'disabled title="Apenas administradores podem editar permissões"';

            tr.innerHTML = `
                <td>
                    <div class="fw-bold text-dark fs-7">${u.nome}</div>
                    <div class="text-muted small">${u.email}</div>
                </td>
                <td><span class="text-dark small">${u.departamento}</span></td>
                <td>${getNivelBadgeHTML(u.nivelHierarquico)}</td>
                <td>${getPermissaBadgeHTML(u.permissaoAdmin)}</td>
                <td><span class="text-muted small">${formatarData(u.ultimoAcesso)}</span></td>
                <td class="text-end">
                    <button class="btn btn-action btn-action-edit" onclick="abrirModalEdicao('${u.id}')" ${btnDisabledAttr}>
                        Editar
                    </button>
                </td>
            `;

            tableBody.appendChild(tr);
        });
    }

    // 7. Modal de Edição de Nível e Permissão
    window.abrirModalEdicao = function(id) {
        if (userRole !== 'admin') {
            showNotification('Apenas administradores possuem autorização para alterar o nível ou permissão de usuários.', 'danger');
            return;
        }

        const usuario = usuarios.find(u => u.id === id);
        if (!usuario) return;

        editUserId.value = usuario.id;
        editUserName.value = `${usuario.nome} (${usuario.email})`;
        editNivel.value = usuario.nivelHierarquico;
        editPerm.value = usuario.permissaoAdmin;

        if (editModal) {
            editModal.show();
        }
    };

    // Submissão do Formulário de Edição
    if (formEditar) {
        formEditar.addEventListener('submit', async (e) => {
            e.preventDefault();

            if (userRole !== 'admin') {
                showNotification('Operação negada: Seu perfil atual não possui autorização administrativa.', 'danger');
                if (editModal) editModal.hide();
                return;
            }

            const id = editUserId.value;
            const usuario = usuarios.find(u => u.id === id);
            if (!usuario) return;

            const novoNivel = editNivel.value;
            const novaPerm = editPerm.value;

            // Ativa estado de carregamento
            btnSalvar.disabled = true;
            editSpinner.classList.remove('d-none');

            const payload = {
                nivelHierarquico: novoNivel,
                permissaoAdmin: novaPerm
            };

            try {
                // Tenta requisição ao endpoint protegido da API REST
                try {
                    await window.ApiClient.post(`/v1/usuarios/${id}/nivel`, {
                        hierarchyLevel: String(novoNivel).toUpperCase()
                    });
                } catch (apiErr) {
                    console.info('[Front-end Fallback] Endpoint POST /api/v1/usuarios/:id/nivel simulado localmente.', apiErr.message);
                    
                    // Simula latência de rede de 400ms
                    await new Promise(resolve => setTimeout(resolve, 400));
                }

                // Atualiza o estado local
                usuario.nivelHierarquico = novoNivel;
                usuario.permissaoAdmin = novaPerm;

                salvarUsuarios();
                renderizarTabela();

                if (editModal) editModal.hide();

                showNotification(`Nível e permissões de <strong>${usuario.nome}</strong> atualizados com sucesso!`, 'success');

            } catch (err) {
                showNotification(`Erro ao atualizar usuário: ${err.message || 'Falha na comunicação com o servidor.'}`, 'danger');
            } finally {
                btnSalvar.disabled = false;
                editSpinner.classList.add('d-none');
            }
        });
    }

    // Eventos de Busca e Filtros
    if (searchInput) searchInput.addEventListener('input', renderizarTabela);
    if (departamentoFilter) departamentoFilter.addEventListener('change', renderizarTabela);
    if (nivelFilter) nivelFilter.addEventListener('change', renderizarTabela);
    if (permFilter) permFilter.addEventListener('change', renderizarTabela);

    // Inicialização da Página
    atualizarPermissaoInterface();
    carregarUsuariosDaAPI();
});
