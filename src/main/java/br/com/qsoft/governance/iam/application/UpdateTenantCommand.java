package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;

/**
 * Dados de entrada da edição dos dados cadastrais do tenant (SPI-52).
 */
public record UpdateTenantCommand(
    TenantId tenantId,
    String name
) {
}
