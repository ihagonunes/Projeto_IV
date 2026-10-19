package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.port.UserSearchFilter;
import br.com.qsoft.governance.iam.application.port.UserSearchRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de listagem/busca paginada de usuários do tenant (SPI-62).
 * <p>
 * O gate HTTP é {@code USER:READ} em {@code BY_TARGET}; aqui aplica-se o recorte
 * de {@code visibilityFor(USER:READ)} (SPEC-01 §6):
 * {@code Departments} ⇒ {@code department_id IN ids OR id = self};
 * {@code SelfOnly} ⇒ só o ator; {@code None} ⇒ nenhum resultado.
 */
@Service
public class ListUsers {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final UserSearchRepository userSearchRepository;
    private final AuthorizationQuery authorizationQuery;

    public ListUsers(UserSearchRepository userSearchRepository,
                     AuthorizationQuery authorizationQuery) {
        this.userSearchRepository = userSearchRepository;
        this.authorizationQuery = authorizationQuery;
    }

    public PageResult<User> list(ListUsersCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UserId actor = Objects.requireNonNull(command.actor(), "actor");
        int page = Math.max(command.page(), 0);
        int size = command.size() <= 0 ? DEFAULT_SIZE : Math.min(command.size(), MAX_SIZE);

        VisibilityScope visibility = authorizationQuery.visibilityFor(
            tenantId, actor, Resource.USER, Action.READ);

        if (visibility instanceof VisibilityScope.None) {
            return new PageResult<>(List.of(), 0L, page, size);
        }
        if (command.departmentId() != null && !coversDepartment(visibility, command.departmentId())) {
            throw new AuthorizationDeniedException(
                "Filtro de departamento fora do escopo do ator.");
        }

        Set<UUID> visibleDepartments = Set.of();
        UUID visibleUserId = null;
        if (visibility instanceof VisibilityScope.Departments departments) {
            visibleDepartments = departments.ids();
            visibleUserId = departments.self().value();
        } else if (visibility instanceof VisibilityScope.SelfOnly selfOnly) {
            visibleUserId = selfOnly.self().value();
        }

        UserSearchFilter filter = new UserSearchFilter(
            command.hierarchyLevel(),
            command.departmentId(),
            command.name(),
            command.email(),
            visibleDepartments,
            visibleUserId
        );
        return userSearchRepository.search(tenantId, filter, page, size);
    }

    private static boolean coversDepartment(VisibilityScope visibility, UUID departmentId) {
        return visibility instanceof VisibilityScope.TenantWide
            || (visibility instanceof VisibilityScope.Departments departments
                && departments.ids().contains(departmentId));
    }
}
