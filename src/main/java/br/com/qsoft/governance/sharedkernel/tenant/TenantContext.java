package br.com.qsoft.governance.sharedkernel.tenant;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Contexto de tenant propagado a partir do JWT autenticado (ADR-03/ADR-30).
 */
public record TenantContext(TenantId tenantId, UserId userId) {
}
