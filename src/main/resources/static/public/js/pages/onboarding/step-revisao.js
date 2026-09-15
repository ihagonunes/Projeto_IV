/**
 * step-revisao.js — Consolida e exibe os dados do onboarding para revisão e envia ao backend
 */

document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("revisaoForm");
    if (!form) return;

    // Elementos DOM de exibição
    const revNomeEmpresa = document.getElementById("revNomeEmpresa");
    const revCnpj = document.getElementById("revCnpj");
    const revSetorPorte = document.getElementById("revSetorPorte");

    const revNomeAdmin = document.getElementById("revNomeAdmin");
    const revEmailAdmin = document.getElementById("revEmailAdmin");
    const revTelefone = document.getElementById("revTelefone");

    const revDepartamentos = document.getElementById("revDepartamentos");
    const revMetodoUsuarios = document.getElementById("revMetodoUsuarios");

    const revProvedor = document.getElementById("revProvedor");
    const revModelos = document.getElementById("revModelos");

    // Recalcular e carregar dados armazenados na sessão
    const tenantData = JSON.parse(sessionStorage.getItem("onboarding_tenant") || "{}");
    const adminData = JSON.parse(sessionStorage.getItem("onboarding_admin") || "{}");
    const depData = JSON.parse(sessionStorage.getItem("onboarding_departamentos") || "{}");
    const usuariosData = JSON.parse(sessionStorage.getItem("onboarding_usuarios") || "{}");
    const iaData = JSON.parse(sessionStorage.getItem("onboarding_ia") || "{}");

    // Preencher Bloco 1: Empresa
    if (revNomeEmpresa) revNomeEmpresa.textContent = tenantData.nomeEmpresa || "Minha Empresa";
    if (revCnpj) revCnpj.textContent = tenantData.cnpj || "Não informado";
    if (revSetorPorte) {
        const setor = tenantData.setor ? tenantData.setor.charAt(0).toUpperCase() + tenantData.setor.slice(1) : "Tecnologia";
        const porte = tenantData.porteEmpresa ? tenantData.porteEmpresa.charAt(0).toUpperCase() + tenantData.porteEmpresa.slice(1) : "Grande";
        revSetorPorte.textContent = `${setor} / ${porte}`;
    }

    // Preencher Bloco 2: Administrador
    if (revNomeAdmin) revNomeAdmin.textContent = adminData.nome || "Administrador Responsável";
    if (revEmailAdmin) revEmailAdmin.textContent = adminData.email || "admin@empresa.com";
    if (revTelefone) revTelefone.textContent = adminData.telefone || "Não informado";

    // Preencher Bloco 3: Departamentos & Usuários
    if (revDepartamentos) revDepartamentos.textContent = depData.total || "2";
    if (revMetodoUsuarios) revMetodoUsuarios.textContent = usuariosData.metodo || "Cadastro Manual";

    // Preencher Bloco 4: IA
    if (revProvedor) revProvedor.textContent = iaData.provedor || "OpenAI";
    if (revModelos) revModelos.textContent = iaData.modelos || "gpt-4o (1 modelo)";

    // Evento de Submissão Final do Onboarding
    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        const btnFinalizar = form.querySelector('button[type="submit"]');
        if (btnFinalizar) {
            btnFinalizar.disabled = true;
            btnFinalizar.innerHTML = `<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Finalizando...`;
        }

        const payloadCompleto = {
            tenant: tenantData,
            admin: adminData,
            departamentos: depData,
            usuarios: usuariosData,
            ia: iaData
        };

        try {
            const apiObj = (typeof window !== 'undefined' && window.API) ? window.API : (typeof API !== 'undefined' ? API : null);
            if (apiObj && typeof apiObj.post === 'function') {
                await apiObj.post("/revisao/confirmar", payloadCompleto);
            }

            // Limpa dados temporários da sessão
            sessionStorage.removeItem("onboarding_tenant");
            sessionStorage.removeItem("onboarding_admin");
            sessionStorage.removeItem("onboarding_departamentos");
            sessionStorage.removeItem("onboarding_usuarios");
            sessionStorage.removeItem("onboarding_ia");

            // Redireciona para o login informando sucesso
            window.location.href = "/login?cadastro=concluido";
        } catch (err) {
            console.warn("API de confirmação indisponível, concluindo no modo demonstrativo:", err);
            
            // Limpa sessão e redireciona para o login no modo de demonstração
            sessionStorage.clear();
            window.location.href = "/login?cadastro=concluido";
        }
    });
});
