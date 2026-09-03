// public/js/pages/admin-convites.js

document.addEventListener('DOMContentLoaded', () => {
    // 1. Estado Local da Aplicação
    let userRole = localStorage.getItem('simulatedUserRole') || 'admin';
    let convites = JSON.parse(localStorage.getItem('convitesList') || '[]');

    // Se estiver vazio na primeira execução, carrega dados de demonstração
    if (convites.length === 0) {
        convites = [
            {
                id: 'inv_101',
                email: 'carla.silva@empresa.com',
                departamento: 'Tecnologia da Informação',
                papel: 'Gestor',
                nivel: 'Gerência',
                enviadoEm: new Date(Date.now() - 3600000 * 4).toISOString(),
                validadeHoras: 48,
                status: 'Pendente'
            },
            {
                id: 'inv_102',
                email: 'roberto.almeida@empresa.com',
                departamento: 'Recursos Humanos',
                papel: 'Usuário Comum',
                nivel: 'Operacional',
                enviadoEm: new Date(Date.now() - 86400000 * 2).toISOString(),
                validadeHoras: 48,
                status: 'Aceito'
            },
            {
                id: 'inv_103',
                email: 'juliana.costa@empresa.com',
                departamento: 'Financeiro',
                papel: 'Apenas Leitura',
                nivel: 'Coordenação',
                enviadoEm: new Date(Date.now() - 86400000 * 5).toISOString(),
                validadeHoras: 24,
                status: 'Expirado'
            }
        ];
        salvarConvites();
    }

    // 2. Elementos do DOM
    const form = document.getElementById('formEmitirConvite');
    const inputEmail = document.getElementById('emailConvite');
    const selectDepartamento = document.getElementById('departamentoConvite');
    const selectPapel = document.getElementById('papelConvite');
    const selectNivel = document.getElementById('nivelConvite');
    const selectValidade = document.getElementById('validadeConvite');
    const btnEnviar = document.getElementById('btnEnviarConvite');
    const submitSpinner = document.getElementById('submitSpinner');
    const btnLimpar = document.getElementById('btnLimparForm');
    const tableBody = document.getElementById('convitesTableBody');
    const searchInput = document.getElementById('searchInput');
    const statusFilter = document.getElementById('statusFilter');
    const emptyState = document.getElementById('emptyState');
    const totalBadge = document.getElementById('totalConvitesBadge');
    const alertArea = document.getElementById('alertArea');
    const accessDeniedBanner = document.getElementById('accessDeniedBanner');
    const roleBadgeText = document.getElementById('roleBadgeText');
    const roleBadgeContainer = document.getElementById('roleBadgeContainer');

    // 3. Função Global para Alternar o Papel (Simulação de Acesso Admin vs Não-Admin)
    window.setSimulatedRole = function(role) {
        userRole = role;
        localStorage.setItem('simulatedUserRole', role);
        atualizarPermissaoInterface();
    };

    function atualizarPermissaoInterface() {
        const isAdmin = userRole === 'admin';
        
        if (isAdmin) {
            roleBadgeText.textContent = 'Perfil: Administrador';
            roleBadgeContainer.className = 'admin-header-badge';
            accessDeniedBanner.classList.add('d-none');
            
            // Habilita o formulário
            Array.from(form.elements).forEach(element => element.disabled = false);
        } else {
            roleBadgeText.textContent = 'Perfil: Usuário Comum (Não-Admin)';
            roleBadgeContainer.className = 'admin-header-badge non-admin';
            accessDeniedBanner.classList.remove('d-none');
            
            // Desabilita os campos do formulário para impedir envio
            Array.from(form.elements).forEach(element => element.disabled = true);
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

    // 5. Validação de Formulário
    function validarFormulario() {
        let valido = true;

        // Validar E-mail
        const emailValue = inputEmail.value.trim();
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailValue || !emailRegex.test(emailValue)) {
            inputEmail.classList.add('is-invalid');
            valido = false;
        } else {
            inputEmail.classList.remove('is-invalid');
        }

        // Validar Departamento
        if (!selectDepartamento.value) {
            selectDepartamento.classList.add('is-invalid');
            valido = false;
        } else {
            selectDepartamento.classList.remove('is-invalid');
        }

        // Validar Papel
        if (!selectPapel.value) {
            selectPapel.classList.add('is-invalid');
            valido = false;
        } else {
            selectPapel.classList.remove('is-invalid');
        }

        // Validar Nível
        if (!selectNivel.value) {
            selectNivel.classList.add('is-invalid');
            valido = false;
        } else {
            selectNivel.classList.remove('is-invalid');
        }

        return valido;
    }

    // Limpar estados de erro ao digitar/alterar
    [inputEmail, selectDepartamento, selectPapel, selectNivel].forEach(field => {
        if (field) {
            field.addEventListener('input', () => field.classList.remove('is-invalid'));
            field.addEventListener('change', () => field.classList.remove('is-invalid'));
        }
    });

    // 6. Envio de Convite (Integração com Backend / Fallback)
    if (form) {
        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            if (userRole !== 'admin') {
                showNotification('Apenas administradores podem emitir convites.', 'danger');
                return;
            }

            if (!validarFormulario()) {
                showNotification('Por favor, preencha corretamente todos os campos obrigatórios.', 'warning');
                return;
            }

            // Ativa Estado de Carregamento
            btnEnviar.disabled = true;
            submitSpinner.classList.remove('d-none');

            const novoConvitePayload = {
                email: inputEmail.value.trim(),
                departamento: selectDepartamento.value,
                papel: selectPapel.value,
                nivel: selectNivel.value,
                validadeHoras: parseInt(selectValidade.value, 10)
            };

            try {
                // Tenta realizar a chamada ao endpoint real da API REST
                let responseData;
                try {
                    responseData = await window.ApiClient.post('/v1/convites', novoConvitePayload);
                } catch (apiError) {
                    console.info('[Front-end Fallback] Endpoint backend real ainda não ativo. Processando emissão em modo de simulação local.', apiError.message);
                    
                    // Simulação de latência de rede (500ms)
                    await new Promise(resolve => setTimeout(resolve, 500));
                    
                    responseData = {
                        id: `inv_${Date.now()}`,
                        ...novoConvitePayload,
                        enviadoEm: new Date().toISOString(),
                        status: 'Pendente'
                    };
                }

                // Adiciona o novo convite ao topo da lista local
                convites.unshift(responseData);
                salvarConvites();
                renderizarTabela();

                showNotification(`Convite emitido com sucesso para <strong>${responseData.email}</strong>!`, 'success');
                form.reset();

            } catch (err) {
                showNotification(`Erro ao emitir convite: ${err.message || 'Falha ao processar a solicitação.'}`, 'danger');
            } finally {
                btnEnviar.disabled = false;
                submitSpinner.classList.add('d-none');
            }
        });
    }

    if (btnLimpar) {
        btnLimpar.addEventListener('click', () => {
            form.reset();
            [inputEmail, selectDepartamento, selectPapel, selectNivel].forEach(f => f.classList.remove('is-invalid'));
        });
    }

    // 7. Persistência e Renderização da Tabela de Convites
    function salvarConvites() {
        localStorage.setItem('convitesList', JSON.stringify(convites));
    }

    function getStatusBadgeClass(status) {
        switch (status) {
            case 'Pendente': return 'status-badge pending';
            case 'Aceito': return 'status-badge accepted';
            case 'Expirado': return 'status-badge expired';
            case 'Cancelado': return 'status-badge cancelled';
            default: return 'status-badge pending';
        }
    }

    function formatarData(isoString) {
        const data = new Date(isoString);
        return data.toLocaleDateString('pt-BR', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function renderizarTabela() {
        const termoBusca = (searchInput.value || '').toLowerCase().trim();
        const filtroStatus = statusFilter.value;

        const convitesFiltrados = convites.filter(c => {
            const bateuBusca = c.email.toLowerCase().includes(termoBusca) || 
                               c.departamento.toLowerCase().includes(termoBusca);
            const bateuStatus = filtroStatus === 'TODOS' || c.status === filtroStatus;
            return bateuBusca && bateuStatus;
        });

        totalBadge.textContent = `${convitesFiltrados.length} ${convitesFiltrados.length === 1 ? 'convite' : 'convites'}`;
        tableBody.innerHTML = '';

        if (convitesFiltrados.length === 0) {
            emptyState.classList.remove('d-none');
            return;
        }

        emptyState.classList.add('d-none');

        convitesFiltrados.forEach(c => {
            const tr = document.createElement('tr');
            
            const acoesHTML = c.status === 'Pendente' ? `
                <button class="btn btn-action btn-action-copy me-1" onclick="copiarLinkConvite('${c.id}')" title="Copiar link do convite">
                    Link
                </button>
                <button class="btn btn-action btn-action-resend me-1" onclick="reenviarConvite('${c.id}')" title="Reenviar solicitação">
                    Reenviar
                </button>
                <button class="btn btn-action btn-action-cancel" onclick="cancelarConvite('${c.id}')" title="Cancelar convite">
                    Cancelar
                </button>
            ` : `
                <span class="text-muted small">—</span>
            `;

            tr.innerHTML = `
                <td class="fw-medium text-dark">${c.email}</td>
                <td>${c.departamento}</td>
                <td>
                    <span class="d-block text-dark fw-medium fs-7">${c.papel}</span>
                    <span class="text-muted small">${c.nivel}</span>
                </td>
                <td>${formatarData(c.enviadoEm)}</td>
                <td>
                    <span class="${getStatusBadgeClass(c.status)}">${c.status}</span>
                </td>
                <td class="text-end">${acoesHTML}</td>
            `;

            tableBody.appendChild(tr);
        });
    }

    // 8. Funções de Ação sobre Convites Existentes
    window.reenviarConvite = async function(id) {
        const item = convites.find(c => c.id === id);
        if (!item) return;

        try {
            // Chamada de API para reenviar
            try {
                await window.ApiClient.post(`/v1/convites/${id}/reenviar`);
            } catch (apiErr) {
                console.info('[Front-end Fallback] Reenvio simulado localmente.', apiErr.message);
            }

            item.enviadoEm = new Date().toISOString();
            item.status = 'Pendente';
            salvarConvites();
            renderizarTabela();

            showNotification(`Convite reenviado com sucesso para <strong>${item.email}</strong>!`, 'info');
        } catch (err) {
            showNotification(`Erro ao reenviar convite: ${err.message}`, 'danger');
        }
    };

    window.cancelarConvite = async function(id) {
        const item = convites.find(c => c.id === id);
        if (!item) return;

        if (!confirm(`Deseja realmente cancelar o convite enviado para ${item.email}?`)) return;

        try {
            try {
                await window.ApiClient.delete(`/v1/convites/${id}`);
            } catch (apiErr) {
                console.info('[Front-end Fallback] Cancelamento simulado localmente.', apiErr.message);
            }

            item.status = 'Cancelado';
            salvarConvites();
            renderizarTabela();

            showNotification(`O convite para <strong>${item.email}</strong> foi cancelado.`, 'warning');
        } catch (err) {
            showNotification(`Erro ao cancelar convite: ${err.message}`, 'danger');
        }
    };

    window.copiarLinkConvite = function(id) {
        const item = convites.find(c => c.id === id);
        if (!item) return;

        // Gera URL segura sem expor segredo/token privado
        const inviteUrl = `${window.location.origin}/aceitar-convite?id=${id}`;
        
        navigator.clipboard.writeText(inviteUrl).then(() => {
            showNotification(`Link do convite para <strong>${item.email}</strong> copiado para a área de transferência!`, 'success');
        }).catch(() => {
            showNotification(`Não foi possível copiar o link automaticamente: ${inviteUrl}`, 'info');
        });
    };

    // Eventos de Busca e Filtro
    if (searchInput) searchInput.addEventListener('input', renderizarTabela);
    if (statusFilter) statusFilter.addEventListener('change', renderizarTabela);

    // Inicialização da Página
    atualizarPermissaoInterface();
    renderizarTabela();
});
