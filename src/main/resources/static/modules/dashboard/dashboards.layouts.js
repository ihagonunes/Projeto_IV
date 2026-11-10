"use strict";
// SPI-92: conteúdo estático de apresentação. Não representa dados nem regras de RBAC.
module.exports = {
    colaborador: {
        title: "Dashboard do Colaborador", scope: "Meu consumo", icon: "bi-person",
        description: "Seu uso de IA, engajamento e histórico em um só lugar.",
        kpis: ["Meu consumo de tokens", "Minha posição em AI Credits", "Meus AI Credits", "Média anônima de consumo"],
        charts: [["Meu consumo ao longo do tempo", "Tokens de entrada e saída no período."], ["Comparação anônima", "Consumo próprio e médias agregadas, sem identificar colegas."]],
        tables: [["Minhas conversas no Chat", "Conversas ativas do meu perfil.", ["Conversa", "Canal", "Criada em", "Ações"]]],
        actions: ["Ir para o Chat", "Gerenciar / excluir meu histórico"],
        analytics: [
            { title: "Meu custo de IA", description: "Custo da fonte e do período selecionados.", kpis: ["Meu custo registrado"], charts: [] },
            { title: "Meu retorno estimado", demoOnly: true, description: "Simulação de demonstração baseada no volume de requisições.", kpis: ["Tempo economizado estimado", "Valor gerado estimado", "Benefício líquido estimado"], charts: [["Evolução da minha eficiência", "Custo e benefício líquido simulados no mesmo período."], ["Eficiência em contexto", "Tempo estimado em contexto · simulação."]] }
        ]
    },
    lider: {
        title: "Dashboard do Líder", scope: "Minha equipe", icon: "bi-people",
        description: "Consumo, participação e oportunidades de capacitação da equipe.",
        kpis: ["Meus gastos", "Gastos da equipe", "Taxa de usuários ativos", "Taxa de usuários inativos"],
        charts: [["Últimos 7 dias × 7 dias anteriores", "Comparativo dos gastos próprios e agregados da equipe."], ["Modelos usados pela equipe", "Distribuição do uso por modelo de IA."], ["Custo por membro", "Custo operacional registrado por membro da equipe.", "OPERACIONAL"], ["Participação da equipe", "Usuários ativos e inativos no período."]],
        tables: [],
        highlights: ["Treinados sem uso recente", "Ativos sem treinamento concluído"]
    },
    gestor: {
        title: "Dashboard do Gestor", scope: "Minha área", icon: "bi-diagram-3",
        description: "Consumo, orçamento, adoção e treinamento da área.",
        kpis: ["Gastos do departamento", "AI Adoption da área"],
        charts: [["Evolução dos gastos do departamento", "Custo diário no recorte autorizado."], ["Distribuição de modelos", "Participação dos modelos no consumo da área."]],
        tables: []
    },
    executivo: {
        title: "Dashboard Executivo", scope: "Visão corporativa", icon: "bi-building",
        description: "Indicadores consolidados de adoção e investimento em IA.",
        kpis: ["Gastos corporativos", "AI Adoption global"],
        charts: [["FinOps corporativo", "Evolução dos gastos consolidados de IA."], ["Adoção nos últimos 30 dias", "Usuários ativos e inativos nos 30 dias até a data de corte."], ["Benchmarking entre departamentos", "Comparação de custos por departamento."]],
        tables: [["Consolidado por departamento", "Custos por área na fonte e no período selecionados.", ["Departamento", "Gastos"]]],
        management: { title: "Gestão do orçamento", description: "Defina o orçamento mensal dos departamentos.", actions: ["Ajustar orçamento"] }
    },
    administrador: {
        title: "Painel de Governança/TI", scope: "Operação técnica", icon: "bi-shield-check",
        description: "Auditoria, disponibilidade, infraestrutura e segurança do ambiente de IA.",
        kpis: ["Requisições do AI Gateway", "Latência das requisições", "Taxa de erros", "Alertas de segurança"],
        charts: [["Latência do AI Gateway", "Tempo de resposta das requisições ao longo do período."], ["Erros e status de requisições", "Distribuição dos resultados operacionais do AI Gateway."], ["Consumo de infraestrutura", "Acompanhamento dos recursos de infraestrutura."], ["Segurança", "Visão dos eventos e alertas de segurança."]],
        tables: [["Logs técnicos de auditoria do AI Gateway", "Metadados técnicos, sem prompts, respostas ou segredos.", ["Data e hora", "Requisição", "Modelo", "Latência", "Status"]]],
        actions: ["Gestão de usuários", "Homologação de modelos", "Credenciais BYOK", "Upload de políticas", "Gestão de alertas"],
        analytics: [{ title: "Qualidade da medição e uso homologado", description: "Metadados que sustentam as estimativas e a governança. Sem conteúdo de conversas, salários ou credenciais.", kpis: ["Requisições com custo válido", "Requisições com latência válida", "Uso não homologado identificado", "Cobertura do monitoramento"], charts: [["Sucesso técnico e latência por modelo", "Indicadores operacionais; não equivalem à qualidade ou utilidade da tarefa."], ["Capacidade contratada dos modelos", "Utilização dos limites documentados e disponibilidade dos parâmetros de otimização."]] }]
    }
};
