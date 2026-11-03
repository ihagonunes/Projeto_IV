package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.UUID;

/** Dados de entrada da edição de um Model do catálogo (SPI-87). */
public record UpdateModelCommand(
    TenantId tenantId,
    UUID modelId,
    String name,
    String externalName,
    BigDecimal inputPricePer1k,
    BigDecimal outputPricePer1k
) {
}
