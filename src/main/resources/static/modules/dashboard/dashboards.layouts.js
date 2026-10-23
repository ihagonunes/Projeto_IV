"use strict";
// SPI-92: conteúdo estático de apresentação. Não representa dados nem regras de RBAC.
module.exports = {
    colaborador: {
        title: "Dashboard do Colaborador", scope: "Meu consumo", icon: "bi-person",
        description: "Seu uso de IA, engajamento e histórico em um só lugar.",
        kpis: ["Meu consumo de tokens", "Minha posição em AI Credits", "Meus AI Credits", "Média anônima de consumo"],
        charts: [["Meu consumo ao longo do tempo", "Tokens de entrada e saída no período."], ["Comparação anônima", "Consumo próprio e médias agregadas, sem identificar colegas."]],
        tables: [["Meu histórico", "Somente minhas interações com o Chat e o Agente de Políticas.", ["Interação", "Canal", "Data e hora", "Ações"]]],
        actions: ["Ir para o Chat", "Ir para o Agente de Políticas", "Gerenciar / excluir meu histórico"],
        analytics: [{ title: "Meu retorno estimado", description: "Estimativas baseadas no uso registrado e nas premissas de produtividade; não representam lucro comprovado.", kpis: ["Meu custo registrado", "Tempo economizado estimado", "Valor gerado estimado", "Benefício líquido estimado"], charts: [["Evolução da minha eficiência", "Custo registrado e benefício líquido estimado no mesmo período."], ["Eficiência em contexto", "Comparação anônima entre perfis de uso comparáveis, quando houver amostra suficiente."]] }]
    },
    lider: {
        title: "Dashboard do Líder", scope: "Minha equipe", icon: "bi-people",
        description: "Consumo, participação e oportunidades de capacitação da equipe.",
        kpis: ["Meus gastos", "Gastos da equipe", "Taxa de usuários ativos", "Taxa de usuários inativos"],
        charts: [["Semana atual × semana anterior", "Comparativo dos gastos próprios e agregados da equipe."], ["Modelos usados pela equipe", "Distribuição do uso por modelo de IA."], ["Uso por membro", "Distribuição do consumo entre membros da equipe."], ["Participação da equipe", "Usuários ativos e inativos no período."]],
        tables: [["Ranking de engajamento da equipe", "Posição e AI Credits dos membros da equipe.", ["Posição", "Membro", "AI Credits", "Período"]]],
        highlights: ["Gargalos de uso", "Oportunidades de capacitação"],
        analytics: [{ title: "Retorno estimado da equipe", description: "Agregação das estimativas individuais; sem exposição de salários e sem alterar o ranking de AI Credits.", kpis: ["Tempo economizado estimado da equipe", "Valor gerado estimado da equipe", "Benefício líquido estimado da equipe", "Cobertura das estimativas"], charts: [["Custo e benefício estimado da equipe", "Evolução semanal com indicação da cobertura dos dados e das premissas."]] }]
    },
    gestor: {
        title: "Dashboard do Gestor", scope: "Minha área", icon: "bi-diagram-3",
        description: "Visão analítica da área, orçamento e resultados de capacitação.",
        kpis: ["Gastos da empresa", "Gastos do departamento", "AI Adoption da área", "Governance Score da área"],
        charts: [["Evolução dos gastos", "Comparativo temporal da empresa e do departamento."], ["Distribuição de modelos", "Participação dos modelos no consumo da área."], ["AI Adoption & Governance Score", "Evolução dos indicadores da área."], ["ROI de treinamentos", "Acompanhamento dos resultados dos treinamentos da área."]],
        tables: [["Desempenho dos agentes de IA da área", "Execuções, latência, sucesso e custo por agente.", ["Agente", "Execuções", "Latência", "Sucesso", "Custo"]]],
        management: { title: "Orçamento departamental", description: "Acompanhar o consumo, propor valores e ajustar o orçamento da área. Orçamento financeiro é distinto de AI Credits.", fields: ["Orçamento vigente", "Consumo do período", "Disponível"], actions: ["Propor orçamento", "Ajustar orçamento"] },
        analytics: [
            { title: "Retorno estimado do departamento", description: "Benefício econômico estimado do uso observado, separado dos resultados dos treinamentos.", kpis: ["Valor gerado estimado da área", "Benefício líquido estimado da área", "Retorno estimado sobre custo de IA", "Cobertura das estimativas"], charts: [["Custo registrado × benefício estimado", "Evolução das estimativas do departamento com premissas e moeda consistentes."]] },
            { title: "Cenário otimizado do departamento", description: "Projeção de distribuição de requisições entre modelos elegíveis. A recomendação não altera o roteamento nem representa economia realizada.", kpis: ["Custo projetado", "Benefício líquido projetado", "Ganho potencial estimado", "Demanda atendida no cenário"], charts: [["Distribuição atual × recomendada", "Comparação entre alocação observada e cenário viável para a mesma demanda."], ["Limites do cenário", "Orçamento individual e departamental e capacidade contratada por modelo."]] }
        ]
    },
    executivo: {
        title: "Dashboard Executivo", scope: "Visão corporativa", icon: "bi-building",
        description: "Indicadores consolidados de adoção, governança e investimento em IA.",
        kpis: ["Gastos corporativos", "AI Adoption global", "Governance Score global", "Taxa de adoção geral"],
        charts: [["FinOps corporativo", "Evolução dos gastos e do consumo consolidado de IA."], ["Adoção de IA na empresa", "Evolução da participação e da adoção geral."], ["Governança corporativa", "Evolução do AI Adoption & Governance Score global."], ["Benchmarking entre departamentos", "Comparação dos indicadores agregados por departamento."]],
        tables: [["Consolidado por departamento", "Indicadores corporativos para comparação entre áreas.", ["Departamento", "Gastos", "Adoção", "Governance Score"]]],
        management: { title: "Relatórios executivos", description: "Relatórios consolidados com resultados observados, estimativas e projeções identificados separadamente.", actions: ["Exportar PDF", "Exportar apresentação"] },
        analytics: [
            { title: "Valor econômico estimado da IA", description: "Consolidação corporativa das estimativas, com cobertura dos dados. Benefício de tempo não equivale a receita ou lucro contábil.", kpis: ["Valor gerado estimado corporativo", "Benefício líquido estimado corporativo", "Retorno estimado sobre custo de IA", "Cobertura das estimativas"], charts: [["Retorno estimado por departamento", "Comparação entre áreas com a mesma metodologia e indicação da cobertura."]] },
            { title: "Oportunidades de otimização", description: "Resultados projetados de cenários viáveis, para a mesma demanda e premissas do cenário de referência.", kpis: ["Benefício líquido projetado", "Ganho potencial estimado", "Variação projetada de custo", "Demanda atendida no cenário"], charts: [["Referência × cenário otimizado", "Custos e benefícios projetados; sem tratar recomendações como ganhos já realizados."]] },
            { title: "Adoção homologada e Shadow AI", description: "A adoção interna não comprova redução de Shadow AI. Os indicadores dependem das fontes de uso observáveis.", kpis: ["Adoção de ferramentas homologadas", "Uso não homologado identificado", "Participação não homologada observada", "Cobertura do monitoramento"], charts: [["Evolução do uso homologado", "Participação no universo monitorado, com fontes e cobertura identificadas."]] }
        ]
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
