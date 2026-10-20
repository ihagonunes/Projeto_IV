package br.com.qsoft.governance.department.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Validação e upsert do orçamento mensal (SPEC-02 §1/§2). */
class MonthlyBudgetTest {

    private final TenantId tenantId = TenantId.random();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final YearMonth month = YearMonth.of(2026, 10);

    @Test
    void criaOrcamentoValido() {
        MonthlyBudget budget = MonthlyBudget.create(UUID.randomUUID(), tenantId, departmentId,
            month, new BigDecimal("1000.00"), 80, actor);

        assertThat(budget.month()).isEqualTo(month);
        assertThat(budget.amount()).isEqualByComparingTo("1000.00");
        assertThat(budget.alertThresholdPct()).isEqualTo(80);
        assertThat(budget.updatedBy()).isEqualTo(actor);
    }

    @Test
    void rejeitaValorNegativoELimiarForaDaFaixa() {
        assertThatThrownBy(() -> MonthlyBudget.create(UUID.randomUUID(), tenantId, departmentId,
            month, new BigDecimal("-0.01"), 80, actor))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MonthlyBudget.create(UUID.randomUUID(), tenantId, departmentId,
            month, BigDecimal.TEN, 0, actor))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MonthlyBudget.create(UUID.randomUUID(), tenantId, departmentId,
            month, BigDecimal.TEN, 101, actor))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeAtualizaValorLimiarEAutor() {
        UUID newActor = UUID.randomUUID();
        MonthlyBudget budget = MonthlyBudget.create(UUID.randomUUID(), tenantId, departmentId,
            month, BigDecimal.TEN, 80, actor);

        budget.change(new BigDecimal("2500.00"), 50, newActor);

        assertThat(budget.amount()).isEqualByComparingTo("2500.00");
        assertThat(budget.alertThresholdPct()).isEqualTo(50);
        assertThat(budget.updatedBy()).isEqualTo(newActor);
        assertThat(budget.updatedAt()).isAfterOrEqualTo(budget.createdAt());
    }
}
