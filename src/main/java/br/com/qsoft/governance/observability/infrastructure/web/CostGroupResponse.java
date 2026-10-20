package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Grupo de custo do dashboard (SPI-113/114/115): {@code chave} é o id da
 * dimensão pedida em {@code groupBy}. O rótulo não é resolvido pelo
 * observability (allowlist sem aigateway); o front resolve nomes pelo catálogo.
 */
public record CostGroupResponse(
    UUID chave,
    long requisicoes,
    long tokensEntrada,
    long tokensSaida,
    BigDecimal custoEstimado
) {

    public static CostGroupResponse from(UsageMetricsRepository.CostGroup group) {
        return new CostGroupResponse(
            group.key(),
            group.requests(),
            group.inputTokens(),
            group.outputTokens(),
            group.estimatedCost()
        );
    }
}
