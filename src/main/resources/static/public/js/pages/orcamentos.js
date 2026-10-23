/**
 * orcamentos.js — administração do orçamento mensal por departamento (SPEC-02 §5; SPI-121).
 *
 * Consome, via window.ApiClient:
 *   GET  /v1/observability/orcamentos?mes=AAAA-MM  -> orçado x realizado do mês
 *   PUT  /v1/departamentos/{id}/orcamentos/AAAA-MM -> upsert do valor e do limiar
 *   GET  /v1/departamentos/{id}/orcamentos?de&ate  -> histórico (máx. 24 meses)
 *   GET  /v1/usuarios/me/permissoes                -> só experiência de uso
 *
 * O tenant e a autorização vêm do JWT no backend. Esconder a edição aqui não
 * substitui o RBAC: o backend exige DEPARTMENT:MANAGE (ADR-34).
 */
document.addEventListener("DOMContentLoaded", async () => {
    const client = window.ApiClient;
    const mesInput = document.getElementById("orcamentoMes");
    const corpo = document.getElementById("orcamentosTableBody");
    const mensagem = document.getElementById("orcamentosMensagem");
    const historicoTitulo = document.getElementById("historicoTitulo");
    const historicoMensagem = document.getElementById("historicoMensagem");
    const historicoCorpo = document.getElementById("historicoTableBody");
    const editor = document.getElementById("orcamentoEditor");
    const editorForm = document.getElementById("orcamentoEditorForm");
    const editorValor = document.getElementById("orcamentoEditorValor");
    const editorLimiar = document.getElementById("orcamentoEditorLimiar");
    const editorMensagem = document.getElementById("orcamentoEditorMensagem");
    const editorSalvar = document.getElementById("orcamentoEditorSalvar");
    const editorCancelar = document.getElementById("orcamentoEditorCancelar");
    let edicao = null;
    let salvando = false;
    let statusVersao = 0;
    let historicoVersao = 0;

    const STATUS = {
        OK: { texto: "OK", classe: "text-bg-success" },
        ALERTA: { texto: "Alerta", classe: "text-bg-warning" },
        EXCEDIDO: { texto: "Excedido", classe: "text-bg-danger" },
        SEM_ORCAMENTO: { texto: "Sem orçamento", classe: "text-bg-secondary" }
    };

    let podeEditar = false;
    let itensAtuais = [];
    let historicoDepartamentoId = null;

    mesInput.value = new Date().toISOString().slice(0, 7);

    function avisar(texto, tipo) {
        mensagem.textContent = texto || "";
        mensagem.className = texto ? `alert alert-${tipo || "info"} mt-3` : "d-none";
    }

    function avisarHistorico(texto) {
        historicoMensagem.textContent = texto || "";
        historicoMensagem.className = texto ? "alert alert-warning mt-3 mb-0" : "text-muted small";
    }

    function escapar(valor) {
        return String(valor ?? "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function formatarMoeda(valor) {
        if (valor == null) return "Sem orçamento";
        return "R$ " + Number(valor).toLocaleString("pt-BR", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function formatarPercentual(valor) {
        if (valor == null) return "—";
        return Number(valor).toLocaleString("pt-BR", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }) + "%";
    }

    function formatarData(valor) {
        if (!valor) return "—";
        const data = new Date(valor);
        return Number.isNaN(data.getTime()) ? "—" : data.toLocaleString("pt-BR");
    }

    function mensagemDeErro(err, padrao) {
        if (err?.status === 403) {
            return "Você não tem permissão para esta operação de orçamento.";
        }
        if (err?.status === 404) {
            return "Departamento não encontrado.";
        }
        if (err?.status >= 500) {
            return "O serviço de orçamento está indisponível. Tente novamente em instantes.";
        }
        return err?.message || padrao;
    }

    async function carregarPermissoes() {
        try {
            const acesso = await client.get("/v1/usuarios/me/permissoes");
            podeEditar = Array.isArray(acesso?.permissoes)
                && acesso.permissoes.includes("DEPARTMENT:MANAGE");
        } catch (err) {
            podeEditar = false;
        }
    }

    async function carregarStatus() {
        const versao = ++statusVersao;
        itensAtuais = [];
        corpo.innerHTML = '<tr><td colspan="7">Carregando orçamentos…</td></tr>';
        if (!client) {
            avisar("Cliente HTTP não carregado.", "warning");
            return;
        }
        avisar("");
        try {
            const resposta = await client.get(
                `/v1/observability/orcamentos?mes=${encodeURIComponent(mesInput.value)}`);
            if (versao !== statusVersao) return;
            itensAtuais = Array.isArray(resposta?.itens) ? resposta.itens : [];
            renderizarTabela(itensAtuais);
            return true;
        } catch (err) {
            if (versao !== statusVersao) return;
            itensAtuais = [];
            corpo.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3">'
                + 'Não foi possível carregar os orçamentos.</td></tr>';
            avisar(mensagemDeErro(err, "Não foi possível carregar os orçamentos."), "danger");
            return false;
        }
    }

    function renderizarTabela(itens) {
        corpo.innerHTML = "";
        if (!itens.length) {
            corpo.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3">'
                + 'Nenhum departamento ativo no seu escopo para este mês.</td></tr>';
            return;
        }

        itens.forEach((item) => {
            const status = STATUS[item.status] || { texto: item.status, classe: "text-bg-secondary" };
            const origem = item.orcamentoPadrao ? ' <span class="text-muted small">(padrão)</span>' : "";
            const orcamentoCelula = `${escapar(formatarMoeda(item.orcamento))}${origem}`;
            const limiarCelula = String(item.limiarAlerta);
            // O percentual e o status são calculados pelo backend. A largura é apenas visual.
            const percentual = Number(item.pctUsado);
            const temPercentual = item.pctUsado != null && Number.isFinite(percentual);
            const largura = temPercentual ? Math.min(100, Math.max(0, percentual))
                : item.status === "EXCEDIDO" ? 100 : 0;
            const cor = item.status === "SEM_ORCAMENTO" ? "bg-secondary"
                : percentual >= 100 || item.status === "EXCEDIDO" ? "bg-danger"
                : percentual >= 80 ? "bg-warning" : "bg-success";
            const progresso = item.status === "SEM_ORCAMENTO" ? "Sem orçamento"
                : temPercentual ? formatarPercentual(percentual) : "Percentual não aplicável";

            const tr = document.createElement("tr");
            tr.dataset.departmentId = item.departmentId;
            tr.innerHTML = `
                <td>${escapar(item.departmentName)}</td>
                <td class="text-end">${orcamentoCelula}</td>
                <td class="text-end">${limiarCelula}</td>
                <td class="text-end">${escapar(item.custoReal == null ? "—" : formatarMoeda(item.custoReal))}</td>
                <td style="min-width:160px"><div>${escapar(progresso)}</div>
                    <div class="progress" role="progressbar" aria-label="Consumo de ${escapar(item.departmentName)}"
                        aria-valuemin="0" aria-valuemax="100" aria-valuenow="${largura}"
                        aria-valuetext="${escapar(progresso)}">
                        <div class="progress-bar ${cor}" style="width:${largura}%"></div>
                    </div></td>
                <td><span class="badge ${status.classe}">${escapar(status.texto)}</span></td>
                <td class="text-end text-nowrap">
                    ${podeEditar ? '<button type="button" class="btn btn-sm btn-outline-primary me-1" data-acao="editar">Editar</button>' : ""}
                    <button type="button" class="btn btn-sm btn-outline-secondary" data-acao="historico">Histórico</button>
                </td>`;
            corpo.appendChild(tr);
        });
    }

    corpo.addEventListener("click", async (event) => {
        const botao = event.target.closest("button[data-acao]");
        if (!botao) return;
        const linha = botao.closest("tr");
        if (!linha) return;
        const departmentId = linha.dataset.departmentId;
        const item = itensAtuais.find((atual) => String(atual.departmentId) === String(departmentId));
        if (!item) return;

        if (botao.dataset.acao === "editar") {
            if (!podeEditar) return;
            edicao = { item, mes: mesInput.value };
            editorValor.value = item.orcamento == null ? "" : String(item.orcamento);
            editorLimiar.value = String(item.limiarAlerta);
            editorMensagem.textContent = "";
            document.getElementById("orcamentoEditorDepartamento").textContent = `${item.departmentName} — ${edicao.mes}`;
            editor.showModal();
        } else if (botao.dataset.acao === "historico") {
            await carregarHistorico(departmentId, item.departmentName);
        }
    });

    async function salvar(event) {
        event.preventDefault();
        if (!podeEditar || !edicao || salvando) return;
        const { item, mes } = edicao;
        const valor = editorValor.value.trim();
        const limiar = editorLimiar.value.trim();
        if (valor === "") {
            editorMensagem.textContent = `Informe o orçamento de ${item.departmentName}.`;
            return;
        }
        const threshold = limiar === "" ? 80 : Number(limiar);
        if (!/^\d{1,13}(\.\d{1,2})?$/.test(valor) || !Number.isFinite(Number(valor))) {
            editorMensagem.textContent = "Informe um valor não negativo, com até 13 dígitos inteiros e duas casas decimais.";
            return;
        }
        if (!Number.isInteger(threshold) || threshold < 1 || threshold > 100) {
            editorMensagem.textContent = "O limiar deve ser um número inteiro entre 1 e 100.";
            return;
        }

        try {
            salvando = true;
            editorMensagem.textContent = "";
            editorSalvar.disabled = true;
            editorCancelar.disabled = true;
            await client.put(
                `/v1/departamentos/${item.departmentId}/orcamentos/${mes}`,
                { amount: valor, alertThresholdPct: threshold });
            editor.close();
            if (await carregarStatus()) {
                avisar(`Orçamento de ${item.departmentName} atualizado.`, "success");
            }
            if (String(historicoDepartamentoId) === String(item.departmentId)) {
                await carregarHistorico(item.departmentId, item.departmentName);
            }
        } catch (err) {
            editorMensagem.textContent = mensagemDeErro(err, "Não foi possível salvar o orçamento.");
        } finally {
            salvando = false;
            editorSalvar.disabled = false;
            editorCancelar.disabled = false;
        }
    }

    editorForm.addEventListener("submit", salvar);
    editorCancelar.addEventListener("click", () => editor.close());
    editor.addEventListener("cancel", event => { if (salvando) event.preventDefault(); });

    function somarMeses(mes, delta) {
        const [ano, numeroMes] = mes.split("-").map(Number);
        const data = new Date(Date.UTC(ano, numeroMes - 1 + delta, 1));
        return data.toISOString().slice(0, 7);
    }

    async function carregarHistorico(departmentId, departmentName) {
        const versao = ++historicoVersao;
        if (!mesInput.value) {
            avisarHistorico("Selecione um mês de referência para consultar o histórico.");
            return;
        }
        historicoDepartamentoId = departmentId;
        historicoTitulo.textContent = `Histórico de ${departmentName} (últimos 12 meses)`;
        avisarHistorico("");
        historicoCorpo.innerHTML = "";
        try {
            const de = somarMeses(mesInput.value, -11);
            const lista = await client.get(
                `/v1/departamentos/${departmentId}/orcamentos?de=${de}&ate=${mesInput.value}`);
            if (versao !== historicoVersao) return;
            const registros = Array.isArray(lista) ? lista : [];
            if (!registros.length) {
                historicoCorpo.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">'
                    + 'Nenhum orçamento próprio cadastrado nos últimos 12 meses.</td></tr>';
                return;
            }
            historicoCorpo.innerHTML = registros.map((registro) => `
                <tr>
                    <td>${escapar(registro.mes)}</td>
                    <td class="text-end">${escapar(formatarMoeda(registro.valor))}</td>
                    <td class="text-end">${escapar(registro.limiarAlerta ?? "—")}</td>
                    <td>${escapar(formatarData(registro.atualizadoEm))}</td>
                </tr>`).join("");
        } catch (err) {
            if (versao !== historicoVersao) return;
            historicoCorpo.innerHTML = "";
            avisarHistorico(mensagemDeErro(err, "Não foi possível carregar o histórico."));
        }
    }

    mesInput.addEventListener("change", () => {
        ++historicoVersao;
        historicoCorpo.innerHTML = "";
        historicoDepartamentoId = null;
        historicoTitulo.textContent = "Histórico dos últimos 12 meses";
        avisarHistorico('Selecione "Histórico" em um departamento.');
        if (!mesInput.value) {
            ++statusVersao;
            itensAtuais = [];
            corpo.innerHTML = "";
            avisar("Selecione um mês de referência.", "warning");
            return;
        }
        carregarStatus();
    });

    await carregarPermissoes();
    await carregarStatus();
});
