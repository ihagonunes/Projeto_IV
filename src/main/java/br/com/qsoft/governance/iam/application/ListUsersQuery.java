package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;

public record ListUsersQuery(
    TenantId tenantId,
    String search,
    String status
) {
}
