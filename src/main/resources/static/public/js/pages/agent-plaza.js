/* SPI-136/SPI-180: editor visual; a persistência usa a API real fora da prévia. */
(function (root, factory) {
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    else root.AtlasAgentPlaza = api;
})(typeof window === "undefined" ? globalThis : window, function () {
    "use strict";
    const artwork = {
        policies: "01_politicas-internas", "ai-cost": "02_custos-de-ia", quality: "03_qualidade-de-processos",
        writing: "04_escrita-e-documentos", sales: "05_comercial", people: "06_pessoas-e-treinamento",
        data: "07_dados-e-validacao", marketing: "08_marketing-e-conteudo", projects: "09_projetos",
        code: "10_codigo", support: "11_atendimento", research: "12_pesquisa", tests: "13_qualidade-e-testes",
        stock: "14_estoque-e-compras", review: "15_revisor-critico", finance: "16_financeiro",
        operations: "17_operacoes-e-producao", merge: "18_consolidador"
    };
    const artworkPath = kind => `/public/img/agents/${artwork[kind] || "01_politicas-internas"}.png`;
    // Catálogo estático: usado apenas na prévia pública (/chat/previa) e como
    // fallback de arte. A produção recebe o catálogo da API.
    const catalog = [
        ["policies", "Políticas internas", "Consulta políticas e documentos internos.", "Gerais"],
        ["writing", "Escrita e documentos", "Organiza textos, PDFs e documentos Word.", "Gerais"],
        ["data", "Dados e validação", "Confere indicadores e inconsistências.", "Gerais"],
        ["code", "Código", "Produz, explica e revisa código.", "Tecnologia"],
        ["tests", "Qualidade e testes", "Cria cenários e identifica falhas.", "Tecnologia"],
        ["finance", "Financeiro", "Analisa orçamento, custos e projeções.", "Finanças"],
        ["ai-cost", "Custos de IA", "Analisa consumo e oportunidades de economia.", "Finanças"],
        ["sales", "Comercial", "Organiza oportunidades e propostas.", "Comercial"],
        ["marketing", "Marketing e conteúdo", "Prepara campanhas e conteúdo de marca.", "Comercial"],
        ["support", "Atendimento", "Prepara respostas e resume atendimentos.", "Comercial"],
        ["stock", "Estoque e compras", "Analisa reposição e disponibilidade.", "Operações"],
        ["operations", "Operações e produção", "Identifica capacidade e gargalos.", "Operações"],
        ["quality", "Qualidade de processos", "Propõe melhorias e verifica conformidade.", "Operações"],
        ["people", "Pessoas e treinamento", "Organiza onboarding e aprendizagem.", "Pessoas"],
        ["projects", "Projetos", "Organiza etapas, dependências e entregáveis.", "Gerais"],
        ["research", "Pesquisa", "Reúne fontes e sintetiza informações.", "Gerais"],
        ["review", "Revisor crítico", "Aponta lacunas e contradições.", "Gerais"],
        ["merge", "Consolidador", "Reúne os ramos em uma única entrega.", "Gerais"]
    ].map(([kind, name, description, category]) => ({ kind, name, description, category, image: artworkPath(kind) }));
    const endpoints = {
        task: { name: "Tarefa do usuário", description: "Recebe sua tarefa e inicia a esteira de agentes.", image: "/public/img/agents/19_tarefa-do-usuario.png" },
        result: { name: "Resultado", description: "Recebe a entrega final da esteira de agentes.", image: "/public/img/agents/20_resultado.png" }
    };
    const mergeAgent = catalog.find(agent => agent.kind === "merge");
    const clamp = value => Math.max(0, Math.min(1, value));
    let uidCounter = 0;
    function uid() {
        if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") return globalThis.crypto.randomUUID();
        uidCounter += 1;
        return `node-${Date.now().toString(36)}-${uidCounter}`;
    }
    function createGraph() {
        return { nodes: [{ id: "task", kind: "task", name: "Tarefa do usuário", x: 0, y: 0 }], edges: [] };
    }
    function addNode(graph, kind, x = 0, y = 0, items = catalog) {
        const item = kind === "result" ? { name: "Resultado" } : items.find(a => a.kind === kind);
        if (!item || (kind === "result" && graph.nodes.some(n => n.kind === "result"))) return null;
        const entry = { id: uid(), kind, name: item.name, x: clamp(x), y: clamp(y) };
        graph.nodes.push(entry); return entry;
    }
    function connect(graph, from, to) {
        const source = graph.nodes.find(n => n.id === from), target = graph.nodes.find(n => n.id === to);
        if (!source || !target || from === to || source.kind === "result" || target.kind === "task") return "Conexão inválida.";
        if (graph.edges.some(e => e.from === from && e.to === to)) return "Essa conexão já existe.";
        const seen = new Set();
        function reaches(id) {
            if (id === from) return true;
            if (seen.has(id)) return false;
            seen.add(id); return graph.edges.filter(e => e.from === id).some(e => reaches(e.to));
        }
        if (reaches(to)) return "Essa conexão criaria um ciclo. Escolha uma etapa posterior.";
        graph.edges.push({ from, to }); return null;
    }
    function removeNode(graph, id) {
        if (id === "task") return;
        graph.nodes = graph.nodes.filter(n => n.id !== id);
        graph.edges = graph.edges.filter(e => e.from !== id && e.to !== id);
    }
    function reachableNodes(graph) {
        const reached = new Set();
        function visit(id) {
            if (reached.has(id)) return;
            reached.add(id);
            graph.edges.filter(e => e.from === id).forEach(e => visit(e.to));
        }
        visit("task"); return reached;
    }
    function validateGraph(graph) {
        const reached = reachableNodes(graph);
        if (graph.nodes.some(n => n.kind !== "result" && !reached.has(n.id))) {
            return { valid: false, needsMerge: false, message: "Conecte todos os agentes à tarefa do usuário antes de confirmar." };
        }
        if (!graph.nodes.some(n => !["task", "result"].includes(n.kind))) {
            return { valid: false, needsMerge: false, message: "Adicione pelo menos um agente à esteira." };
        }
        const mergers = graph.nodes.filter(n => n.kind === "merge" && graph.edges.filter(e => e.to === n.id).length > 1);
        const forks = graph.nodes.filter(n => graph.edges.filter(e => e.from === n.id).length > 1);
        const unresolved = forks.some(fork => !mergers.some(merge => {
            const memo = new Map();
            function allPathsMeet(id) {
                if (id === merge.id) return true;
                if (memo.has(id)) return memo.get(id);
                const exits = graph.edges.filter(e => e.from === id);
                memo.set(id, false);
                const meets = exits.length > 0 && exits.every(e => allPathsMeet(e.to));
                memo.set(id, meets); return meets;
            }
            return allPathsMeet(fork.id);
        }));
        if (unresolved) return { valid: false, needsMerge: true, message: "Há ramos paralelos sem consolidação. Adicione o agente Consolidador para reunir os ramos." };
        const result = graph.nodes.find(n => n.kind === "result");
        if (!result || !reached.has(result.id)) return { valid: false, needsMerge: false, message: "Adicione o resultado e conecte a última etapa à sua entrada." };
        if (graph.nodes.some(n => n.kind !== "result" && !graph.edges.some(e => e.from === n.id))) {
            return { valid: false, needsMerge: false, message: "Conecte todas as pontas da esteira ao resultado." };
        }
        return { valid: true, needsMerge: false, message: "Esteira pronta para confirmar." };
    }
    function consolidate(graph, insert = kind => addNode(graph, kind, .7, .5)) {
        if (!validateGraph(graph).needsMerge) return false;
        // A junção deve acontecer num Consolidador, inclusive quando os ramos
        // já foram ligados diretamente a um agente comum ou ao resultado.
        for (const target of [...graph.nodes]) {
            const incoming = graph.edges.filter(e => e.to === target.id);
            if (target.kind === "merge" || incoming.length < 2) continue;
            const merge = insert("merge");
            graph.edges = graph.edges.filter(e => e.to !== target.id);
            incoming.forEach(e => connect(graph, e.from, merge.id));
            connect(graph, merge.id, target.id);
        }
        const result = graph.nodes.find(n => n.kind === "result");
        const leaves = graph.nodes.filter(n => !["task", "result"].includes(n.kind) && !graph.edges.some(e => e.from === n.id));
        if (leaves.length && (leaves.length > 1 || (result && graph.edges.some(e => e.to === result.id)))) {
            const sources = new Set(leaves.map(n => n.id));
            if (result) graph.edges.filter(e => e.to === result.id).forEach(e => sources.add(e.from));
            const merge = insert("merge");
            if (result) graph.edges = graph.edges.filter(e => e.to !== result.id);
            sources.forEach(id => connect(graph, id, merge.id));
            if (result) connect(graph, merge.id, result.id);
        }
        return true;
    }

    // ----------------------------------------------------------------------
    // Contrato com a API de agentes (BE-02/BE-03/BE-04)
    // ----------------------------------------------------------------------
    function catalogFromApi(entries) {
        return (entries || []).filter(entry => entry && entry.agentKey).map(entry => ({
            kind: entry.agentKey,
            name: entry.name || entry.agentKey,
            description: entry.description || "",
            category: entry.category || "Gerais",
            status: entry.status,
            executable: entry.executable === true,
            image: artworkPath(entry.agentKey)
        }));
    }
    function toServerGraph(graph) {
        return {
            schemaVersion: 1,
            nodes: graph.nodes.map(node => node.kind === "task" || node.kind === "result" || node.kind === "merge"
                ? { id: node.id, kind: node.kind, x: node.x, y: node.y }
                : { id: node.id, kind: "agent", agentKey: node.kind, x: node.x, y: node.y }),
            edges: graph.edges.map(edge => ({ from: edge.from, to: edge.to }))
        };
    }
    function fromServerGraph(serverGraph) {
        const graph = { nodes: [], edges: [] };
        const nodes = (serverGraph && serverGraph.nodes) || [];
        graph.nodes = nodes.map(node => ({
            id: node.id,
            kind: node.kind === "agent" ? (node.agentKey || "policies") : node.kind,
            name: node.name,
            x: clamp(Number(node.x) || 0),
            y: clamp(Number(node.y) || 0)
        }));
        graph.edges = ((serverGraph && serverGraph.edges) || []).map(edge => ({ from: edge.from, to: edge.to }));
        return graph;
    }
    function createService(client) {
        if (!client) throw new Error("client é obrigatório.");
        return {
            async catalog() {
                const data = await client.get("/agentes/catalogo");
                return catalogFromApi(data && data.agentes);
            },
            async load(flowId) {
                const data = await client.get(`/agentes/fluxos/${flowId}`);
                return {
                    id: data.id,
                    name: data.name,
                    rowVersion: data.rowVersion,
                    status: data.status,
                    graph: fromServerGraph(data.currentVersion && data.currentVersion.graph)
                };
            },
            async create(name, graph) {
                return client.post("/agentes/fluxos", { name, schemaVersion: 1, graphJson: toServerGraph(graph) });
            },
            async save(flowId, rowVersion, name, graph) {
                return client.put(`/agentes/fluxos/${flowId}`,
                    { name, schemaVersion: 1, graphJson: toServerGraph(graph) },
                    { headers: { "If-Match": `"${rowVersion}"` } });
            },
            async validate(flowId, graph) {
                return client.post(`/agentes/fluxos/${flowId}/validar`,
                    { schemaVersion: 1, graphJson: toServerGraph(graph) });
            },
            async publish(flowId, rowVersion) {
                return client.post(`/agentes/fluxos/${flowId}/publicar`, {},
                    { headers: { "If-Match": `"${rowVersion}"` } });
            },
            async execute(flowId, idempotencyKey, input) {
                return client.post(`/agentes/fluxos/${flowId}/execucoes`,
                    { input }, { headers: { "Idempotency-Key": idempotencyKey } });
            },
            isConflict(error) {
                return Boolean(error && (error.status === 409 || error.status === 412));
            }
        };
    }

    function mount({ onConfirm = () => {}, onNew = () => {}, client = null, catalog: initialCatalog = null, flowId = null,
        graph: initialGraph = null } = {}) {
        const graph = initialGraph
            ? { nodes: initialGraph.nodes.map(node => ({ ...node })),
                edges: initialGraph.edges.map(edge => ({ ...edge })) }
            : createGraph();
        let source = null, confirmedSignature = null, missingResultWarning = false;
        let available = initialCatalog ? initialCatalog.slice() : (client ? [] : catalog.slice());
        let nodeErrors = [];
        let currentFlowId = flowId, rowVersion = null, lastSavedSignature = null;
        let flowStatus = null, saving = false;
        const service = client ? createService(client) : null;
        const pendingClicks = new Map();
        function cancelClick(id) {
            if (pendingClicks.has(id)) window.clearTimeout(pendingClicks.get(id).timer);
            pendingClicks.delete(id);
        }
        const signature = () => JSON.stringify([graph.nodes.map(n => [n.id, n.kind, n.x, n.y]), graph.edges, nameInput.value]);
        const el = (tag, text, cls) => { const n = document.createElement(tag); if (text) n.textContent = text; if (cls) n.className = cls; return n; };
        const button = (text, action, cls = "chat-action-button") => { const b = el("button", text, cls); b.type = "button"; b.addEventListener("click", action); return b; };
        const root = el("section", null, "chat-agent-plaza");
        const toolbar = el("div", null, "plaza-toolbar");
        const summary = el("span", "Monte seu fluxo", "plaza-summary");
        const agentFor = kind => endpoints[kind] || available.find(a => a.kind === kind)
            || catalog.find(a => a.kind === kind) || (kind === "merge" ? mergeAgent : null);
        const libraryItems = () => (available.some(a => a.kind === "merge")
            ? available.slice() : [...available, mergeAgent]).sort((a, b) => Number(b.executable !== false) - Number(a.executable !== false));
        const addResult = button("+ Resultado", () => {
            const result = addAtAvailablePosition("result");
            if (result) { render(); announce("Resultado adicionado. Conecte a saída do consolidador à entrada do resultado."); }
        }, "chat-action-button is-primary");
        const confirm = button("Confirmar esteira", () => {
            if (saving) return;
            if (flowStatus === "PUBLISHED") return handoff();
            for (const [id, pending] of [...pendingClicks]) { cancelClick(id); pending.activate(); }
            if (!graph.nodes.some(n => n.kind === "result")) {
                const message = "Falta adicionar o Resultado. Adicione essa etapa e conecte a saída final antes de confirmar a esteira.";
                missingResultWarning = true; render(); announce(message); return;
            }
            const validation = validateGraph(graph);
            if (!validation.valid) return announce(validation.message);
            if (!service) {
                confirmedSignature = signature(); render();
                announce("Fluxo pronto. Escreva sua tarefa no chat.");
                handoff(); return;
            }
            confirmFlow();
        }, "chat-action-button is-primary");
        toolbar.append(summary, addResult, confirm);
        const instructions = el("p", "Clique em uma etapa e depois na próxima para conectar. Arraste para organizar.", "plaza-instructions");
        const viewport = el("div", null, "plaza-viewport");
        const scenery = el("div", null, "plaza-scenery"); scenery.setAttribute("aria-hidden", "true");
        for (let frame = 1; frame <= 1; frame++) {
            const image = el("img"); image.src = `/public/img/agent-plaza/fundo_0${frame}.png`; image.alt = "";
            image.draggable = false; image.style.animationDelay = `${(frame - 1) * .75}s`;
            scenery.append(image);
        }
        viewport.append(scenery);
        const canvas = el("div", null, "plaza-canvas"); canvas.setAttribute("aria-label", "Área de montagem do fluxo");
        const canvasHeading = el("div", null, "plaza-canvas-heading");
        canvasHeading.append(el("span", "ÁREA DE CRIAÇÃO"), el("span", "Seu próximo resultado começa aqui."));
        const empty = el("div", null, "plaza-empty");
        empty.append(el("span", "+", "plaza-empty-symbol"), el("strong", "Quem vai fazer parte do seu fluxo?"), el("span", "Adicione um agente da biblioteca para começar."));
        viewport.append(canvasHeading, empty);
        const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg"); svg.setAttribute("class", "plaza-wires");
        const nodes = el("div"); canvas.append(svg, nodes); viewport.append(canvas);
        const live = el("p", "Comece pela tarefa do usuário e adicione o primeiro agente.", "plaza-status"); live.setAttribute("role", "status");
        const announce = message => { live.textContent = message; };
        const tooltip = el("div", null, "plaza-tooltip");
        tooltip.id = "plaza-agent-description"; tooltip.hidden = true; tooltip.setAttribute("role", "tooltip");
        const hideTooltip = () => { tooltip.hidden = true; };
        function character(card, agent) {
            const figure = el("span", null, "plaza-figure");
            const image = el("img", null, "plaza-sprite"); image.src = agent.image; image.alt = ""; image.draggable = false;
            image.width = 64; image.height = 64;
            figure.append(image, el("strong", agent.name, "plaza-agent-name")); card.append(figure);
            card.setAttribute("aria-description", agent.description);
            function showTooltip() {
                tooltip.textContent = agent.description; tooltip.hidden = false;
                const head = image.getBoundingClientRect();
                const bounds = tooltip.getBoundingClientRect();
                const left = Math.max(8, Math.min(head.left + head.width / 2 - bounds.width / 2, window.innerWidth - bounds.width - 8));
                tooltip.style.left = `${left}px`;
                tooltip.style.top = `${Math.max(8, head.top - bounds.height - 10)}px`;
                tooltip.style.setProperty("--bubble-pointer", `${Math.max(12, Math.min(bounds.width - 12, head.left + head.width / 2 - left))}px`);
                card.setAttribute("aria-describedby", tooltip.id);
            }
            card.addEventListener("mouseenter", showTooltip); card.addEventListener("mouseleave", hideTooltip);
            card.addEventListener("focusin", showTooltip); card.addEventListener("focusout", hideTooltip);
        }
        const resultNotice = el("div", null, "plaza-merge-notice plaza-result-notice");
        const resultMessage = el("p", "Falta adicionar o Resultado. Adicione essa etapa e conecte a saída final antes de confirmar a esteira.");
        resultMessage.setAttribute("role", "alert");
        resultNotice.append(resultMessage, button("Adicionar Resultado", () => addResult.click()));
        const mergeNotice = el("div", null, "plaza-merge-notice");
        const mergeMessage = el("p"); mergeMessage.setAttribute("role", "status");
        const autoMerge = button("Adicionar Consolidador e ligar ramos", () => {
            consolidate(graph, addAtAvailablePosition); source = null; render();
            announce(validateGraph(graph).message);
        });
        mergeNotice.append(mergeMessage, autoMerge);
        const workspace = el("div", null, "plaza-workspace");
        const library = el("aside", null, "plaza-library"); const libraryHeader = el("div", null, "plaza-library-header");
        library.id = "plaza-agent-library";
        library.setAttribute("aria-label", "Agentes disponíveis");
        const toggle = button("Recolher agentes", () => {
            library.hidden = !library.hidden;
            toggle.textContent = library.hidden ? "Mostrar agentes" : "Recolher agentes";
            toggle.setAttribute("aria-expanded", String(!library.hidden));
        });
        toggle.setAttribute("aria-expanded", "true");
        toggle.setAttribute("aria-controls", library.id);
        toolbar.insertBefore(toggle, addResult);
        const search = el("input"); search.type = "search"; search.placeholder = "Buscar agente…"; search.setAttribute("aria-label", "Buscar agente");
        const category = el("select"); category.setAttribute("aria-label", "Categoria dos agentes");
        ["Todos", ...new Set(libraryItems().map(a => a.category))].forEach(name => { const o = el("option", name); category.append(o); });
        libraryHeader.append(el("span", "SUA EQUIPE DE IA", "plaza-eyebrow"), el("strong", "Biblioteca de agentes"), search, category);
        const cards = el("div", null, "plaza-catalog"); library.append(libraryHeader, cards);
        workspace.append(viewport, library);
        const footer = el("footer", null, "plaza-footer");
        const footerNote = el("div", null, "plaza-footer-note");
        footerNote.append(live, el("p", "Confirmar salva o fluxo. A execução só começa quando você enviar uma mensagem no chat.", "plaza-footnote"));
        const footerActions = el("div", null, "plaza-footer-actions");
        footerActions.append(confirm); footer.append(footerNote, footerActions);
        root.append(toolbar, instructions, resultNotice, mergeNotice, workspace, footer, tooltip);

        // Persistência real (oculta na prévia, onde `service` é nulo).
        const nameInput = el("input"); nameInput.type = "text"; nameInput.value = "Meu fluxo"; nameInput.maxLength = 200;
        nameInput.autocomplete = "off";
        nameInput.setAttribute("aria-label", "Nome do fluxo");
        const saveButton = button("Salvar rascunho", () => saveDraft());
        const conflictNotice = el("div", null, "plaza-merge-notice");
        conflictNotice.hidden = true;
        const conflictMessage = el("p", "O fluxo foi alterado por outra pessoa. Recarregue ou sobrescreva com a sua versão.");
        conflictMessage.setAttribute("role", "alert");
        const reloadButton = button("Recarregar", () => reloadFlow());
        const overwriteButton = button("Sobrescrever", () => overwriteFlow());
        conflictNotice.append(conflictMessage, reloadButton, overwriteButton);
        const nameField = el("label", null, "plaza-name-field");
        nameField.append(el("span", "Nome do fluxo"), nameInput);
        toolbar.insertBefore(nameField, addResult);
        toolbar.insertBefore(button("Novo fluxo", onNew), addResult);
        if (service) {
            footerActions.insertBefore(saveButton, confirm);
            root.insertBefore(conflictNotice, resultNotice);
        }

        function handoff() {
            onConfirm({ id: currentFlowId, name: nameInput.value.trim() || "Meu fluxo", status: flowStatus,
                preview: !service, graph: { nodes: graph.nodes.map(n => ({ ...n })), edges: graph.edges.map(e => ({ ...e })) } });
        }
        const arrange = button("Organizar fluxo", () => {
            if (flowStatus === "PUBLISHED") return;
            const depths = new Map([["task", 0]]);
            for (let pass = 0; pass < graph.nodes.length; pass++) {
                for (const edge of graph.edges) if (depths.has(edge.from)) {
                    depths.set(edge.to, Math.max(depths.get(edge.to) || 0, depths.get(edge.from) + 1));
                }
            }
            const groups = new Map();
            graph.nodes.forEach((n, index) => {
                const depth = depths.get(n.id) ?? index;
                if (!groups.has(depth)) groups.set(depth, []);
                groups.get(depth).push(n);
            });
            const levels = [...groups.keys()].sort((a, b) => a - b);
            levels.forEach((level, index) => groups.get(level).forEach((n, lane, group) => {
                n.x = levels.length > 1 ? index / (levels.length - 1) : .12;
                n.y = group.length > 1 ? lane / (group.length - 1) : .45;
            }));
            render(); announce("Etapas organizadas. As conexões foram preservadas.");
        });
        toolbar.insertBefore(arrange, addResult);

        async function persist() {
            const name = (nameInput.value || "").trim() || "Fluxo sem nome";
            if (!currentFlowId) {
                const created = await service.create(name, graph);
                currentFlowId = created.id;
                rowVersion = created.rowVersion;
                flowStatus = created.status || "DRAFT";
                lastSavedSignature = signature();
                return;
            }
            if (lastSavedSignature === signature()) return;
            const saved = await service.save(currentFlowId, rowVersion, name, graph);
            rowVersion = saved.rowVersion;
            flowStatus = saved.status || flowStatus;
            lastSavedSignature = signature();
        }
        async function saveDraft() {
            if (!service || saving) return;
            busy(true);
            try {
                await persist();
                conflictNotice.hidden = true;
                render();
                announce("Rascunho salvo no servidor.");
            } catch (error) {
                handleError(error);
            } finally {
                busy(false);
            }
        }
        async function confirmFlow() {
            busy(true);
            try {
                await persist();
                const validation = await service.validate(currentFlowId, graph);
                showServerErrors(validation);
                if (!validation.valid) return;
                const published = await service.publish(currentFlowId, rowVersion);
                rowVersion = published.rowVersion;
                flowStatus = "PUBLISHED";
                confirmedSignature = signature();
                missingResultWarning = false;
                render();
                announce("Fluxo salvo. Continue no chat.");
                handoff();
            } catch (error) {
                handleError(error);
            } finally {
                busy(false);
            }
        }
        async function reloadFlow() {
            if (!service || !currentFlowId) return;
            try {
                const loaded = await service.load(currentFlowId);
                applyLoaded(loaded);
                conflictNotice.hidden = true;
                announce("Fluxo recarregado do servidor.");
            } catch (error) { handleError(error); }
        }
        async function overwriteFlow() {
            if (!service || !currentFlowId) return;
            try {
                const loaded = await service.load(currentFlowId);
                rowVersion = loaded.rowVersion;
                lastSavedSignature = null;
                await persist();
                conflictNotice.hidden = true;
                render();
                announce("Versão local salva sobre a versão mais recente.");
            } catch (error) { handleError(error); }
        }
        function applyLoaded(loaded) {
            graph.nodes = loaded.graph.nodes.length ? loaded.graph.nodes : createGraph().nodes;
            graph.edges = loaded.graph.edges.slice();
            graph.nodes.forEach(node => {
                const agent = agentFor(node.kind);
                if (!node.name && agent) node.name = agent.name;
            });
            currentFlowId = loaded.id;
            rowVersion = loaded.rowVersion;
            flowStatus = loaded.status || "DRAFT";
            lastSavedSignature = signature();
            confirmedSignature = loaded.status === "PUBLISHED" ? signature() : null;
            nameInput.value = loaded.name || "Fluxo sem nome";
            nodeErrors = [];
            source = null;
            render();
        }
        function showServerErrors(validation) {
            nodeErrors = (validation && validation.errors) || [];
            if (!nodeErrors.length) { render(); return; }
            const first = nodeErrors[0];
            announce(`O servidor recusou o fluxo: ${first.message}`);
            render();
        }
        function handleError(error) {
            if (error?.status === 403) {
                announce("Seu perfil não pode salvar ou publicar este fluxo. Peça acesso ao responsável pelo departamento.");
                return;
            }
            if (service && service.isConflict(error)) {
                conflictNotice.hidden = false;
                announce("Conflito de versão: recarregue o fluxo ou sobrescreva com a sua versão.");
                return;
            }
            const code = error && error.data && error.data.code ? ` (${error.data.code})` : "";
            announce(`Não foi possível concluir a operação${code}. ${error && error.message ? error.message : ""}`.trim());
        }
        function busy(value) {
            saving = value;
            root.setAttribute("aria-busy", String(value));
            confirm.disabled = value;
            confirm.textContent = value ? "Salvando fluxo…" : flowStatus === "PUBLISHED" ? "Usar no chat →" : "Confirmar e ir para o chat →";
            saveButton.disabled = value || flowStatus === "PUBLISHED";
        }
        function hasNodeError(index) {
            return nodeErrors.some(error => typeof error.path === "string"
                && error.path.startsWith(`/nodes/${index}`));
        }
        function serverErrorsFor(index) {
            return nodeErrors.filter(error => typeof error.path === "string"
                && error.path.startsWith(`/nodes/${index}`)).map(error => error.message).join(" ");
        }

        const inset = 20;
        const cardWidth = 148;
        const cardHeight = 86;
        canvas.style.setProperty("--plaza-card-width", `${cardWidth}px`);
        canvas.style.setProperty("--plaza-card-height", `${cardHeight}px`);
        const travel = () => ({
            x: Math.max(0, canvas.clientWidth - cardWidth - inset * 2),
            y: Math.max(0, canvas.clientHeight - cardHeight - inset * 2)
        });
        const position = (n, bounds) => ({ x: inset + n.x * bounds.x, y: inset + n.y * bounds.y });
        const fraction = (value, span) => span ? clamp(value / span) : 0;
        function addAtAvailablePosition(kind) {
            if (flowStatus === "PUBLISHED") return null;
            const bounds = travel();
            const occupied = graph.nodes.map(n => position(n, bounds));
            const columns = Math.floor(bounds.x / (cardWidth + 12));
            const rows = Math.floor(bounds.y / (cardHeight + 12));
            for (let row = 0; row <= rows; row++) {
                for (let column = 0; column <= columns; column++) {
                    const x = columns ? column / columns : 0;
                    const y = rows ? row / rows : 0;
                    const candidate = position({ x, y }, bounds);
                    if (occupied.every(p => Math.abs(p.x - candidate.x) >= cardWidth || Math.abs(p.y - candidate.y) >= cardHeight)) {
                        return addNode(graph, kind, x, y, libraryItems());
                    }
                }
            }
            let best = { x: 0, y: 0, overlap: Infinity };
            for (const x of [0, .25, .5, .75, 1]) {
                for (const y of [0, .25, .5, .75, 1]) {
                    const candidate = position({ x, y }, bounds);
                    const overlap = occupied.reduce((area, p) => area +
                        Math.max(0, cardWidth - Math.abs(p.x - candidate.x)) *
                        Math.max(0, cardHeight - Math.abs(p.y - candidate.y)), 0);
                    if (overlap < best.overlap) best = { x, y, overlap };
                }
            }
            return addNode(graph, kind, best.x, best.y, libraryItems());
        }
        function drag(event, payload) { event.dataTransfer.setData("application/x-atlas-agent", JSON.stringify(payload)); event.dataTransfer.effectAllowed = payload.id ? "move" : "copy"; }
        canvas.addEventListener("dragover", event => { if (Array.from(event.dataTransfer.types).includes("application/x-atlas-agent")) event.preventDefault(); });
        canvas.addEventListener("drop", event => {
            if (flowStatus === "PUBLISHED") return;
            event.preventDefault(); let payload;
            try { payload = JSON.parse(event.dataTransfer.getData("application/x-atlas-agent")); } catch { return; }
            const rect = canvas.getBoundingClientRect();
            const bounds = travel();
            const x = fraction(event.clientX - rect.left - (payload.dx ?? cardWidth / 2) - inset, bounds.x);
            const y = fraction(event.clientY - rect.top - (payload.dy ?? cardHeight / 2) - inset, bounds.y);
            if (payload.id) { const n = graph.nodes.find(n => n.id === payload.id); if (n) Object.assign(n, { x, y }); }
            else addNode(graph, payload.kind, x, y);
            render(); announce("Etapa posicionada. Use Entrada e Saída para definir a ordem.");
        });
        function layout() {
            hideTooltip();
            const bounds = travel();
            const positions = new Map();
            graph.nodes.forEach((n, i) => {
                const p = position(n, bounds);
                positions.set(n.id, p);
                const card = nodes.children[i];
                card.style.left = `${p.x}px`;
                card.style.top = `${p.y}px`;
            });
            svg.replaceChildren();
            for (const edge of graph.edges) {
                const a = positions.get(edge.from), b = positions.get(edge.to);
                const x = a.x + cardWidth / 2 + 24, y = a.y + 28;
                const tx = b.x + cardWidth / 2 - 24, ty = b.y + 28;
                const curve = `M ${x} ${y} C ${x + 85} ${y}, ${tx - 85} ${ty}, ${tx} ${ty}`;
                const arrow = `M ${tx - 8} ${ty - 5} L ${tx} ${ty} L ${tx - 8} ${ty + 5}`;
                const wire = document.createElementNS(svg.namespaceURI, "g");
                wire.setAttribute("class", "plaza-wire");
                wire.setAttribute("role", "button");
                wire.setAttribute("tabindex", "0");
                const from = graph.nodes.find(n => n.id === edge.from).name;
                const to = graph.nodes.find(n => n.id === edge.to).name;
                wire.setAttribute("aria-label", `Remover conexão de ${from} para ${to}`);

                const path = document.createElementNS(svg.namespaceURI, "path");
                path.setAttribute("d", `${curve} ${arrow}`);
                const hit = path.cloneNode();
                hit.setAttribute("class", "plaza-wire-hit");
                wire.append(hit, path);
                const remove = () => {
                    if (flowStatus === "PUBLISHED") return;
                    graph.edges = graph.edges.filter(candidate => candidate !== edge);
                    render();
                    announce(`Conexão de ${from} para ${to} removida.`);
                };
                wire.addEventListener("dblclick", remove);
                wire.addEventListener("keydown", event => {
                    if (!["Enter", " ", "Delete"].includes(event.key)) return;
                    event.preventDefault();
                    remove();
                    toggle.focus();
                });
                svg.append(wire);
            }
        }
        function selectNode(n) {
            if (flowStatus === "PUBLISHED") return;
            if (!graph.nodes.some(candidate => candidate.id === n.id)) return;
            if (!source) {
                if (n.kind === "result") return announce("Resultado é a etapa final. Clique primeiro no agente de origem.");
                source = n.id;
                // Preserva o elemento para que o segundo clique de um clique duplo
                // continue chegando ao mesmo agente.
                graph.nodes.forEach((entry, index) => {
                    const card = nodes.children[index];
                    card.className = card.className.replace(/ is-connecting/g, "") + (entry.id === source ? " is-connecting" : "");
                });
                announce(`${n.name} selecionado como origem. Clique no agente de destino.`);
                return;
            }
            const error = connect(graph, source, n.id); source = null;
            render(); announce(error || "Conexão criada. Clique na próxima origem e depois no destino.");
        }
        root.clearSelection = () => {
            const selected = Boolean(source) || pendingClicks.size > 0;
            for (const id of [...pendingClicks.keys()]) cancelClick(id);
            source = null;
            if (!selected) return false;
            for (const card of nodes.children) card.className = card.className.replace(/ is-connecting/g, "");
            hideTooltip(); announce("Agente desmarcado. Clique em uma origem para iniciar outra conexão.");
            return true;
        };
        function render() {
            const focusedId = document.activeElement && document.activeElement.dataset
                ? document.activeElement.dataset.nodeId : null;
            hideTooltip();
            canvas.style.minWidth = `${Math.max(650, graph.nodes.length * (cardWidth + 36) + 40)}px`;
            nodes.replaceChildren();
            graph.nodes.forEach((n, index) => {
                const hasError = hasNodeError(index);
                const card = el("article", null, `plaza-node plaza-${n.kind}${source === n.id ? " is-connecting" : ""}${hasError ? " has-error" : ""}`);
                card.dataset.nodeId = n.id;
                if (hasError) card.setAttribute("aria-invalid", "true");
                card.draggable = flowStatus !== "PUBLISHED";
                card.tabIndex = 0; card.setAttribute("aria-label", `${n.name}. Use as setas para mover.`);
                if (hasError) card.setAttribute("aria-description", serverErrorsFor(index));
                card.addEventListener("dragstart", event => { cancelClick(n.id); const r = card.getBoundingClientRect(); drag(event, { id: n.id, dx: event.clientX - r.left, dy: event.clientY - r.top }); });
                card.addEventListener("click", event => {
                    if (event.detail > 1) return;
                    cancelClick(n.id);
                    const activate = () => selectNode(n);
                    const timer = window.setTimeout(() => { pendingClicks.delete(n.id); activate(); }, 500);
                    pendingClicks.set(n.id, { timer, activate });
                });
                const remove = () => {
                    if (flowStatus === "PUBLISHED") return;
                    cancelClick(n.id);
                    if (n.kind === "task") return announce("A tarefa do usuário é o início e não pode ser removida.");
                    removeNode(graph, n.id); if (source === n.id) source = null;
                    render(); announce(`${n.name} e suas conexões removidos.`);
                };
                card.addEventListener("dblclick", remove);
                card.addEventListener("keydown", event => {
                    if (flowStatus === "PUBLISHED") return;
                    if (event.target === card && ["Enter", " ", "Delete"].includes(event.key)) {
                        event.preventDefault(); cancelClick(n.id);
                        if (event.key === "Delete") remove(); else selectNode(n);
                        return;
                    }
                    if (event.target !== card || !["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key)) return;
                    event.preventDefault();
                    const bounds = travel();
                    if (bounds.x && event.key === "ArrowLeft") n.x = clamp(n.x - 20 / bounds.x);
                    if (bounds.x && event.key === "ArrowRight") n.x = clamp(n.x + 20 / bounds.x);
                    if (bounds.y && event.key === "ArrowUp") n.y = clamp(n.y - 20 / bounds.y);
                    if (bounds.y && event.key === "ArrowDown") n.y = clamp(n.y + 20 / bounds.y);
                    layout();
                });
                const agent = agentFor(n.kind);
                card.append(el("span", n.kind === "task" ? "ENTRADA" : n.kind === "result" ? "ENTREGA" : `AGENTE ${String(index).padStart(2, "0")}`, "plaza-node-label"));
                if (agent) { card.className += " plaza-character"; character(card, agent); }
                else card.append(el("span", n.kind === "task" ? "INÍCIO" : "ENTREGA FINAL", "plaza-node-label"), el("strong", n.name));
                if (n.kind !== "task") {
                    const removeButton = button("×", event => { event.stopPropagation(); remove(); }, "plaza-remove");
                    removeButton.setAttribute("aria-label", `Remover ${n.name}`);
                    removeButton.disabled = flowStatus === "PUBLISHED";
                    card.append(removeButton);
                }
                nodes.append(card);
            });
            layout(); addResult.disabled = graph.nodes.some(n => n.kind === "result");
            if (addResult.disabled) missingResultWarning = false;
            resultNotice.hidden = !missingResultWarning;
            summary.textContent = `${graph.nodes.filter(n => !["task", "result"].includes(n.kind)).length} agentes · ${graph.edges.length} conexões`;
            const validation = validateGraph(graph);
            mergeNotice.hidden = !validation.needsMerge;
            mergeMessage.textContent = validation.needsMerge ? validation.message : "";
            const confirmed = confirmedSignature === signature();
            root.dataset.confirmed = String(confirmed);
            root.dataset.flowId = currentFlowId || "";
            root.dataset.flowStatus = flowStatus || "";
            root.dataset.rowVersion = rowVersion == null ? "" : String(rowVersion);
            const published = flowStatus === "PUBLISHED";
            confirm.textContent = published ? "Usar no chat →" : "Confirmar e ir para o chat →";
            confirm.disabled = saving;
            saveButton.disabled = published || saving;
            nameInput.disabled = published || saving;
            arrange.disabled = published || saving;
            addResult.disabled = addResult.disabled || published;
            empty.hidden = graph.nodes.length > 1;
            if (focusedId) {
                const target = [...nodes.children].find(card => card.dataset && card.dataset.nodeId === focusedId);
                if (target && typeof target.focus === "function") target.focus();
            }
        }
        function renderCatalog() {
            cards.replaceChildren();
            const normalize = s => s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
            libraryItems().filter(a => (category.value === "Todos" || a.category === category.value) && normalize(a.name + a.description).includes(normalize(search.value))).forEach(a => {
                const executable = a.executable !== false;
                const label = a === mergeAgent ? `Adicionar ${a.name}`
                    : executable
                        ? (a.status ? `Adicionar ${a.name} (${a.status})` : `Adicionar ${a.name}`)
                        : `${a.name} (${a.status || "DRAFT"}, não executável)`;
                const card = button("", () => {
                    if (!executable) { announce(`${a.name} está em DRAFT e não pode ser executado.`); return; }
                    addAtAvailablePosition(a.kind);
                    render(); announce(`${a.name} adicionado. Conecte as etapas para definir a ordem.`);
                }, "plaza-agent-card");
                card.draggable = executable; card.setAttribute("aria-label", label);
                if (!executable || flowStatus === "PUBLISHED") { card.disabled = true; card.setAttribute("aria-disabled", "true"); }
                if (executable) card.addEventListener("dragstart", event => drag(event, { kind: a.kind }));
                character(card, a); cards.append(card);
                const meta = el("span", null, "plaza-agent-meta");
                meta.append(el("span", executable ? (a.category || "Fluxo") : "Em breve"), el("span", executable ? "+" : "◇"));
                card.append(meta);
            });
            if (!cards.children.length) cards.append(el("p", "Nenhum agente encontrado."));
        }
        search.addEventListener("input", renderCatalog); category.addEventListener("change", renderCatalog);
        root.addEventListener("mouseleave", hideTooltip);
        new ResizeObserver(layout).observe(canvas);
        render(); renderCatalog();
        if (service) {
            (async () => {
                try {
                    available = await service.catalog();
                    if (currentFlowId) {
                        const loaded = await service.load(currentFlowId);
                        applyLoaded(loaded);
                    } else {
                        render();
                    }
                    const categories = ["Todos", ...new Set(libraryItems().map(a => a.category))];
                    category.replaceChildren(...categories.map(name => { const o = el("option", name); return o; }));
                    category.value = "Todos";
                    renderCatalog();
                } catch (error) {
                    handleError(error);
                }
            })();
        }
        return root;
    }
    return { catalog, createGraph, addNode, connect, removeNode, validateGraph, consolidate, mount,
        createService, toServerGraph, fromServerGraph, catalogFromApi };
});
