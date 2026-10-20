package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.UUID;

/**
 * Dados de entrada para provisionar (bootstrap/onboarding) um usuário com senha.
 * A autorização do usuário vem das concessões (ADR-36), não deste comando.
 */
public record ProvisionUserCommand(
    TenantId tenantId,
    String email,
    String name,
    HierarchyLevel hierarchyLevel,
    String password,
    UUID departmentId
) {
}
