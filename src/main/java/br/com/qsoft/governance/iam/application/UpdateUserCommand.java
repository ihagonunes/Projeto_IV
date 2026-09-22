package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/**
 * Dados de entrada da edição de papel (HierarchyLevel) e Department de um
 * usuário existente (SPI-63). Campos nulos não são alterados.
 */
public record UpdateUserCommand(
    UserId actorUserId,
    UserId targetUserId,
    HierarchyLevel hierarchyLevel,
    UUID departmentId
) {
}
