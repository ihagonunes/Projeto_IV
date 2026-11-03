package br.com.qsoft.governance.observability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PJ01–PJ04 e PJ06: exatidão do cálculo puro (SPEC-09 §4). */
class BudgetProjectionCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static final YearMonth MONTH = YearMonth.of(2026, 10);

    private static BudgetProjectionFacts facts(BigDecimal budget, int threshold,
                                               BigDecimal spent, BigDecimal windowCost) {
        return new BudgetProjectionFacts(MONTH, TODAY, 14, budget, false, threshold,
            spent, windowCost);
    }

    @Test
    void pj01TabelaDeCasosComValoresCalculadosAMao() {
        record Case(String name, BigDecimal budget, int threshold, BigDecimal spent,
                    BigDecimal windowCost, String average, String projected, String pct,
                    LocalDate exhaustion, BudgetRecommendation recommendation) {
        }
        List<Case> cases = List.of(
            new Case("exemplo da spec", new BigDecimal("1000.00"), 80, new BigDecimal("640.00"),
                new BigDecimal("400.00"), "28.571429", "754.29", "75.43", null,
                BudgetRecommendation.DENTRO_DO_ORCAMENTO),
            new Case("esgota pela média", new BigDecimal("1000.00"), 80, new BigDecimal("700.00"),
                new BigDecimal("1400.00"), "100.000000", "1100.00", "110.00",
                LocalDate.of(2026, 10, 30), BudgetRecommendation.ESGOTA_NO_MES),
            new Case("limiar exato sem média", new BigDecimal("1000.00"), 80, new BigDecimal("800.00"),
                BigDecimal.ZERO, "0.000000", "800.00", "80.00", null,
                BudgetRecommendation.ATENCAO),
            new Case("limiar customizado", new BigDecimal("1000.00"), 50, new BigDecimal("500.00"),
                BigDecimal.ZERO, "0.000000", "500.00", "50.00", null,
                BudgetRecommendation.ATENCAO)
        );

        for (Case item : cases) {
            BudgetProjection projection = BudgetProjectionCalculator.calculate(
                facts(item.budget(), item.threshold(), item.spent(), item.windowCost()));
            assertThat(projection.dailyAverage())
                .as(item.name()).isEqualByComparingTo(item.average());
            assertThat(projection.projected())
                .as(item.name()).isEqualByComparingTo(item.projected());
            assertThat(projection.projectedPct())
                .as(item.name()).isEqualByComparingTo(item.pct());
            assertThat(projection.exhaustionDate())
                .as(item.name()).isEqualTo(item.exhaustion());
            assertThat(projection.recommendation())
                .as(item.name()).isEqualTo(item.recommendation());
            assertThat(projection.remainingDays()).as(item.name()).isEqualTo(4);
            assertThat(projection.calculationVersion()).isEqualTo("projection-v1");
        }
    }

    @Test
    void pj02GastoJaExcedidoRetornaHojeComoEsgotamento() {
        BudgetProjection projection = BudgetProjectionCalculator.calculate(
            facts(new BigDecimal("1000.00"), 80, new BigDecimal("1000.00"), BigDecimal.ZERO));

        assertThat(projection.recommendation()).isEqualTo(BudgetRecommendation.JA_EXCEDIDO);
        assertThat(projection.exhaustionDate()).isEqualTo(TODAY);
        assertThat(projection.projectedPct()).isEqualByComparingTo("100.00");
    }

    @Test
    void pj03EsgotaNoDiaCalculadoPelaMediaAlta() {
        BudgetProjection projection = BudgetProjectionCalculator.calculate(
            new BudgetProjectionFacts(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 24), 14,
                new BigDecimal("1000.00"), false, 80, new BigDecimal("500.00"),
                new BigDecimal("1400.00")));

        assertThat(projection.dailyAverage()).isEqualByComparingTo("100.000000");
        assertThat(projection.remainingDays()).isEqualTo(7);
        assertThat(projection.projected()).isEqualByComparingTo("1200.00");
        assertThat(projection.exhaustionDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(projection.recommendation()).isEqualTo(BudgetRecommendation.ESGOTA_NO_MES);
    }

    @Test
    void pj04SemOrcamentoEOrcamentoZeroSemInventarPercentual() {
        BudgetProjection semOrcamento = BudgetProjectionCalculator.calculate(
            facts(null, 80, new BigDecimal("12.34"), BigDecimal.ZERO));
        assertThat(semOrcamento.recommendation()).isEqualTo(BudgetRecommendation.SEM_ORCAMENTO);
        assertThat(semOrcamento.projectedPct()).isNull();
        assertThat(semOrcamento.exhaustionDate()).isNull();
        assertThat(semOrcamento.projected()).isEqualByComparingTo("12.34");

        BudgetProjection zeroSemGasto = BudgetProjectionCalculator.calculate(
            facts(BigDecimal.ZERO, 80, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(zeroSemGasto.recommendation())
            .isEqualTo(BudgetRecommendation.DENTRO_DO_ORCAMENTO);
        assertThat(zeroSemGasto.projectedPct()).isNull();
        assertThat(zeroSemGasto.exhaustionDate()).isNull();

        BudgetProjection zeroComGasto = BudgetProjectionCalculator.calculate(
            facts(BigDecimal.ZERO, 80, new BigDecimal("0.01"), BigDecimal.ZERO));
        assertThat(zeroComGasto.recommendation()).isEqualTo(BudgetRecommendation.JA_EXCEDIDO);
        assertThat(zeroComGasto.projectedPct()).isNull();
        assertThat(zeroComGasto.exhaustionDate()).isEqualTo(TODAY);
    }

    @Test
    void pj06UltimoDiaDoMesNaoTemDiasRestantesNemProjecaoFutura() {
        BudgetProjection projection = BudgetProjectionCalculator.calculate(
            new BudgetProjectionFacts(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 31), 14,
                new BigDecimal("1000.00"), false, 80, new BigDecimal("500.00"),
                new BigDecimal("1400.00")));

        assertThat(projection.remainingDays()).isZero();
        assertThat(projection.projected()).isEqualByComparingTo("500.00");
        assertThat(projection.exhaustionDate()).isNull();
        assertThat(projection.recommendation())
            .isEqualTo(BudgetRecommendation.DENTRO_DO_ORCAMENTO);
    }

    @Test
    void mesFuturoNaoTemProjecao() {
        BudgetProjectionFacts future = new BudgetProjectionFacts(YearMonth.of(2026, 11),
            TODAY, 14, new BigDecimal("1000.00"), false, 80, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> BudgetProjectionCalculator.calculate(future))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mes futuro");
    }
}
