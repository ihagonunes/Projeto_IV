package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.UserId;

/**
 * Dados de entrada para alterar o nível hierárquico de um usuário.
 * <code>actorUserId</code> identifica quem está realizando a ação.
 */
public record ChangeUserHierarchyLevelCommand(
    UserId actorUserId,
    UserId targetUserId,
    HierarchyLevel newLevel
) {
}
