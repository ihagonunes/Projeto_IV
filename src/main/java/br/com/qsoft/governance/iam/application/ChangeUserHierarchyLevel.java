package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de alteração controlada do nível hierárquico de um usuário.
 *	Exige (ADR-04/ADR-03): autorização via AuthorizationQuery (permissão
 *	MANAGE_HIERARCHY) + mesmo tenant. A interpretação hierárquica é do IAM.
 */
@Service
public class ChangeUserHierarchyLevel {

    private final UserRepository userRepository;
    private final AuthorizationQuery authorizationQuery;

    public ChangeUserHierarchyLevel(UserRepository userRepository, AuthorizationQuery authorizationQuery) {
        this.userRepository = userRepository;
        this.authorizationQuery = authorizationQuery;
    }

    public User change(ChangeUserHierarchyLevelCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        UserId actor = Objects.requireNonNull(command.actorUserId(), "actorUserId");
        UserId target = Objects.requireNonNull(command.targetUserId(), "targetUserId");
        HierarchyLevel newLevel = command.newLevel();
        if (newLevel == null) {
            throw new IllegalArgumentException("newLevel é obrigatório.");
        }

        User actorUser = userRepository.findById(actor)
            .orElseThrow(() -> new UserNotFoundException(actor));

        // Autorização determinística do IAM: permissão MANAGE_HIERARCHY.
        AuthorizationResult authorization =
            authorizationQuery.checkPermission(actor, Permission.MANAGE_HIERARCHY);
        if (!authorization.allowed()) {
            throw new AuthorizationDeniedException(
                authorization.denialReason() != null ? authorization.denialReason()
                    : "Usuário não autorizado a alterar níveis hierárquicos.");
        }

        User targetUser = userRepository.findById(target)
            .orElseThrow(() -> new UserNotFoundException(target));

        // Escopo: só altera usuários do MESMO tenant (ADR-03).
        if (!actorUser.tenantId().equals(targetUser.tenantId())) {
            throw new AuthorizationDeniedException(
                "Usuário não autorizado a alterar nível de usuário de outro tenant.");
        }

        targetUser.changeHierarchyLevel(newLevel);
        return userRepository.save(targetUser);
    }
}
