package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de entrada para alteração de status do tenant (SPI-57).
 */
public record ChangeTenantStatusRequest(
    @NotNull(message = "status é obrigatório")
    TenantStatus status
) {
}
