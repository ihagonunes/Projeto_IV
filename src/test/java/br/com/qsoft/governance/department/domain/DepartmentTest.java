package br.com.qsoft.governance.department.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepartmentTest {

    private static final TenantId TENANT = TenantId.random();

    private Department department() {
        return Department.create(UUID.randomUUID(), TENANT, "Marketing", "Time de marketing",
            null, new BigDecimal("1000.00"));
    }

    @Test
    void shouldCreateActiveDepartmentTrimmingFields() {
        Department department = Department.create(UUID.randomUUID(), TENANT,
            "  Marketing  ", "  Time  ", null, new BigDecimal("500.00"));

        assertThat(department.name()).isEqualTo("Marketing");
        assertThat(department.description()).isEqualTo("Time");
        assertThat(department.status()).isEqualTo(DepartmentStatus.ACTIVE);
        assertThat(department.monthlyBudget()).isEqualByComparingTo("500.00");
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> Department.create(UUID.randomUUID(), TENANT, "   ", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeBudget() {
        assertThatThrownBy(() -> Department.create(UUID.randomUUID(), TENANT, "Marketing", null, null,
            new BigDecimal("-1.00")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowNullBudgetAndDescription() {
        Department department = Department.create(UUID.randomUUID(), TENANT, "Marketing", null, null, null);

        assertThat(department.monthlyBudget()).isNull();
        assertThat(department.description()).isNull();
    }

    @Test
    void shouldClearDescriptionWhenBlank() {
        Department department = department();

        department.changeDescription("   ");

        assertThat(department.description()).isNull();
    }

    @Test
    void shouldDeactivateAndActivate() {
        Department department = department();

        assertThat(department.deactivate().status()).isEqualTo(DepartmentStatus.INACTIVE);
        assertThat(department.activate().status()).isEqualTo(DepartmentStatus.ACTIVE);
    }

    @Test
    void shouldChangeNameLeaderAndBudget() {
        Department department = department();
        UUID leader = UUID.randomUUID();

        department.rename("Vendas").changeLeader(leader).changeMonthlyBudget(new BigDecimal("2000.00"));

        assertThat(department.name()).isEqualTo("Vendas");
        assertThat(department.leaderUserId()).isEqualTo(leader);
        assertThat(department.monthlyBudget()).isEqualByComparingTo("2000.00");
    }
}
