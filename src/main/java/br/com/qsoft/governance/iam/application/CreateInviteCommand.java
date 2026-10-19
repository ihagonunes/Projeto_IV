package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.UUID;

/**
 * Dados de entrada para emissão de um convite.
 * {@code invitedBy} é o usuário autenticado que emite o convite;
 * {@code hierarchyLevel} é rótulo opcional (derivado do papel quando nulo).
 */
public record CreateInviteCommand(
    TenantId tenantId,
    String email,
    String roleKey,
    UUID departmentId,
    HierarchyLevel hierarchyLevel,
    UUID invitedBy
) {
}
