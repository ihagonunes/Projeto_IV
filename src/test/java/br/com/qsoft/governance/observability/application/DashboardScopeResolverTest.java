package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.api.model.AuthorizationScope;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardScopeResolverTest {

    private AuthorizationQuery authorizationQuery;
    private IdentityQuery identityQuery;
    private DashboardScopeResolver resolver;

    @BeforeEach
    void setUp() {
        authorizationQuery = mock(AuthorizationQuery.class);
        identityQuery = mock(IdentityQuery.class);
        resolver = new DashboardScopeResolver(authorizationQuery, identityQuery);
    }

    @Test
    void deve_lancarExcecao_quando_tenantIdNulo() {
        assertThatThrownBy(() -> resolver.resolve(null, UserId.random(), null, null, null, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void deve_lancarExcecao_quando_userIdNulo() {
        assertThatThrownBy(() -> resolver.resolve(TenantId.random(), null, null, null, null, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("userId");
    }

    @Test
    void deve_resolverEscopoSelf_paraColaborador() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        Instant from = Instant.now();
        Instant to = Instant.now();

        when(authorizationQuery.resolveScope(userId)).thenReturn(AuthorizationScope.SELF);

        UsageMetricsRepository.UsageMetricsFilter filter = resolver.resolve(
            tenantId, userId, UUID.randomUUID(), from, to, UsageMetricsRepository.Granularity.DAY);

        assertThat(filter.tenantId()).isEqualTo(tenantId);
        assertThat(filter.userId()).isEqualTo(userId.value());
        assertThat(filter.departmentId()).isNull();
        assertThat(filter.from()).isEqualTo(from);
        assertThat(filter.to()).isEqualTo(to);
        assertThat(filter.granularity()).isEqualTo(UsageMetricsRepository.Granularity.DAY);
    }

    @Test
    void deve_resolverEscopoDepartment_quandoInformacoesPresentes() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        UUID deptId = UUID.randomUUID();

        when(authorizationQuery.resolveScope(userId)).thenReturn(AuthorizationScope.DEPARTMENT);
        when(identityQuery.getUserContext(userId)).thenReturn(
            Optional.of(new UserContext(userId, tenantId, "COLLABORATOR", deptId, "ACTIVE")));

        UsageMetricsRepository.UsageMetricsFilter filter = resolver.resolve(
            tenantId, userId, UUID.randomUUID(), null, null, UsageMetricsRepository.Granularity.DAY);

        assertThat(filter.userId()).isNull();
        assertThat(filter.departmentId()).isEqualTo(deptId);
    }

    @Test
    void deve_resolverEscopoDepartment_quandoUserContextVazio() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();

        when(authorizationQuery.resolveScope(userId)).thenReturn(AuthorizationScope.DEPARTMENT);
        when(identityQuery.getUserContext(userId)).thenReturn(Optional.empty());

        UsageMetricsRepository.UsageMetricsFilter filter = resolver.resolve(
            tenantId, userId, UUID.randomUUID(), null, null, UsageMetricsRepository.Granularity.DAY);

        assertThat(filter.userId()).isNull();
        assertThat(filter.departmentId()).isNull();
    }

    @Test
    void deve_resolverEscopoTenant_usandoDepartamentoSolicitado() {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        UUID requestedDeptId = UUID.randomUUID();

        when(authorizationQuery.resolveScope(userId)).thenReturn(AuthorizationScope.TENANT);

        UsageMetricsRepository.UsageMetricsFilter filter = resolver.resolve(
            tenantId, userId, requestedDeptId, null, null, UsageMetricsRepository.Granularity.MONTH);

        assertThat(filter.userId()).isNull();
        assertThat(filter.departmentId()).isEqualTo(requestedDeptId);
    }
}
