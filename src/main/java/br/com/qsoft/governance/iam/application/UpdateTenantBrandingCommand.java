package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;

/**
 * Dados de entrada da edição da identidade visual do tenant (SPI-53).
 * Campos nulos/em branco limpam o valor correspondente.
 */
public record UpdateTenantBrandingCommand(
    TenantId tenantId,
    String logoUrl,
    String primaryColor,
    String secondaryColor
) {
}
