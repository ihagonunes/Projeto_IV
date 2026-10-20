package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.api.exception.AIAttemptAccessDeniedException;
import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.CostStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
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

/** Escopo e filtros da RF047 (ADR-36). */
class QueryRequestAttemptsTest {

    private final TenantId tenantId = TenantId.random();
    private final UserId actor = UserId.random();
    private final UUID departmentA = UUID.randomUUID();
    private final UUID departmentB = UUID.randomUUID();

    private final RequestAttemptRepository repository = mock(RequestAttemptRepository.class);
    private final AuthorizationQuery authorizationQuery = mock(AuthorizationQuery.class);
    private final QueryRequestAttempts useCase =
        new QueryRequestAttempts(repository, authorizationQuery);

    @BeforeEach
    void setUp() {
        when(repository.search(any(), anyInt(), anyInt())).thenReturn(
            new PageResult<>(List.of(), 0, 0, 20));
    }

    @Test
    void tenantWideVeTudoEPassaFiltros() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.TenantWide());

        useCase.query(command(departmentB, UUID.randomUUID(), ExecutionStatus.SUCCEEDED,
            DeliveryStatus.DELIVERED, 0, 20));

        ArgumentCaptor<RequestAttemptRepository.AttemptFilter> captor =
            ArgumentCaptor.forClass(RequestAttemptRepository.AttemptFilter.class);
        verify(repository).search(captor.capture(), org.mockito.ArgumentMatchers.eq(0),
            org.mockito.ArgumentMatchers.eq(20));
        RequestAttemptRepository.AttemptFilter filter = captor.getValue();
        assertThat(filter.visibility()).isInstanceOf(VisibilityScope.TenantWide.class);
        assertThat(filter.departmentId()).isEqualTo(departmentB);
        assertThat(filter.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(filter.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void semVisibilidadeRetorna403() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.None());

        assertThatThrownBy(() -> useCase.query(command(null, null, null, null, 0, 20)))
            .isInstanceOf(AIAttemptAccessDeniedException.class);
    }

    @Test
    void departamentoForaDoEscopoRetorna403() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.Departments(Set.of(departmentA), actor));

        assertThatThrownBy(() -> useCase.query(
            command(departmentB, null, null, null, 0, 20)))
            .isInstanceOf(AIAttemptAccessDeniedException.class);
    }

    @Test
    void departamentoNoEscopoEhAceito() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.Departments(Set.of(departmentA), actor));

        useCase.query(command(departmentA, actor.value(), null, null, 0, 20));

        verify(repository).search(any(), anyInt(), anyInt());
    }

    @Test
    void filtroPorOutroUsuarioRetorna403() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.SelfOnly(actor));

        assertThatThrownBy(() -> useCase.query(
            command(null, UUID.randomUUID(), null, null, 0, 20)))
            .isInstanceOf(AIAttemptAccessDeniedException.class);
    }

    @Test
    void selfOnlyAceitaOFiltroDoProprioUsuario() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.SelfOnly(actor));

        useCase.query(command(null, actor.value(), null, null, 0, 20));

        verify(repository).search(any(), anyInt(), anyInt());
    }

    @Test
    void paginaESizeSaoNormalizados() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.TenantWide());

        useCase.query(command(null, null, null, null, -3, 500));

        verify(repository).search(any(), org.mockito.ArgumentMatchers.eq(0),
            org.mockito.ArgumentMatchers.eq(100));

        useCase.query(command(null, null, null, null, 0, 0));

        verify(repository).search(any(), org.mockito.ArgumentMatchers.eq(0),
            org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    void deve_repassarFiltroDeCustoInconclusivo() {
        when(authorizationQuery.visibilityFor(tenantId, actor, Resource.USAGE, Action.READ))
            .thenReturn(new VisibilityScope.TenantWide());

        useCase.query(new QueryRequestAttemptsCommand(tenantId, actor, null, null, null, null,
            CostStatus.INCONCLUSIVE, null, null, 0, 20));

        ArgumentCaptor<RequestAttemptRepository.AttemptFilter> captor =
            ArgumentCaptor.forClass(RequestAttemptRepository.AttemptFilter.class);
        verify(repository).search(captor.capture(), anyInt(), anyInt());
        assertThat(captor.getValue().costStatus()).isEqualTo(CostStatus.INCONCLUSIVE);
    }

    private QueryRequestAttemptsCommand command(UUID departmentId, UUID userId,
                                                ExecutionStatus status,
                                                DeliveryStatus delivery,
                                                int page, int size) {
        return new QueryRequestAttemptsCommand(tenantId, actor, Instant.now().minusSeconds(3600),
            Instant.now(), status, delivery, null, userId, departmentId, page, size);
    }
}
