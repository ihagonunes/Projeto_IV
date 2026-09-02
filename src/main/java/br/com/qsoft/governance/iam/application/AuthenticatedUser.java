package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Resultado da autenticação, com a identidade estável usada nas claims do JWT.
 */
public record AuthenticatedUser(UserId userId, TenantId tenantId, String email, String hierarchyLevel) {
}
