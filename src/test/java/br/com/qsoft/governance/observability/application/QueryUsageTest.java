package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.observability.application.port.UsageRecordRepository;
import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QueryUsageTest {

    private UsageRecordRepository repository;
    private AuthorizationQuery authorizationQuery;
    private QueryUsage queryUsage;
    private final TenantId tenantId = TenantId.random();
    private final UserId actor = UserId.random();

    @BeforeEach
    void setUp() {
        repository = mock(UsageRecordRepository.class);
        authorizationQuery = mock(AuthorizationQuery.class);
        queryUsage = new QueryUsage(repository, authorizationQuery);
        when(authorizationQuery.visibilityFor(any(), any(), any(), any()))
            .thenReturn(new VisibilityScope.TenantWide());
        when(repository.search(any(), anyInt(), anyInt()))
            .thenReturn(new PageResult<>(List.of(), 0, 0, 20));
    }

    @Test
    void deve_lancarExcecao_quando_commandNulo() {
        assertThatThrownBy(() -> queryUsage.query(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Command é obrigatório");
    }

    @Test
    void deve_lancarExcecao_quando_tenantIdNulo() {
        QueryUsageCommand command = new QueryUsageCommand(
            null, actor, null, null, null, null, 0, 20);

        assertThatThrownBy(() -> queryUsage.query(command))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("tenantId");
    }

    @Test
    void deve_lancarExcecao_quando_actorNulo() {
        QueryUsageCommand command = new QueryUsageCommand(
            tenantId, null, null, null, null, null, 0, 20);

        assertThatThrownBy(() -> queryUsage.query(command))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("actor");
    }

    @Test
    void deve_normalizarPaginaESizeMinimos() {
        QueryUsageCommand command = new QueryUsageCommand(
            tenantId, actor, null, null, null, null, -1, 0);

        PageResult<UsageRecord> expectedPage = new PageResult<>(List.of(), 0, 0, 20);
        when(repository.search(any(), anyInt(), anyInt())).thenReturn(expectedPage);

        PageResult<UsageRecord> result = queryUsage.query(command);

        assertThat(result).isEqualTo(expectedPage);
        verify(repository).search(any(),
            org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    void deve_normalizarSizeMaximo() {
        QueryUsageCommand command = new QueryUsageCommand(
            tenantId, actor, null, null, null, null, 2, 500);

        PageResult<UsageRecord> expectedPage = new PageResult<>(List.of(), 0, 2, 100);
        when(repository.search(any(), anyInt(), anyInt())).thenReturn(expectedPage);

        PageResult<UsageRecord> result = queryUsage.query(command);

        assertThat(result).isEqualTo(expectedPage);
        verify(repository).search(any(),
            org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(100));
    }

    @Test
    void deve_executarConsultaComParametrosValidos() {
        UUID deptId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant from = Instant.now();
        Instant to = Instant.now();

        QueryUsageCommand command = new QueryUsageCommand(
            tenantId, actor, deptId, userId, from, to, 1, 50);

        PageResult<UsageRecord> expectedPage = new PageResult<>(List.of(), 0, 1, 50);
        when(repository.search(any(), anyInt(), anyInt())).thenReturn(expectedPage);

        PageResult<UsageRecord> result = queryUsage.query(command);

        assertThat(result).isEqualTo(expectedPage);

        ArgumentCaptor<UsageRecordRepository.UsageRecordFilter> filterCaptor =
            ArgumentCaptor.forClass(UsageRecordRepository.UsageRecordFilter.class);
        verify(repository).search(filterCaptor.capture(),
            org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(50));

        UsageRecordRepository.UsageRecordFilter filter = filterCaptor.getValue();
        assertThat(filter.tenantId()).isEqualTo(tenantId);
        assertThat(filter.visibility()).isEqualTo(new VisibilityScope.TenantWide());
        assertThat(filter.departmentId()).isEqualTo(deptId);
        assertThat(filter.userId()).isEqualTo(userId);
        assertThat(filter.from()).isEqualTo(from);
        assertThat(filter.to()).isEqualTo(to);
    }

    @Test
    void deve_negar_quandoVisibilityNone() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.None());

        assertThatThrownBy(() -> queryUsage.query(new QueryUsageCommand(
            tenantId, actor, null, null, null, null, 0, 20)))
            .isInstanceOf(UsageVisibilityDeniedException.class);
    }

    @Test
    void deve_negarFiltroDeDepartamentoForaDoEscopo() {
        UUID visible = UUID.randomUUID();
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.Departments(Set.of(visible), actor));

        assertThatThrownBy(() -> queryUsage.query(new QueryUsageCommand(
            tenantId, actor, UUID.randomUUID(), null, null, null, 0, 20)))
            .isInstanceOf(UsageVisibilityDeniedException.class);
    }

    @Test
    void deve_negarFiltroDeUsuarioDeOutro_noEscopoSelfOnly() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.SelfOnly(actor));

        assertThatThrownBy(() -> queryUsage.query(new QueryUsageCommand(
            tenantId, actor, null, UUID.randomUUID(), null, null, 0, 20)))
            .isInstanceOf(UsageVisibilityDeniedException.class);
    }
}
