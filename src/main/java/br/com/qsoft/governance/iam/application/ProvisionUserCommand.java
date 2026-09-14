package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Set;
import java.util.UUID;

/**
 * Dados de entrada para provisionar (bootstrap/onboarding) um usuário com senha
 * e permissões. Usado pelo cadastro público de onboarding; a criação autorizada
 * (com contexto JWT) virá na implementação completa.
 */
public record ProvisionUserCommand(
    TenantId tenantId,
    String email,
    String name,
    HierarchyLevel hierarchyLevel,
    String password,
    UUID departmentId,
    Set<Permission> permissions
) {
}
