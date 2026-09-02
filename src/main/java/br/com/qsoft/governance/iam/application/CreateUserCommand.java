package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.UUID;

/**
 * Dados de entrada para o cadastro de um usuário dentro do tenant.
 */
public record CreateUserCommand(
    TenantId tenantId,
    String email,
    String name,
    HierarchyLevel hierarchyLevel,
    UUID departmentId
) {
}
