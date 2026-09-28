package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.UUID;

/**
 * Dados de entrada da conclusão de uma requisição de IA (SPI-70/72).
 * {@code requestId} nulo gera um novo — a idempotência no observability é por
 * este id (ADR-13).
 */
public record CompleteAIRequestCommand(
    UUID requestId,
    TenantId tenantId,
    UUID departmentId,
    UUID userId,
    UUID modelId,
    long inputTokens,
    long outputTokens,
    long latencyMs,
    String status
) {
}
