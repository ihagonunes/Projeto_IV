/* SPI-136 · controlador exclusivo da interface do chat ATLAS. */
(function () {
    "use strict";
    const C = window.AtlasChat;
    const $ = id => document.getElementById(id);
    const app = $("chat-app");
    const preview = app.dataset.preview === "true";
    const service = preview ? C.createPreviewService() : C.createLiveService(window.API);
    const state = { conversations: [], messages: [], models: [], receipts: new Map(), selected: null, selectedConversation: null, modelId: null,
        archived: false, page: 0, totalPages: 0, pending: false, loading: false, controller: null, generation: 0, files: [], usage: { input: 0, output: 0 } };
    let toastTimer;
    let searchTimer;
    let historyVersion = 0;
    function node(tag, text, className) { const el = document.createElement(tag); if (text != null) el.textContent = text; if (className) el.className = className; return el; }
    function button(text, handler, className = "chat-action-button") { const el = node("button", text, className); el.type = "button"; el.addEventListener("click", handler); return el; }
    function toast(text) { $("chat-toast").textContent = text; $("chat-toast").hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => { $("chat-toast").hidden = true; }, 5000); }
    function status(text) { $("chat-status").textContent = text || ""; $("chat-status").hidden = !text; }
    function dialog(title, children) { $("chat-dialog-title").textContent = title; $("chat-dialog-body").replaceChildren(...children); if (!$("chat-dialog").open) $("chat-dialog").showModal(); }
    function closeDialog() { $("chat-dialog").close(); }
    function unavailable(feature) { dialog(feature, [node("p", "Esta função está prevista para o chat e aguarda integração com o serviço. Nenhuma alteração foi salva."), node("p", "Você pode experimentar o fluxo completo com dados fictícios na prévia interativa."), (() => { const a = node("a", "Abrir prévia interativa", "chat-action-button"); a.href = "/chat/previa"; return a; })()]); }
    function current() {
        return state.conversations.find(c => c.id === state.selected)
            || (state.selectedConversation?.id === state.selected ? state.selectedConversation : undefined);
    }
    function resize() { $("chat-input").style.height = "auto"; $("chat-input").style.height = `${Math.min($("chat-input").scrollHeight, 180)}px`; }
    function composer() {
        const input = $("chat-input");
        $("chat-count").textContent = `${input.value.length.toLocaleString("pt-BR")} / 8.000`;
        $("chat-send").disabled = state.pending || state.loading || !C.validateMessage(input.value) || current()?.status === "ARCHIVED";
        $("chat-send").hidden = state.pending; $("chat-stop").hidden = !state.pending;
        input.disabled = state.pending || state.loading || current()?.status === "ARCHIVED";
        $("chat-new").disabled = state.pending || state.loading;
        $("chat-model").disabled = state.pending; $("chat-attach").disabled = state.pending;
        warning(); resize();
    }
    let financialBalance = null;
    const money = value => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
    function renderFinancialBalance(balance) {
        const tank = $("chat-token-tank"), label = $("chat-token-total");
        if (!tank || !label) return;
        const percentage = balance?.limit > 0 ? 100 * balance.spent / balance.limit : 0;
        tank.style.setProperty("--token-level", `${Math.min(100, percentage)}%`);
        label.textContent = balance ? money(balance.spent) : "—";
        label.title = balance ? `${money(balance.spent)} de ${money(balance.limit)} · ${balance.month} · saldo ${money(balance.available)} · reservado ${money(balance.reserved)}` : "Saldo indisponível";
        label.setAttribute("aria-label", label.title);
        $("chat-spending-limit").textContent = balance ? (balance.configured ? `de ${money(balance.limit)}` : "A definir") : "";
        $("chat-spending-percentage").textContent = balance?.configured ? `${percentage.toFixed(1)}%` : "";
    }
    async function refreshTokenTank() {
        if (preview || !$("chat-token-tank")) return;
        try {
            financialBalance = await window.API.get("/orcamento-ia/me");
        } catch {
            financialBalance = null;
        }
        renderFinancialBalance(financialBalance);
        warning();
    }
    function warning() {
        let message = "";
        if ($("chat-input").value.length >= 7200) message = "Sua mensagem está próxima do limite de 8.000 caracteres. Esse limite não representa uma cota de tokens.";
        else if (preview && service.quota.used >= service.quota.limit) message = "Prévia: cota de tokens atingida. O bloqueio de envio ainda precisa ser definido no backend.";
        else if (preview && service.quota.used / service.quota.limit >= .8) message = "Prévia: você usou mais de 80% da cota fictícia de tokens. Acompanhe em Consumo.";
        if (!preview && financialBalance) {
            if (!financialBalance.configured) message = "O orçamento do seu departamento precisa ser configurado pelo executivo.";
            else if (financialBalance.available <= 0) message = "Saldo financeiro esgotado ou reservado em solicitações pendentes.";
            else if (financialBalance.alert) message = `Você atingiu ${financialBalance.alertPct}% do seu limite. Gasto ${money(financialBalance.spent)} de ${money(financialBalance.limit)} · saldo ${money(financialBalance.available)}.`;
        }
        $("chat-token-warning").textContent = message; $("chat-token-warning").hidden = !message;
        $("chat-usage-dot").hidden = preview ? service.quota.used / service.quota.limit < .8 : !financialBalance?.alert;
    }
    function history() {
        const container = $("chat-history"); container.replaceChildren();
        const query = $("chat-search").value.trim();
        const items = preview
            ? C.filterConversations(state.conversations, query, state.archived)
            : state.conversations;
        $("chat-active").classList.toggle("is-active", !state.archived); $("chat-active").setAttribute("aria-pressed", String(!state.archived));
        $("chat-archived").classList.toggle("is-active", state.archived); $("chat-archived").setAttribute("aria-pressed", String(state.archived));
        if (!items.length) container.append(node("p", query ? "Nenhuma conversa encontrada." : state.archived ? "Nenhuma conversa arquivada." : "Suas ideias começam aqui. Crie sua primeira conversa.", "chat-history-empty"));
        let previousGroup;
        for (const c of items) {
            const date = new Date(c.updatedAt);
            const group = date.toDateString() === new Date().toDateString() ? "Hoje" : "Anteriores";
            if (group !== previousGroup) { container.append(node("p", group, "chat-history-label")); previousGroup = group; }
            const row = node("div", null, `chat-history-row${state.selected === c.id ? " is-selected" : ""}`);
            const open = button("", () => openConversation(c), "chat-history-open");
            const icon = node("i", null, "bi bi-chat-left"); icon.setAttribute("aria-hidden", "true");
            open.append(icon, node("span", c.titulo)); open.title = c.titulo; open.disabled = state.pending || state.loading;
            if (c.id === state.selected) open.setAttribute("aria-current", "true");
            const actions = button("···", () => conversationActions(c), "chat-history-actions"); actions.setAttribute("aria-label", `Opções de ${c.titulo}`); actions.disabled = state.pending || state.loading;
            row.append(open, actions); container.append(row);
        }
        $("chat-more").hidden = state.page + 1 >= state.totalPages;
    }
    function richContent(content) {
        const element = node("div", null, "chat-message-content");
        // Apenas texto e blocos de código; HTML/links da IA nunca são executados.
        String(content || "").split("```").forEach((part, index) => {
            if (index % 2) { const pre = node("pre"); const code = node("code", part.replace(/^[a-z0-9+#.-]*\n/i, "")); pre.append(code); element.append(pre); }
            else element.append(document.createTextNode(part));
        });
        return element;
    }
    function thread() {
        const container = $("chat-thread"); container.replaceChildren();
        $("chat-welcome").hidden = Boolean(state.selected || state.messages.length || state.pending);
        container.hidden = !$("chat-welcome").hidden;
        for (const message of state.messages) {
            const article = node("article", null, `chat-message ${message.role === "USER" ? "is-user" : "is-assistant"}`);
            article.setAttribute("aria-label", message.role === "USER" ? "Você" : "Assistente ATLAS");
            if (message.role === "USER") article.textContent = message.content;
            else {
                const receipt = state.receipts.get(message.requestId) || (preview ? service.receipt(message.requestId) : null);
                const heading = node("div", null, "chat-assistant-heading");
                const img = node("img"); img.src = "/public/img/atlas-logo.png"; img.alt = "";
                heading.append(img, node("strong", "ATLAS"), node("span", receipt?.modelo || "Assistente de IA"));
                const actions = node("div", null, "chat-message-actions");
                const copy = button("", async () => { try { await navigator.clipboard.writeText(message.content); toast("Resposta copiada."); } catch { toast("Não foi possível copiar. Selecione o texto da resposta."); } }, "chat-icon");
                copy.append(node("i", null, "bi bi-copy")); copy.setAttribute("aria-label", "Copiar resposta");
                const regenerate = button("", () => regenerateDialog(message), "chat-icon"); regenerate.append(node("i", null, "bi bi-arrow-clockwise")); regenerate.setAttribute("aria-label", "Gerar outra resposta"); regenerate.disabled = state.pending;
                actions.append(copy, regenerate, button(receipt ? `${receipt.tokensEntrada == null || receipt.tokensSaida == null ? "Tokens indisponíveis" : `${C.tokenLabel(receipt.tokensEntrada + receipt.tokensSaida)} tokens`} · Ver recibo` : "Ver detalhes da execução", () => receiptDialog(receipt, message.requestId), "chat-receipt"));
                article.append(heading, richContent(message.content), actions);
            }
            container.append(article);
        }
        if (state.pending) { const pending = node("div", "ATLAS está preparando a resposta…", "chat-pending"); pending.setAttribute("role", "status"); container.append(pending); }
        if (state.loading) container.append(node("p", "Carregando conversa…", "chat-history-empty"));
        composer();
    }
    function bottom() { requestAnimationFrame(() => { $("chat-scroll").scrollTop = $("chat-scroll").scrollHeight; }); }
    function closeSidebar() { app.classList.remove("is-mobile-open"); $("chat-overlay").hidden = true; if (matchMedia("(max-width: 720px)").matches) $("chat-toggle").setAttribute("aria-expanded", "false"); }
    async function loadHistory(append = false) {
        const version = ++historyVersion;
        const result = await service.list(
            append ? state.page + 1 : 0,
            state.archived ? "ARCHIVED" : "ACTIVE",
            $("chat-search").value.trim()
        );
        if (version !== historyVersion) return;
        state.conversations = append ? C.mergeById(state.conversations, result.content) : result.content;
        state.page = result.page; state.totalPages = result.totalPages; history();
    }
    async function openConversation(c) {
        if (state.pending || state.loading) return;
        const generation = ++state.generation;
        state.selected = c.id; state.selectedConversation = c; state.messages = []; state.loading = true; status(""); history(); thread(); closeSidebar();
        try {
            const messages = await service.messages(c.id);
            if (generation !== state.generation) return;
            state.messages = messages;
            for (const [requestId, receipt] of C.collectReceipts(messages)) {
                state.receipts.set(requestId, receipt);
            }
        }
        catch (error) { status(C.errorMessage(error)); }
        finally { if (generation === state.generation) { state.loading = false; if (c.status === "ARCHIVED") status("Esta conversa está arquivada. Restaure-a para continuar."); thread(); history(); bottom(); } }
    }
    async function newConversation() {
        if (state.pending || state.loading) return;
        state.generation++; state.selected = null; state.selectedConversation = null; state.messages = []; state.files = []; state.archived = false;
        $("chat-input").value = ""; status(""); renderFiles(); thread(); closeSidebar(); $("chat-input").focus();
        if (!preview) {
            try { await loadHistory(); } catch { status("Não foi possível atualizar a lista de conversas. Recarregue a página."); }
        } else history();
    }
    function conversationActions(c) {
        const note = preview
            ? node("p", "Estas alterações afetam apenas os dados fictícios desta prévia.")
            : node("p", "As alterações são confirmadas pelo servidor. Falhas mantêm a lista reconciliada com a API.");
        dialog("Opções da conversa", [node("p", c.titulo), note,
            button("Renomear conversa", () => renameDialog(c)),
            button(c.status === "ARCHIVED" ? "Restaurar conversa" : "Arquivar conversa", async () => {
                const nextArchived = c.status !== "ARCHIVED";
                try {
                    await service.archive(c.id, nextArchived);
                    closeDialog();
                    if (state.selectedConversation?.id === c.id) {
                        state.selectedConversation.status = nextArchived ? "ARCHIVED" : "ACTIVE";
                    }
                    if (state.selected === c.id) state.archived = nextArchived;
                    try { await loadHistory(); } catch { status("Alteração salva; não foi possível atualizar a lista. Recarregue a página."); }
                    if (state.selected === c.id) status(nextArchived ? "Esta conversa está arquivada. Restaure-a para continuar." : "");
                    toast(nextArchived ? "Conversa arquivada." : "Conversa restaurada.");
                } catch (error) {
                    closeDialog();
                    status(C.errorMessage(error));
                    try { await loadHistory(); } catch { /* O erro da ação permanece visível. */ }
                }
            }),
            button("Excluir conversa", () => deleteDialog(c), "chat-action-button is-danger")]);
    }
    function renameDialog(c) {
        const label = node("label", "Título da conversa"); label.htmlFor = "chat-rename-input";
        const input = node("input", null, "chat-dialog-input"); input.id = "chat-rename-input"; input.maxLength = 200; input.value = c.titulo;
        const save = button("Salvar título", async () => {
            if (!input.value.trim()) { input.setCustomValidity("Informe um título."); input.reportValidity(); return; }
            save.disabled = true;
            try {
                const updated = await service.rename(c.id, input.value);
                closeDialog();
                if (state.selectedConversation?.id === c.id) {
                    state.selectedConversation.titulo = updated?.titulo || input.value.trim();
                }
                try { await loadHistory(); } catch { status("Título salvo; não foi possível atualizar a lista. Recarregue a página."); }
                toast("Título atualizado.");
            } catch (error) {
                closeDialog();
                status(C.errorMessage(error));
                try { await loadHistory(); } catch { /* O erro da ação permanece visível. */ }
            } finally {
                save.disabled = false;
            }
        }, "chat-action-button is-primary");
        const actions = node("div", null, "chat-dialog-actions"); actions.append(button("Cancelar", closeDialog), save);
        input.addEventListener("input", () => input.setCustomValidity("")); dialog("Renomear conversa", [label, input, actions]); input.focus();
    }
    function deleteDialog(c) {
        const actions = node("div", null, "chat-dialog-actions");
        const confirm = button("Excluir conversa", async () => {
            confirm.disabled = true;
            try {
                const wasSelected = state.selected === c.id;
                await service.remove(c.id);
                closeDialog();
                if (wasSelected) await newConversation();
                else try { await loadHistory(); } catch { status("Conversa excluída; não foi possível atualizar a lista. Recarregue a página."); }
                toast("Conversa excluída.");
            } catch (error) {
                closeDialog();
                status(C.errorMessage(error));
                try { await loadHistory(); } catch { /* O erro da ação permanece visível. */ }
            } finally {
                confirm.disabled = false;
            }
        }, "chat-action-button is-danger");
        actions.append(button("Cancelar", closeDialog), confirm);
        const description = preview
            ? `A conversa “${c.titulo}” será removida dos dados fictícios. Esta ação não pode ser desfeita nesta sessão.`
            : `A conversa “${c.titulo}” será removida das suas listas. O conteúdo permanece retido no servidor conforme a política de retenção.`;
        dialog("Excluir esta conversa?", [node("p", description), actions]);
    }
    function details(rows) { const dl = node("dl"); rows.forEach(([label, value]) => { const row = node("div"); row.append(node("dt", label), node("dd", value)); dl.append(row); }); return dl; }
    function regenerateDialog(message) {
        if (!preview) return unavailable("Gerar outra resposta");
        dialog("Gerar outra resposta", [node("p", "A prévia substituirá esta resposta por outra versão fictícia, sem repetir a pergunta. A política de versões do serviço real ainda precisa ser definida."),
            button("Gerar versão de exemplo", async () => { const response = await service.regenerate(state.selected, message.id, state.modelId); state.messages = state.messages.map(m => m.id === message.id ? response : m); closeDialog(); thread(); toast("Nova versão fictícia gerada."); }, "chat-action-button is-primary")]);
    }
    function receiptDialog(receipt, requestId) {
        const note = node("p", preview ? "Recibo fictício para demonstração da interface." : "Metadados informados pelo serviço de IA. Custo estimado com base nos preços cadastrados; moeda não informada pelo contrato.");
        if (!receipt) return dialog("Detalhes da execução", [node("p", "O histórico atual não devolve o recibo completo. Tokens, custo e modelo não estão disponíveis para esta resposta."), details([["Identificador da execução", requestId || "Indisponível"]])]);
        dialog("Recibo da resposta", [note, details([
            ["Modelo", receipt.modelo], ["Provider", receipt.provider], ["Tokens de entrada", C.tokenLabel(receipt.tokensEntrada)],
            ["Tokens de saída", C.tokenLabel(receipt.tokensSaida)], ["Custo estimado · moeda não informada", C.costLabel(receipt)],
            ["Status do custo", receipt.statusCusto === "KNOWN" ? "Informado pelo serviço" : "Indisponível / inconclusivo"],
            ["Latência", `${C.tokenLabel(receipt.latenciaMs)} ms`], ["Identificador da execução", receipt.requestId]
        ]), node("p", "Os tokens de entrada incluem o contexto enviado. Preços não cadastrados podem produzir custo zero.", "chat-dialog-note")]);
    }
    async function modelDialog() {
        const options = state.models.map(model => {
            const b = button("", () => { state.modelId = model.id; $("chat-model-name").textContent = model.name; closeDialog(); toast(`Modelo selecionado: ${model.name}. Vale para o próximo envio.`); }, `chat-model-option${state.modelId === model.id || (state.modelId === null && model === state.models[0]) ? " is-selected" : ""}`);
            const label = node("div"); label.append(node("strong", model.name), node("small", model.description), node("small", model.provider)); b.append(node("span", "✧"), label, node("span", model.badge)); return b;
        });
        dialog("Escolha seu modelo", [node("p", preview ? "Opções fictícias. Experimente alternar o modelo para o próximo envio." : "O modelo é definido pelas permissões do seu departamento."), ...options,
            node("p", preview ? "A troca mantém a conversa e usa o novo modelo nas próximas respostas. A seleção não é persistida ao recarregar." : "A lista vem da política do seu departamento. A seleção vale para o próximo envio e não é persistida ao recarregar.", "chat-dialog-note")]);
    }
    async function usageDialog() {
        if (!preview) {
            await refreshTokenTank();
            const b = financialBalance;
            const grid = node("div", null, "chat-dialog-grid");
            if (b) for (const [label, value] of [["Limite mensal", b.limit], ["Gasto confirmado", b.spent], ["Reservas pendentes", b.reserved], ["Saldo disponível", b.available]]) {
                const item = node("div", null, "chat-dialog-stat");
                item.append(node("span", label), node("strong", money(value))); grid.append(item);
            }
            dialog("Seu consumo de IA", [node("p", b ? `Orçamento de ${b.month} · ${b.timezone} · alerta em ${b.alertPct}% do limite individual.` : "Não foi possível consultar seu saldo."), grid]);
            return;
        }
        const quota = service.quota;
        const grid = node("div", null, "chat-dialog-grid");
        for (const [label, value] of [["Tokens de entrada", quota.input], ["Tokens de saída", quota.output]]) {
            const item = node("div", null, "chat-dialog-stat"); item.append(node("span", label), node("strong", C.tokenLabel(value))); grid.append(item);
        }
        const children = [node("p", "Consumo fictício do mês · cenário de demonstração"), grid];
        const meter = node("div", null, "chat-meter"); meter.setAttribute("role", "meter"); meter.setAttribute("aria-label", "Cota fictícia utilizada"); meter.setAttribute("aria-valuemin", "0"); meter.setAttribute("aria-valuemax", String(quota.limit)); meter.setAttribute("aria-valuenow", String(Math.min(quota.used, quota.limit))); const bar = node("span"); bar.style.width = `${Math.min(100, quota.used / quota.limit * 100)}%`; meter.append(bar);
        children.push(meter, node("p", `${C.tokenLabel(quota.used)} de ${C.tokenLabel(quota.limit)} tokens fictícios · ${Math.round(quota.used / quota.limit * 100)}%`));
        const scenario = node("label", "Próximo envio", "chat-scenario"); const select = node("select"); select.setAttribute("aria-label", "Cenário do próximo envio");
        for (const [value, label] of [["success", "Resposta concluída"], ["403", "Sem permissão · 403"], ["409", "Modelo indisponível · 409"], ["502", "Falha do provider · 502"], ["503", "Registro indisponível · 503"], ["504", "Tempo excedido · 504"]]) { const option = node("option", label); option.value = value; select.append(option); }
        select.value = service.scenario; select.addEventListener("change", () => { service.scenario = select.value; }); scenario.append(select); children.push(scenario);
        children.push(button("Simular cota atingida", () => { quota.input += Math.max(0, quota.limit - quota.used); quota.used = quota.input + quota.output; warning(); closeDialog(); toast("Cenário de cota atingida ativado na prévia."); }));
        children.push(node("p", "Contexto: o serviço envia no máximo as últimas 20 mensagens. Não há cálculo de ocupação da janela do modelo.", "chat-dialog-note"));
        dialog("Seu consumo de IA", children);
    }
    function renderFiles() {
        $("chat-files").replaceChildren(); $("chat-files").hidden = !state.files.length;
        state.files.forEach((name, index) => { const chip = node("div", null, "chat-file-chip"); const remove = button("×", () => { state.files.splice(index, 1); renderFiles(); }); remove.setAttribute("aria-label", `Remover ${name}`); chip.append(node("span", `${name} · prévia visual`), remove); $("chat-files").append(chip); });
    }
    function attachmentDialog() {
        if (!preview) return unavailable("Anexar arquivos");
        dialog("Anexar à conversa", [node("p", "Prévia visual de anexos. Nenhum arquivo será lido, enviado ou utilizado na resposta."), button("Adicionar documento de exemplo", () => { state.files = ["resumo-do-projeto.pdf"]; renderFiles(); closeDialog(); toast("Anexo fictício adicionado. O conteúdo não é usado na resposta."); }), node("p", "Upload, tipos aceitos, tamanho máximo, processamento e retenção precisam ser definidos no backend.", "chat-dialog-note")]);
    }
    async function send(event) {
        event.preventDefault(); const content = $("chat-input").value;
        if (state.pending || state.loading || !C.validateMessage(content) || current()?.status === "ARCHIVED") return;
        const selected = state.selected; const before = state.messages.slice();
        const messageId = C.clientMessageId();
        state.pending = true; state.controller = new AbortController();
        const optimisticId = `local-${Date.now()}`;
        state.messages.push({ id: optimisticId, role: "USER", content });
        $("chat-input").value = ""; status(""); history(); thread(); bottom();
        try {
            const turn = await service.send(selected, content, state.modelId, state.controller.signal, messageId);
            void refreshTokenTank();
            state.selected = turn.conversa.id; state.selectedConversation = turn.conversa; state.messages = C.mergeById(before, turn.mensagens);
            if (turn.recibo) {
                state.receipts.set(turn.recibo.requestId, turn.recibo);
                state.usage.input += turn.recibo.tokensEntrada ?? 0; state.usage.output += turn.recibo.tokensSaida ?? 0;
            }
            state.files = []; renderFiles();
            state.conversations = C.mergeById(state.conversations, [turn.conversa]);
            try { await loadHistory(); } catch { status("Resposta recebida. Não foi possível atualizar a lista de conversas. Recarregue o histórico mais tarde."); }
        } catch (error) {
            void refreshTokenTank();
            const code = error.code || error.data?.code;
            status(C.errorMessage(error));
            $("chat-input").value = content;
            if (code === "CONVERSATION_ARCHIVED") {
                const conversation = current();
                if (conversation) conversation.status = "ARCHIVED";
                if (state.selectedConversation?.id === state.selected) {
                    state.selectedConversation.status = "ARCHIVED";
                }
                state.archived = true;
            }
            if (selected) {
                try { state.messages = await service.messages(selected); }
                catch { state.messages = before; status(`${C.errorMessage(error)} Não foi possível reconciliar o histórico.`); }
            } else { state.messages = before; }
            try { await loadHistory(); } catch { /* O erro do envio permanece visível. */ }
            if (error.status === 401) { const a = node("a", " Entrar novamente"); a.href = "/login"; $("chat-status").append(a); }
            else if (!selected) $("chat-status").append(document.createTextNode(" Verifique as conversas recentes antes de criar outro chat: a primeira mensagem pode ter sido salva."));
        } finally {
            state.pending = false; state.controller = null; thread(); history(); bottom(); $("chat-input").focus();
        }
    }
    $("chat-form").addEventListener("submit", send);
    $("chat-input").addEventListener("input", composer);
    $("chat-input").addEventListener("keydown", event => { if (event.key === "Enter" && !event.shiftKey && !event.isComposing && !matchMedia("(max-width: 720px)").matches) { event.preventDefault(); $("chat-form").requestSubmit(); } });
    $("chat-new").addEventListener("click", newConversation);
    $("chat-search").addEventListener("input", () => {
        clearTimeout(searchTimer);
        if (preview) { history(); return; }
        searchTimer = setTimeout(async () => {
            try { await loadHistory(); status(""); }
            catch (error) { status(C.errorMessage(error)); }
        }, 250);
    });
    $("chat-active").addEventListener("click", async () => {
        state.archived = false;
        try { await loadHistory(); status(""); } catch (error) { status(C.errorMessage(error)); }
    });
    $("chat-archived").addEventListener("click", async () => {
        state.archived = true;
        try { await loadHistory(); status(""); } catch (error) { status(C.errorMessage(error)); }
    });
    $("chat-more").addEventListener("click", async () => { $("chat-more").disabled = true; try { await loadHistory(true); } catch { status("Não foi possível carregar mais conversas."); } finally { $("chat-more").disabled = false; } });
    $("chat-model").addEventListener("click", modelDialog);
    let agentPlaza;
    const plazaIsOpen = () => agentPlaza && $("chat-dialog-body").contains(agentPlaza);
    $("chat-dialog").addEventListener("cancel", event => {
        if (!plazaIsOpen()) return;
        event.preventDefault(); agentPlaza.clearSelection();
    });
    $("chat-dialog").addEventListener("keydown", event => {
        if (event.key !== "Escape" || !plazaIsOpen()) return;
        event.preventDefault(); event.stopPropagation(); agentPlaza.clearSelection();
    });
    $("chat-agent")?.addEventListener("click", () => {
        agentPlaza ||= window.AtlasAgentPlaza.mount({ onConfirm: () => { closeDialog(); toast("Esteira de agentes confirmada."); } });
        dialog("Praça dos agentes", [agentPlaza]);
    });
    $("chat-usage").addEventListener("click", usageDialog);
    $("chat-attach").addEventListener("click", attachmentDialog);
    $("chat-stop").addEventListener("click", () => { state.controller?.abort(); });
    $("chat-context").addEventListener("click", () => dialog("O que a IA consegue lembrar?", [node("p", "O histórico completo fica disponível para você, mas cada envio leva apenas as últimas 20 mensagens, incluindo a pergunta atual. Mensagens mais antigas podem não influenciar a resposta."), node("p", "Ao trocar de modelo, esse mesmo recorte de contexto é usado. Não há resumo automático nem medidor de tokens da janela de contexto.")]));
    $("chat-help").addEventListener("click", () => dialog("Seu espaço de trabalho com IA", [node("p", "Converse, explore ideias e acompanhe os detalhes de cada resposta. O acesso às conversas é privado por usuário, e os modelos seguem as políticas do departamento."), node("p", preview ? "Você está em uma prévia: todos os dados são fictícios e desaparecem ao recarregar. Nenhuma requisição de chat é enviada." : "Regeneração de resposta e anexos continuam sem contrato no backend. O histórico, a busca, o seletor e os recibos usam a API real.")]));
    $("chat-dialog-close").addEventListener("click", closeDialog);
    $("chat-dialog").addEventListener("click", event => { if (event.target === $("chat-dialog")) { const r = $("chat-dialog").getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) closeDialog(); } });
    $("chat-toggle").addEventListener("click", () => {
        if (matchMedia("(max-width: 720px)").matches) { app.classList.toggle("is-mobile-open"); $("chat-overlay").hidden = !app.classList.contains("is-mobile-open"); $("chat-toggle").setAttribute("aria-expanded", String(app.classList.contains("is-mobile-open"))); }
        else { app.classList.toggle("is-collapsed"); $("chat-toggle").setAttribute("aria-expanded", String(!app.classList.contains("is-collapsed"))); }
    });
    $("chat-overlay").addEventListener("click", closeSidebar);
    document.addEventListener("keydown", event => { if (event.key === "Escape") closeSidebar(); });
    document.querySelectorAll("[data-prompt]").forEach(b => b.addEventListener("click", () => { if (state.pending) return; $("chat-input").value = b.dataset.prompt; composer(); $("chat-input").focus(); }));
    $("chat-scroll").addEventListener("scroll", () => { const s = $("chat-scroll"); $("chat-scroll-bottom").hidden = s.scrollHeight - s.scrollTop - s.clientHeight < 150; });
    $("chat-scroll-bottom").addEventListener("click", bottom);
    async function initialize() {
        void refreshTokenTank();
        if (matchMedia("(max-width: 720px)").matches) $("chat-toggle").setAttribute("aria-expanded", "false");
        const results = await Promise.allSettled([loadHistory(), service.models()]);
        if (results[0].status === "rejected") {
            status("Não foi possível carregar suas conversas. Tente novamente.");
            $("chat-history").replaceChildren(button("Carregar histórico", async () => { try { await loadHistory(); status(""); } catch (error) { status(C.errorMessage(error)); } }));
        }
        if (results[1].status === "fulfilled") { state.models = results[1].value; $("chat-model-name").textContent = state.models[0]?.name || "Padrão do departamento"; }
        else toast("Não foi possível consultar o modelo. O serviço validará a configuração no envio.");
        thread();
    }
    $("chat-reset")?.addEventListener("click", async () => { if (state.pending) return; service.reset(); state.receipts.clear(); state.usage = { input: 0, output: 0 }; state.modelId = null; $("chat-model-name").textContent = state.models[0]?.name || "Padrão do departamento"; $("chat-search").value = ""; newConversation(); await loadHistory(); toast("Prévia restaurada."); });
    initialize();
})();
