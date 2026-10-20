package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.api.exception.AIAttemptAccessDeniedException;
import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Consulta operacional de tentativas de IA (RF047/ADR-38), restrita pelo
 * {@code visibilityFor(USAGE:READ)} do ator. Filtro pedido fora do escopo → 403.
 * Devolve apenas metadados — a tabela não guarda prompt, resposta ou segredo.
 */
@Service
public class QueryRequestAttempts {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final RequestAttemptRepository attemptRepository;
    private final AuthorizationQuery authorizationQuery;

    public QueryRequestAttempts(RequestAttemptRepository attemptRepository,
                                AuthorizationQuery authorizationQuery) {
        this.attemptRepository = attemptRepository;
        this.authorizationQuery = authorizationQuery;
    }

    public PageResult<RequestAttempt> query(QueryRequestAttemptsCommand command) {
        Objects.requireNonNull(command, "command é obrigatório.");
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UserId actor = Objects.requireNonNull(command.actor(), "actor");
        int page = Math.max(command.page(), 0);
        int size = command.size() <= 0 ? DEFAULT_SIZE : Math.min(command.size(), MAX_SIZE);

        VisibilityScope visibility = authorizationQuery.visibilityFor(
            tenantId, actor, Resource.USAGE, Action.READ);
        if (visibility instanceof VisibilityScope.None) {
            throw new AIAttemptAccessDeniedException();
        }
        UUID requestedDepartment = command.departmentId();
        if (requestedDepartment != null && !coversDepartment(visibility, requestedDepartment)) {
            throw new AIAttemptAccessDeniedException();
        }
        UUID requestedUser = command.userId();
        if (requestedUser != null && !allowsUser(visibility, requestedUser)) {
            throw new AIAttemptAccessDeniedException();
        }

        return attemptRepository.search(new RequestAttemptRepository.AttemptFilter(
            tenantId, visibility, command.from(), command.to(), command.status(),
            command.deliveryStatus(), requestedUser, requestedDepartment), page, size);
    }

    private static boolean coversDepartment(VisibilityScope visibility, UUID departmentId) {
        return visibility instanceof VisibilityScope.TenantWide
            || (visibility instanceof VisibilityScope.Departments departments
                && departments.ids().contains(departmentId));
    }

    /** TenantWide filtra qualquer usuário; os demais só o próprio (fail-closed). */
    private static boolean allowsUser(VisibilityScope visibility, UUID userId) {
        if (visibility instanceof VisibilityScope.TenantWide) {
            return true;
        }
        if (visibility instanceof VisibilityScope.Departments departments) {
            return Objects.equals(userId, departments.self().value());
        }
        if (visibility instanceof VisibilityScope.SelfOnly selfOnly) {
            return Objects.equals(userId, selfOnly.self().value());
        }
        return false;
    }
}
