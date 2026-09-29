package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.UUID;

/** Dados de entrada da criação de um Model do catálogo. */
public record CreateModelCommand(
    TenantId tenantId,
    UUID providerId,
    String name,
    BigDecimal inputPricePer1k,
    BigDecimal outputPricePer1k
) {
}
