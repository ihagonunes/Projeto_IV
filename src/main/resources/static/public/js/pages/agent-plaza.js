/* SPI-136: editor visual local; não executa agentes nem concede acesso a dados. */
(function (root, factory) {
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    else root.AtlasAgentPlaza = api;
})(typeof window === "undefined" ? globalThis : window, function () {
    "use strict";
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
    ].map(([kind, name, description, category]) => ({ kind, name, description, category }));
    const clamp = value => Math.max(0, Math.min(1, value));
    function createGraph() {
        return { nodes: [{ id: "task", kind: "task", name: "Tarefa do usuário", x: 0, y: 0 }], edges: [], nextId: 1 };
    }
    function addNode(graph, kind, x = 0, y = 0) {
        const item = kind === "result" ? { name: "Resultado" } : catalog.find(a => a.kind === kind);
        if (!item || (kind === "result" && graph.nodes.some(n => n.kind === "result"))) return null;
        const entry = { id: `step-${graph.nextId++}`, kind, name: item.name, x: clamp(x), y: clamp(y) };
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
    function mount() {
        const graph = createGraph(); let source = null;
        const el = (tag, text, cls) => { const n = document.createElement(tag); if (text) n.textContent = text; if (cls) n.className = cls; return n; };
        const button = (text, action, cls = "chat-action-button") => { const b = el("button", text, cls); b.type = "button"; b.addEventListener("click", action); return b; };
        const root = el("section", null, "chat-agent-plaza");
        const toolbar = el("div", null, "plaza-toolbar");
        const summary = el("span", "Monte seu fluxo", "plaza-summary");
        const addResult = button("+ Adicionar resultado", () => {
            const result = addAtAvailablePosition("result");
            if (result) { render(); announce("Resultado adicionado. Conecte a saída do consolidador à entrada do resultado."); }
        }, "chat-action-button is-primary");
        toolbar.append(summary, addResult);
        const instructions = el("p", "Arraste ou clique para adicionar agentes. Conecte de Saída para Entrada. Para remover uma conexão, clique duas vezes na seta.", "plaza-instructions");
        const viewport = el("div", null, "plaza-viewport");
        const canvas = el("div", null, "plaza-canvas"); canvas.setAttribute("aria-label", "Área de montagem do fluxo");
        const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg"); svg.setAttribute("class", "plaza-wires");
        const nodes = el("div"); canvas.append(svg, nodes); viewport.append(canvas);
        const live = el("p", "Comece pela tarefa do usuário e adicione o primeiro agente.", "plaza-status"); live.setAttribute("role", "status");
        const announce = message => { live.textContent = message; };
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
        ["Todos", ...new Set(catalog.map(a => a.category))].forEach(name => { const o = el("option", name); category.append(o); });
        libraryHeader.append(el("strong", "Agentes disponíveis"), search, category);
        const cards = el("div", null, "plaza-catalog"); library.append(libraryHeader, cards);
        workspace.append(viewport, library);
        root.append(toolbar, instructions, workspace, live,
            el("p", "Rascunho visual · permanece ao fechar esta janela e é apagado ao recarregar a página. A execução dos agentes ainda não está conectada.", "plaza-footnote"));
        const inset = 8;
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
                        return addNode(graph, kind, x, y);
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
            return addNode(graph, kind, best.x, best.y);
        }
        function drag(event, payload) { event.dataTransfer.setData("application/x-atlas-agent", JSON.stringify(payload)); event.dataTransfer.effectAllowed = payload.id ? "move" : "copy"; }
        canvas.addEventListener("dragover", event => { if (Array.from(event.dataTransfer.types).includes("application/x-atlas-agent")) event.preventDefault(); });
        canvas.addEventListener("drop", event => {
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
                const x = a.x + cardWidth, y = a.y + cardHeight - 20;
                const tx = b.x, ty = b.y + cardHeight - 20;
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
        function render() {
            nodes.replaceChildren();
            graph.nodes.forEach(n => {
                const card = el("article", null, `plaza-node plaza-${n.kind}${source === n.id ? " is-connecting" : ""}`);
                card.draggable = true;
                card.tabIndex = 0; card.setAttribute("aria-label", `${n.name}. Use as setas para mover.`);
                card.addEventListener("dragstart", event => { const r = card.getBoundingClientRect(); drag(event, { id: n.id, dx: event.clientX - r.left, dy: event.clientY - r.top }); });
                card.addEventListener("keydown", event => {
                    if (event.target !== card || !["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key)) return;
                    event.preventDefault();
                    const bounds = travel();
                    if (bounds.x && event.key === "ArrowLeft") n.x = clamp(n.x - 20 / bounds.x);
                    if (bounds.x && event.key === "ArrowRight") n.x = clamp(n.x + 20 / bounds.x);
                    if (bounds.y && event.key === "ArrowUp") n.y = clamp(n.y - 20 / bounds.y);
                    if (bounds.y && event.key === "ArrowDown") n.y = clamp(n.y + 20 / bounds.y);
                    layout();
                });
                card.append(el("span", n.kind === "task" ? "INÍCIO" : n.kind === "result" ? "ENTREGA FINAL" : n.kind === "merge" ? "CONSOLIDAÇÃO" : "AGENTE", "plaza-node-label"), el("strong", n.name));
                if (n.kind !== "task") { const remove = button("×", () => { removeNode(graph, n.id); if (source === n.id) source = null; render(); announce("Etapa e suas conexões removidas."); }, "plaza-remove"); remove.setAttribute("aria-label", `Remover ${n.name}`); card.append(remove); }
                const ports = el("div", null, "plaza-ports");
                if (n.kind !== "task") { const input = button("● Entrada", () => {
                    if (!source) return announce("Selecione primeiro a Saída de uma etapa.");
                    const error = connect(graph, source, n.id); if (!error) source = null;
                    render(); announce(error || "Conexão criada. Você pode criar outros ramos a partir da mesma saída.");
                }, "plaza-port"); input.setAttribute("aria-label", `Entrada de ${n.name}`); ports.append(input); }
                if (n.kind !== "result") { const output = button("Saída ●", () => { source = source === n.id ? null : n.id; render(); announce(source ? `Saída de ${n.name} selecionada. Escolha uma Entrada.` : "Conexão cancelada."); }, "plaza-port plaza-output"); output.setAttribute("aria-label", `Saída de ${n.name}`); output.setAttribute("aria-pressed", String(source === n.id)); ports.append(output); }
                card.append(ports); nodes.append(card);
            });
            layout(); addResult.disabled = graph.nodes.some(n => n.kind === "result");
            summary.textContent = `${graph.nodes.filter(n => !["task", "result"].includes(n.kind)).length} agentes · ${graph.edges.length} conexões`;
        }
        function renderCatalog() {
            cards.replaceChildren();
            const normalize = s => s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
            catalog.filter(a => (category.value === "Todos" || a.category === category.value) && normalize(a.name + a.description).includes(normalize(search.value))).forEach(a => {
                const card = button("", () => {
                    addAtAvailablePosition(a.kind);
                    render(); announce(`${a.name} adicionado. Conecte as etapas para definir a ordem.`);
                }, "plaza-agent-card");
                card.draggable = true; card.setAttribute("aria-label", `Adicionar ${a.name}`); card.addEventListener("dragstart", event => drag(event, { kind: a.kind }));
                card.append(el("small", a.category), el("strong", a.name), el("span", a.description)); cards.append(card);
            });
            if (!cards.children.length) cards.append(el("p", "Nenhum agente encontrado."));
        }
        search.addEventListener("input", renderCatalog); category.addEventListener("change", renderCatalog);
        new ResizeObserver(layout).observe(canvas);
        render(); renderCatalog(); return root;
    }
    return { catalog, createGraph, addNode, connect, removeNode, mount };
});
