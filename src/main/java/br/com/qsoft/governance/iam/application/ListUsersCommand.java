package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.UUID;

/**
 * Dados de entrada da listagem/busca paginada de usuários (SPI-62).
 */
public record ListUsersCommand(
    TenantId tenantId,
    HierarchyLevel hierarchyLevel,
    UUID departmentId,
    String name,
    String email,
    int page,
    int size
) {
}
