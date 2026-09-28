package br.com.qsoft.governance.sharedkernel.event;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de domínio publicado pelo <b>AI Gateway</b> ao concluir uma requisição
 * de IA (ADR-13). É a <b>única origem operacional</b> de UsageRecord.
 * <p>
 * O Gateway calcula o {@code estimatedCost} <b>uma vez</b> (ADR-13/SPI-72); o
 * observability apenas persiste/agrega — nunca recalcula.
 * <p>
 * Vive no {@code sharedkernel} por ser um contrato <b>transversal</b> (ADR-02):
 * quem publica (aigateway) e quem consome (observability) importam daqui, sem
 * criar dependência direta entre os módulos.
 * <p>
 * <b>Não carrega conteúdo privado</b> (prompt, resposta, documento, segredo) —
 * apenas metadados, tokens, custo e latência.
 */
public record AIRequestCompleted(
    UUID requestId,
    UUID tenantId,
    UUID departmentId,
    UUID userId,
    UUID modelId,
    UUID providerId,
    long inputTokens,
    long outputTokens,
    BigDecimal estimatedCost,
    long latencyMs,
    String status
) {

    public AIRequestCompleted {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        Objects.requireNonNull(status, "status");
        if (inputTokens < 0 || outputTokens < 0 || latencyMs < 0) {
            throw new IllegalArgumentException("tokens e latência não podem ser negativos.");
        }
    }
}
