package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/**
 * Dados de entrada da edição do <b>Department</b> de um usuário existente
 * (SPI-63). O papel/nível é editado por {@link ChangeUserHierarchyLevel}, não
 * aqui — o modelo de autorização é por papéis por tenant (ADR-33).
 */
public record UpdateUserCommand(
    UserId actorUserId,
    UserId targetUserId,
    UUID departmentId
) {
}
