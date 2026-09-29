package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.iam.api.model.AuthorizationScope;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolve o <b>escopo de visibilidade</b> do dashboard a partir do
 * {@code AuthorizationScope} do IAM (SPI-94 / ADR-04/ADR-30).
 * <p>
 * O frontend <b>não</b> decide o escopo: ele é sempre derivado do usuário
 * autenticado.
 * <ul>
 *   <li><b>SELF</b> (Collaborator) — apenas os próprios registros;</li>
 *   <li><b>DEPARTMENT</b> (Leader/Manager) — apenas o próprio departamento
 *       (o departamento pedido é <b>ignorado</b>);</li>
 *   <li><b>TENANT</b> (Executive/Admin) — todo o tenant; o departamento pedido é
 *       aceito como filtro opcional.</li>
 * </ul>
 */
@Component
public class DashboardScopeResolver {

    private final AuthorizationQuery authorizationQuery;
    private final IdentityQuery identityQuery;

    public DashboardScopeResolver(AuthorizationQuery authorizationQuery,
                                  IdentityQuery identityQuery) {
        this.authorizationQuery = authorizationQuery;
        this.identityQuery = identityQuery;
    }

    public UsageMetricsRepository.UsageMetricsFilter resolve(
            TenantId tenantId, UserId userId, UUID requestedDepartmentId,
            java.time.Instant from, java.time.Instant to,
            UsageMetricsRepository.Granularity granularity) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");

        AuthorizationScope scope = authorizationQuery.resolveScope(userId);

        UUID scopedUserId = null;
        UUID scopedDepartmentId = null;

        if (scope == AuthorizationScope.SELF) {
            scopedUserId = userId.value();
        } else if (scope == AuthorizationScope.DEPARTMENT) {
            scopedDepartmentId = identityQuery.getUserContext(userId)
                .map(UserContext::departmentId)
                .orElse(null);
        } else {
            // TENANT: o filtro por departamento é opcional e vem do pedido.
            scopedDepartmentId = requestedDepartmentId;
        }

        return new UsageMetricsRepository.UsageMetricsFilter(
            tenantId, scopedUserId, scopedDepartmentId, from, to, granularity);
    }
}
