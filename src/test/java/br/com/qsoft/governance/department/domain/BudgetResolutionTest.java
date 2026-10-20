package br.com.qsoft.governance.department.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Precedência: linha do mês &gt; padrão (limiar 80) &gt; sem orçamento. */
class BudgetResolutionTest {

    private final YearMonth month = YearMonth.of(2026, 10);

    private MonthlyBudget monthRow(BigDecimal amount, int threshold) {
        return MonthlyBudget.create(UUID.randomUUID(), TenantId.random(), UUID.randomUUID(), month,
            amount, threshold, UUID.randomUUID());
    }

    @Test
    void linhaDoMesTemPrecedenciaSobreOPadrao() {
        BudgetResolution resolution = BudgetResolution.resolve(
            monthRow(new BigDecimal("500.00"), 50), new BigDecimal("1000.00"));

        assertThat(resolution.hasBudget()).isTrue();
        assertThat(resolution.amount()).isEqualByComparingTo("500.00");
        assertThat(resolution.alertThresholdPct()).isEqualTo(50);
        assertThat(resolution.fromDefault()).isFalse();
    }

    @Test
    void usaOPadraoComLimiar80QuandoNaoHaLinhaDoMes() {
        BudgetResolution resolution = BudgetResolution.resolve(null, new BigDecimal("1000.00"));

        assertThat(resolution.hasBudget()).isTrue();
        assertThat(resolution.amount()).isEqualByComparingTo("1000.00");
        assertThat(resolution.alertThresholdPct()).isEqualTo(80);
        assertThat(resolution.fromDefault()).isTrue();
    }

    @Test
    void semLinhaESemPadraoNaoHaOrcamento() {
        BudgetResolution resolution = BudgetResolution.resolve(null, null);

        assertThat(resolution.hasBudget()).isFalse();
        assertThat(resolution.amount()).isNull();
        assertThat(resolution.fromDefault()).isFalse();
    }
}
