package br.com.qsoft.governance.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import br.com.qsoft.governance.department.api.spending.SpendingDeniedException;
import br.com.qsoft.governance.department.application.ManageSpending;
import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.SpendingPlan;
import br.com.qsoft.governance.department.infrastructure.persistence.SpendingJdbcStore;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class SpendingLedgerIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine")
        .withDatabaseName("spending_test").withUsername("qsoft").withPassword("test_only_password");
    static DriverManagerDataSource dataSource;
    SpendingJdbcStore store;
    ManageSpending service;
    TransactionTemplate tx;
    TenantId tenant = TenantId.random();
    UserId user = UserId.random();
    UUID department = UUID.randomUUID(), model = UUID.randomUUID();
    YearMonth month = YearMonth.now(ZoneId.of("America/Sao_Paulo"));

    @BeforeAll static void migrate() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }
    <T> T call(Supplier<T> operation) { return tx.execute(status -> operation.get()); }
    @BeforeEach void setup() {
        store = new SpendingJdbcStore(new JdbcTemplate(dataSource), new ObjectMapper());
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var identity = mock(IdentityQuery.class);
        var auth = mock(AuthorizationQuery.class);
        var departments = mock(DepartmentRepository.class);
        var person = new UserContext(user, tenant, "COLLABORATOR", department, "ACTIVE", "Teste");
        when(identity.eligibleUsers(tenant)).thenReturn(List.of(person));
        when(identity.getUserContext(user)).thenReturn(Optional.of(person));
        when(auth.isAllowed(any(), any(), any(), any(), any())).thenReturn(true);
        var dep = Department.create(department, tenant, "Equipe", null, null, BigDecimal.ZERO);
        when(departments.findByTenantIdAndId(tenant, department)).thenReturn(Optional.of(dep));
        when(departments.findByTenantId(tenant)).thenReturn(List.of(dep));
        service = new ManageSpending(store, identity, auth, departments);
        var plan = new SpendingPlan(new BigDecimal("100"),
            List.of(new SpendingPlan.DepartmentShare(department, new BigDecimal("100"))),
            List.of(new SpendingPlan.Allocation(user.value(), department, new BigDecimal("100"))),
            Map.of("COLLABORATOR", BigDecimal.ONE),
            List.of(new SpendingPlan.ModelRate(model, "BRL", BigDecimal.ONE, "Preço em reais")));
        call(() -> { service.save(tenant, user, month, plan); return null; });
    }
    @Test void concurrentReservationsCannotSpendSameBalance() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> reserve = () -> {
                start.await();
                try {
                    call(() -> { service.reserve(tenant, user, model, UUID.randomUUID(), new BigDecimal("60")); return null; });
                    return true;
                } catch (SpendingDeniedException ex) { return false; }
            };
            var a = executor.submit(reserve);
            var b = executor.submit(reserve);
            start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }
        var balance = call(() -> service.own(tenant, user));
        assertThat(balance.reserved()).isEqualByComparingTo("60");
        assertThat(balance.available()).isEqualByComparingTo("40");
    }
    @Test void settlementIsDurableAndIdempotentAndQueriesAreTenantIsolated() {
        UUID request = UUID.randomUUID();
        call(() -> { service.reserve(tenant, user, model, request, new BigDecimal("60")); return null; });
        assertThat(call(() -> store.reservation(UUID.randomUUID(), request))).isEmpty();
        call(() -> { service.settle(tenant, request, new BigDecimal("23.45")); return null; });
        call(() -> { service.settle(tenant, request, new BigDecimal("23.45")); return null; });
        var balance = call(() -> service.own(tenant, user));
        assertThat(balance.reserved()).isZero();
        assertThat(balance.spent()).isEqualByComparingTo("23.45");
        assertThat(balance.available()).isEqualByComparingTo("76.55");
        assertThat(call(() -> store.usage(UUID.randomUUID(), month, user.value())).spent()).isZero();
        assertThatThrownBy(() -> call(() -> { service.reserve(TenantId.random(), user, model, UUID.randomUUID(), BigDecimal.ONE); return null; }))
            .isInstanceOf(SpendingDeniedException.class);
    }
    @Test void unknownCostKeepsReservationAndFailedTransactionRollsBackIt() {
        UUID request = UUID.randomUUID();
        assertThatThrownBy(() -> call(() -> {
            service.reserve(tenant, user, model, request, new BigDecimal("60"));
            throw new IllegalArgumentException("simular rollback");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(call(() -> store.reservation(tenant.value(), request))).isEmpty();
        call(() -> { service.reserve(tenant, user, model, request, new BigDecimal("60")); return null; });
        assertThat(call(() -> service.own(tenant, user)).reserved()).isEqualByComparingTo("60");
    }

    @Test void migrationRecoversDepartmentFromLegacyAllocationWithoutLosingCharges() {
        String database = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(dataSource).execute("CREATE DATABASE " + database);
        var legacySource = new DriverManagerDataSource("jdbc:postgresql://" + POSTGRES.getHost() + ":"
            + POSTGRES.getMappedPort(5432) + "/" + database, POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(legacySource).target("25").load().migrate();
        var jdbc = new JdbcTemplate(legacySource);
        var plan = new SpendingPlan(new BigDecimal("100"),
            List.of(new SpendingPlan.DepartmentShare(department, new BigDecimal("100"))),
            List.of(new SpendingPlan.Allocation(user.value(), department, new BigDecimal("100"))),
            Map.of("COLLABORATOR", BigDecimal.ONE), List.of());
        jdbc.update("INSERT INTO department_spending_plans(tenant_id,month,plan) VALUES (?,?,?::jsonb)",
            tenant.value(), month.toString(), new ObjectMapper().writeValueAsString(plan));
        UUID request = UUID.randomUUID();
        jdbc.update("INSERT INTO department_spending_reservations(tenant_id,request_id,user_id,month,reserved,actual,exchange_rate,currency,rate_reference,state) VALUES (?,?,?,?,5,5,1,'BRL','Teste legado','SETTLED')",
            tenant.value(), request, user.value(), month.toString());
        Flyway.configure().dataSource(legacySource).load().migrate();
        assertThat(jdbc.queryForObject("SELECT department_id FROM department_spending_reservations WHERE tenant_id=? AND request_id=?",
            UUID.class, tenant.value(), request)).isEqualTo(department);
        assertThat(jdbc.queryForObject("SELECT actual FROM department_spending_reservations WHERE tenant_id=? AND request_id=?",
            BigDecimal.class, tenant.value(), request)).isEqualByComparingTo("5");
    }
}
