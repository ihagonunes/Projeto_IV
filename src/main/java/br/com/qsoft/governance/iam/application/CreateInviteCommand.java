package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.UUID;

/**
 * Dados de entrada para emissão de um convite.
 * {@code invitedBy} é o usuário autenticado que emite o convite.
 */
public record CreateInviteCommand(
    TenantId tenantId,
    String email,
    HierarchyLevel hierarchyLevel,
    UUID departmentId,
    UUID invitedBy
) {
}
