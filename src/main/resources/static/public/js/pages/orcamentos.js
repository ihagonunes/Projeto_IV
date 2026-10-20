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
            return "Você não tem permissão para consultar o orçamento deste escopo.";
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
        if (!client) {
            avisar("Cliente HTTP não carregado.", "warning");
            return;
        }
        avisar("");
        try {
            const resposta = await client.get(
                `/v1/observability/orcamentos?mes=${encodeURIComponent(mesInput.value)}`);
            itensAtuais = Array.isArray(resposta?.itens) ? resposta.itens : [];
            renderizarTabela(itensAtuais);
        } catch (err) {
            itensAtuais = [];
            corpo.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3">'
                + 'Não foi possível carregar os orçamentos.</td></tr>';
            avisar(mensagemDeErro(err, "Não foi possível carregar os orçamentos."), "danger");
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
            const orcamentoCelula = podeEditar
                ? `<input type="number" min="0" step="0.01" class="form-control form-control-sm text-end"
                        data-campo="valor" value="${item.orcamento == null ? "" : Number(item.orcamento)}"
                        placeholder="Sem orçamento" aria-label="Orçamento de ${escapar(item.departmentName)}">`
                : `${escapar(formatarMoeda(item.orcamento))}${origem}`;
            const limiarCelula = podeEditar
                ? `<input type="number" min="1" max="100" step="1" class="form-control form-control-sm text-end"
                        data-campo="limiar" value="${Number(item.limiarAlerta)}"
                        aria-label="Limiar de alerta de ${escapar(item.departmentName)}">`
                : String(item.limiarAlerta);

            const tr = document.createElement("tr");
            tr.dataset.departmentId = item.departmentId;
            tr.innerHTML = `
                <td>${escapar(item.departmentName)}</td>
                <td class="text-end">${orcamentoCelula}</td>
                <td class="text-end">${limiarCelula}</td>
                <td class="text-end">${escapar(item.custoReal == null ? "—" : formatarMoeda(item.custoReal))}</td>
                <td class="text-end">${formatarPercentual(item.pctUsado)}</td>
                <td><span class="badge ${status.classe}">${escapar(status.texto)}</span></td>
                <td class="text-end text-nowrap">
                    ${podeEditar ? '<button type="button" class="btn btn-sm btn-outline-primary me-1" data-acao="salvar">Salvar</button>' : ""}
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

        if (botao.dataset.acao === "salvar") {
            await salvar(linha, item);
        } else if (botao.dataset.acao === "historico") {
            await carregarHistorico(departmentId, item.departmentName);
        }
    });

    async function salvar(linha, item) {
        const valor = linha.querySelector('[data-campo="valor"]').value.trim();
        const limiar = linha.querySelector('[data-campo="limiar"]').value.trim();
        if (valor === "") {
            avisar(`Informe o orçamento de ${item.departmentName}.`, "warning");
            return;
        }
        const threshold = limiar === "" ? 80 : Number(limiar);
        if (!Number.isFinite(Number(valor)) || Number(valor) < 0) {
            avisar("O orçamento deve ser um valor maior ou igual a zero.", "warning");
            return;
        }
        if (!Number.isInteger(threshold) || threshold < 1 || threshold > 100) {
            avisar("O limiar deve ser um número inteiro entre 1 e 100.", "warning");
            return;
        }

        try {
            await client.put(
                `/v1/departamentos/${departmentId}/orcamentos/${mesInput.value}`,
                { amount: valor, alertThresholdPct: threshold });
            avisar(`Orçamento de ${item.departmentName} atualizado.`, "success");
            await carregarStatus();
            if (String(historicoDepartamentoId) === String(departmentId)) {
                await carregarHistorico(departmentId, item.departmentName);
            }
        } catch (err) {
            avisar(mensagemDeErro(err, "Não foi possível salvar o orçamento."), "danger");
        }
    }

    function somarMeses(mes, delta) {
        const [ano, numeroMes] = mes.split("-").map(Number);
        const data = new Date(Date.UTC(ano, numeroMes - 1 + delta, 1));
        return data.toISOString().slice(0, 7);
    }

    async function carregarHistorico(departmentId, departmentName) {
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
            historicoCorpo.innerHTML = "";
            avisarHistorico(mensagemDeErro(err, "Não foi possível carregar o histórico."));
        }
    }

    mesInput.addEventListener("change", () => {
        if (!mesInput.value) return;
        carregarStatus();
        if (historicoDepartamentoId) {
            const item = itensAtuais.find(
                (atual) => String(atual.departmentId) === String(historicoDepartamentoId));
            if (item) {
                carregarHistorico(item.departmentId, item.departmentName);
            }
        }
    });

    await carregarPermissoes();
    await carregarStatus();
});
