package br.com.qsoft.governance.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CreditsCalculatorTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 7);
    private static final LocalDate END = MONTH.atEndOfMonth();

    @Test
    void en05_aplicaSaturacaoNormalizacaoEArredondamento() {
        UserId full = UserId.random();
        UserId halfWithLicense = UserId.random();
        UserId noTrainingNoLicense = UserId.random();
        UserId shortUsage = UserId.random();

        EngagementFacts facts = new EngagementFacts(
            Set.of(full, halfWithLicense, noTrainingNoLicense, shortUsage),
            Map.of(
                full, days(10),
                halfWithLicense, days(5),
                noTrainingNoLicense, days(20),
                shortUsage, days(3)),
            Map.of(halfWithLicense, List.of(new LicenseFact(
                UUID.randomUUID(), LocalDate.of(2026, 6, 1), null, true))),
            Map.of(full, List.of(new TrainingFact("c1", LocalDate.of(2026, 7, 10)))),
            new Coverage(4, 4, 0, 0));

        Map<UserId, CreditsItem> items = CreditsCalculator.calculate(facts, MONTH).stream()
            .collect(Collectors.toMap(CreditsItem::userId, Function.identity()));

        assertThat(items.get(full).usage()).isEqualByComparingTo("100.00");
        assertThat(items.get(full).training()).isEqualByComparingTo("100.00");
        assertThat(items.get(full).license()).isNull();
        assertThat(items.get(full).licenseApplicability())
            .isEqualTo(LicenseApplicability.NOT_APPLICABLE);
        assertThat(items.get(full).score()).isEqualByComparingTo("100.00");

        assertThat(items.get(halfWithLicense).usage()).isEqualByComparingTo("50.00");
        assertThat(items.get(halfWithLicense).training()).isEqualByComparingTo("0.00");
        assertThat(items.get(halfWithLicense).license()).isEqualByComparingTo("100.00");
        assertThat(items.get(halfWithLicense).score()).isEqualByComparingTo("45.00");

        assertThat(items.get(noTrainingNoLicense).usage()).isEqualByComparingTo("100.00");
        assertThat(items.get(noTrainingNoLicense).score()).isEqualByComparingTo("62.50");

        assertThat(items.get(shortUsage).usage()).isEqualByComparingTo("30.00");
        assertThat(items.get(shortUsage).score()).isEqualByComparingTo("18.75");
        assertThat(items.values())
            .allMatch(item -> item.dataStatus() == DataStatus.COMPLETE);
    }

    @Test
    void en05_licencaAplicavelSemUsoZeraOComponenteL() {
        UserId user = UserId.random();
        EngagementFacts facts = new EngagementFacts(
            Set.of(user), Map.of(), Map.of(user, List.of(new LicenseFact(
                UUID.randomUUID(), LocalDate.of(2026, 6, 1), null, true))),
            Map.of(), new Coverage(1, 1, 0, 0));

        CreditsItem item = CreditsCalculator.calculate(facts, MONTH).get(0);

        assertThat(item.usage()).isEqualByComparingTo("0.00");
        assertThat(item.license()).isEqualByComparingTo("0.00");
        assertThat(item.score()).isEqualByComparingTo("0.00");
    }

    @Test
    void en05_treinamentoDepoisDoMesNaoConta() {
        UserId user = UserId.random();
        EngagementFacts facts = new EngagementFacts(
            Set.of(user), Map.of(user, days(10)), Map.of(),
            Map.of(user, List.of(new TrainingFact("c1", END.plusDays(1)))),
            new Coverage(1, 1, 0, 0));

        CreditsItem item = CreditsCalculator.calculate(facts, MONTH).get(0);

        assertThat(item.training()).isEqualByComparingTo("0.00");
    }

    private static Set<LocalDate> days(int count) {
        java.util.Set<LocalDate> days = new java.util.LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            days.add(LocalDate.of(2026, 7, 1).plusDays(i));
        }
        return days;
    }
}
