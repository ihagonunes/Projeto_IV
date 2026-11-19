package br.com.qsoft.governance.department.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.qsoft.governance.department.api.spending.SpendingDeniedException;
import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.application.port.SpendingStore;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.SpendingPlan;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManageSpendingTest {
    final TenantId tenant = TenantId.random();
    final UserId user = UserId.random();
    final UUID department = UUID.randomUUID(), model = UUID.randomUUID();
    final YearMonth month = YearMonth.of(2026, 11);
    final SpendingStore store = mock(SpendingStore.class);
    final IdentityQuery identity = mock(IdentityQuery.class);
    final AuthorizationQuery authorization = mock(AuthorizationQuery.class);
    final DepartmentRepository departments = mock(DepartmentRepository.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZoneOffset.UTC);
    ManageSpending service;
    UserContext person;

    BigDecimal value(String number) { return new BigDecimal(number); }
    SpendingPlan plan(String limit) {
        return new SpendingPlan(value("100"), List.of(new SpendingPlan.DepartmentShare(department, value("100"))),
            List.of(new SpendingPlan.Allocation(user.value(), department, value(limit))),
            Map.of("COLLABORATOR", BigDecimal.ONE),
            List.of(new SpendingPlan.ModelRate(model, "USD", value("5"), "Taxa de teste 2026-11-18")));
    }
    @BeforeEach void setup() {
        person = new UserContext(user, tenant, "COLLABORATOR", department, "ACTIVE", "Pessoa");
        when(authorization.isAllowed(any(), any(), any(), any(), any())).thenReturn(true);
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(person));
        when(identity.getUserContext(user)).thenReturn(Optional.of(person));
        var dep = Department.create(department, tenant, "Equipe", "", null, BigDecimal.ZERO);
        when(departments.findByTenantIdAndId(tenant, department)).thenReturn(Optional.of(dep));
        when(departments.findByTenantId(tenant)).thenReturn(List.of(dep));
        when(store.lock(tenant.value())).thenReturn(new SpendingStore.Settings("America/Sao_Paulo", 80));
        when(store.plan(tenant.value(), month)).thenReturn(Optional.of(plan("100")));
        when(store.usage(tenant.value(), month, user.value())).thenReturn(new SpendingStore.Usage(BigDecimal.ZERO, BigDecimal.ZERO));
        when(store.companyUsage(any(), any())).thenReturn(new SpendingStore.Usage(BigDecimal.ZERO, BigDecimal.ZERO));
        when(store.departmentUsage(any(), any(), any())).thenReturn(new SpendingStore.Usage(BigDecimal.ZERO, BigDecimal.ZERO));
        service = new ManageSpending(store, identity, authorization, departments, clock);
    }
    @Test void reservesConvertedMaximumAndProtectsRemainingBalance() {
        service.reserve(tenant, user, model, UUID.randomUUID(), value("10"));
        verify(store).reserve(eq(tenant.value()), argThat(r -> r.reserved().compareTo(value("50")) == 0
            && r.exchangeRate().compareTo(value("5")) == 0 && r.month().equals(month)));
        when(store.usage(tenant.value(), month, user.value())).thenReturn(new SpendingStore.Usage(value("80"), value("10")));
        assertThatThrownBy(() -> service.reserve(tenant, user, model, UUID.randomUUID(), value("3")))
            .isInstanceOf(SpendingDeniedException.class).hasMessageContaining("Saldo insuficiente");
    }
    @Test void thresholdUsesConfirmedSpendingAndReservationsReduceBalance() {
        when(store.usage(tenant.value(), month, user.value())).thenReturn(new SpendingStore.Usage(value("79"), value("20")));
        assertThat(service.own(tenant, user).alert()).isFalse();
        assertThat(service.own(tenant, user).available()).isEqualByComparingTo("1");
        when(store.usage(tenant.value(), month, user.value())).thenReturn(new SpendingStore.Usage(value("80"), value("0")));
        assertThat(service.own(tenant, user).alert()).isTrue();
    }
    @Test void noPlanOrCurrencyFailsClosed() {
        when(store.plan(tenant.value(), month)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reserve(tenant, user, model, UUID.randomUUID(), value("1")))
            .isInstanceOf(SpendingDeniedException.class);
        when(store.plan(tenant.value(), month)).thenReturn(Optional.of(plan("100")));
        assertThatThrownBy(() -> service.reserve(tenant, user, UUID.randomUUID(), UUID.randomUUID(), value("1")))
            .isInstanceOf(SpendingDeniedException.class).hasMessageContaining("moeda");
    }
    @Test void roleChecksRunBeforeReadingFinanceAndCannotCrossTenant() {
        when(authorization.isAllowed(any(), any(), any(), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.overview(tenant, user, month)).isInstanceOf(SpendingDeniedException.class);
        verifyNoInteractions(store);
        when(authorization.isAllowed(any(), any(), any(), any(), any())).thenReturn(true);
        when(identity.getUserContext(user)).thenReturn(Optional.of(new UserContext(user, TenantId.random(), "COLLABORATOR", department, "ACTIVE")));
        assertThatThrownBy(() -> service.reserve(tenant, user, model, UUID.randomUUID(), BigDecimal.ONE))
            .isInstanceOf(SpendingDeniedException.class);
        verify(store, never()).reserve(any(), any());
    }
    @Test void ignoresManualLimitsAndRejectsBudgetBelowCommittedSpending() {
        service.save(tenant, user, month, plan("1"));
        verify(store).save(eq(tenant.value()), eq(month), argThat(p -> p.allocations().isEmpty()));
        assertThat(service.own(tenant, user).limit()).isEqualByComparingTo("100");
        when(store.companyUsage(tenant.value(), month)).thenReturn(new SpendingStore.Usage(value("80"), value("21")));
        assertThatThrownBy(() -> service.save(tenant, user, month, plan("100")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("gastos e reservas");
    }
    @Test void newEmployeesChangeCalculatedLimitsAndDepartmentCeilingStillApplies() {
        var colleague = new UserContext(UserId.random(), tenant, "COLLABORATOR", department, "ACTIVE");
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(person, colleague));
        assertThat(service.own(tenant, user).limit()).isEqualByComparingTo("50");
        when(store.departmentUsage(tenant.value(), month, department))
            .thenReturn(new SpendingStore.Usage(value("80"), BigDecimal.ZERO));
        assertThat(service.own(tenant, user).available()).isEqualByComparingTo("20");
        assertThatThrownBy(() -> service.reserve(tenant, user, model, UUID.randomUUID(), value("5")))
            .isInstanceOf(SpendingDeniedException.class);
    }
    @Test void knownCostSettlesUsingFrozenRateAndSecondDeliveryIsIdempotent() {
        UUID request = UUID.randomUUID();
        var r = new SpendingStore.Reservation(request, user.value(), month, value("50"), null, value("5"), "USD", "origem", "PENDING", clock.instant());
        when(store.reservation(tenant.value(), request)).thenReturn(Optional.of(r));
        service.settle(tenant, request, value("2"));
        verify(store).settle(tenant.value(), request, value("10.00000000"));
        when(store.reservation(tenant.value(), request)).thenReturn(Optional.of(new SpendingStore.Reservation(request,
            user.value(), month, value("50"), value("10"), value("5"), "USD", "origem", "SETTLED", clock.instant())));
        service.settle(tenant, request, value("2"));
        verify(store, times(1)).settle(any(), any(), any());
    }
    @Test void renewalUsesCompanyTimezoneAndDoesNotCarryConsumption() {
        service = new ManageSpending(store, identity, authorization, departments,
            Clock.fixed(Instant.parse("2026-12-01T02:30:00Z"), ZoneOffset.UTC));
        assertThat(service.own(tenant, user).month()).isEqualTo("2026-11");
        service = new ManageSpending(store, identity, authorization, departments,
            Clock.fixed(Instant.parse("2026-12-01T03:01:00Z"), ZoneOffset.UTC));
        when(store.previous(tenant.value(), month.plusMonths(1))).thenReturn(Optional.of(plan("100")));
        when(store.usage(tenant.value(), month.plusMonths(1), user.value()))
            .thenReturn(new SpendingStore.Usage(BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(service.own(tenant, user).spent()).isZero();
        verify(store).save(eq(tenant.value()), eq(month.plusMonths(1)), argThat(p -> p.allocations().isEmpty()));
    }
    @Test void automaticDistributionUsesWeightsAndLeavesRoundingResidualUnallocated() {
        UserContext other = new UserContext(UserId.random(), tenant, "COLLABORATOR", department, "ACTIVE");
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(person, other));
        var proposed = service.suggest(tenant, user, plan("100"));
        assertThat(proposed.allocations()).hasSize(2).allMatch(a -> a.limit().compareTo(value("50")) == 0);
        verify(store, never()).save(any(), any(), any());
    }
    @Test void hierarchyWeightsDetermineEqualLimitsWithinEachGroup() {
        var manager = new UserContext(UserId.random(), tenant, "MANAGER", department, "ACTIVE");
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(person, manager));
        var original = plan("100");
        var policy = new SpendingPlan(original.amount(), original.departments(), List.of(),
            Map.of("COLLABORATOR", BigDecimal.ONE, "MANAGER", value("2")), original.rates());
        when(store.plan(tenant.value(), month)).thenReturn(Optional.of(policy));
        assertThat(service.own(tenant, user).limit()).isEqualByComparingTo("33.33");
        var calculated = service.suggest(tenant, user, policy);
        assertThat(calculated.allocations().stream().filter(a -> a.userId().equals(manager.userId().value())).findFirst().orElseThrow().limit())
            .isEqualByComparingTo("66.66");
    }
    @Test void inactiveDepartmentRetainsCommittedBudgetWithoutGrantingNewLimits() {
        var inactive = Department.create(department, tenant, "Equipe", "", null, BigDecimal.ZERO).deactivate();
        when(departments.findByTenantId(tenant)).thenReturn(List.of(inactive));
        when(departments.findByTenantIdAndId(tenant, department)).thenReturn(Optional.of(inactive));
        when(store.companyUsage(tenant.value(), month)).thenReturn(new SpendingStore.Usage(value("50"), BigDecimal.ZERO));
        when(store.departmentUsage(tenant.value(), month, department)).thenReturn(new SpendingStore.Usage(value("50"), BigDecimal.ZERO));
        service.save(tenant, user, month, plan("100"));
        assertThat(service.own(tenant, user).configured()).isFalse();
        verify(store).save(eq(tenant.value()), eq(month), argThat(p -> p.allocations().isEmpty()));
    }
    @Test void graphOfSharesRejectsOverspendingAndDuplicatePeople() {
        assertThatThrownBy(() -> plan("101")).isInstanceOf(IllegalArgumentException.class);
        var original = plan("100");
        assertThatThrownBy(() -> new SpendingPlan(original.amount(), original.departments(),
            List.of(original.allocations().get(0), original.allocations().get(0)), original.weights(), original.rates()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpendingPlan(original.amount(), original.departments(), original.allocations(),
            original.weights(), List.of(new SpendingPlan.ModelRate(model, "USD", value("1E+30"), "Taxa inválida"))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
