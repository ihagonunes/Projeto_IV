package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.UUID;

/** Dados de entrada da criação de um Model do catálogo (ADR-38). */
public record CreateModelCommand(
    TenantId tenantId,
    UUID providerId,
    String name,
    String externalName,
    BigDecimal inputPricePer1k,
    BigDecimal outputPricePer1k
) {
}
