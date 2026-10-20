package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.observability.application.port.UsageRecordRepository;
import br.com.qsoft.governance.observability.domain.UsageRecord;
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
 * Consulta operacional paginada de UsageRecord (SPI-73), restrita pelo
 * {@code visibilityFor(USAGE:READ)} do ator (ADR-36). Filtro pedido fora do
 * escopo → 403. Sem filtro, o escopo define o que é visível (AZ20).
 */
@Service
public class QueryUsage {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final UsageRecordRepository usageRecordRepository;
    private final AuthorizationQuery authorizationQuery;

    public QueryUsage(UsageRecordRepository usageRecordRepository,
                      AuthorizationQuery authorizationQuery) {
        this.usageRecordRepository = usageRecordRepository;
        this.authorizationQuery = authorizationQuery;
    }

    public PageResult<UsageRecord> query(QueryUsageCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UserId actor = Objects.requireNonNull(command.actor(), "actor");
        int page = Math.max(command.page(), 0);
        int size = command.size() <= 0 ? DEFAULT_SIZE : Math.min(command.size(), MAX_SIZE);

        VisibilityScope visibility = authorizationQuery.visibilityFor(
            tenantId, actor, Resource.USAGE, Action.READ);

        if (visibility instanceof VisibilityScope.None) {
            throw new UsageVisibilityDeniedException();
        }
        UUID requestedDepartment = command.departmentId();
        if (requestedDepartment != null && !coversDepartment(visibility, requestedDepartment)) {
            throw new UsageVisibilityDeniedException();
        }
        UUID requestedUser = command.userId();
        if (requestedUser != null && !allowsUser(visibility, requestedUser)) {
            throw new UsageVisibilityDeniedException();
        }

        return usageRecordRepository.search(new UsageRecordRepository.UsageRecordFilter(
            tenantId, visibility, requestedDepartment, requestedUser, command.from(), command.to()
        ), page, size);
    }

    private static boolean coversDepartment(VisibilityScope visibility, UUID departmentId) {
        return visibility instanceof VisibilityScope.TenantWide
            || (visibility instanceof VisibilityScope.Departments departments
                && departments.ids().contains(departmentId));
    }

    /**
     * TenantWide pode filtrar qualquer usuário; os demais só o próprio (fail-closed
     * para usuários fora do escopo departamental, que o observability não resolve).
     */
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
