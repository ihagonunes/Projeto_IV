package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Modelo resolvido pelo roteamento determinístico (SPI-83/84), com a
 * configuração do provider e os preços fotografados para a tentativa
 * (ADR-38). Nunca carrega o segredo — apenas a referência.
 */
public record ResolvedModel(
    UUID modelId,
    String modelName,
    String externalName,
    UUID providerId,
    String providerName,
    ProviderKind providerKind,
    String baseUrl,
    String credentialRef,
    int timeoutMs,
    UUID departmentId,
    BigDecimal inputPricePer1k,
    BigDecimal outputPricePer1k
) {

    /** Nome enviado ao provider: {@code external_name} quando presente, senão o nome. */
    public String externalModelName() {
        return externalName == null || externalName.isBlank() ? modelName : externalName;
    }

    /** Custo calculado com os preços fotografados no início da tentativa. */
    public BigDecimal estimatedCost(long inputTokens, long outputTokens) {
        return Model.estimateCost(inputPricePer1k, outputPricePer1k, inputTokens, outputTokens);
    }
}
