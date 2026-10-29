package br.com.qsoft.governance.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdoptionCalculatorTest {

    private static final LocalDate ATE = LocalDate.of(2026, 7, 31);

    @Test
    void en01_janelaDeTrintaDiasEhInclusiva() {
        UserId boundary = UserId.random();
        UserId outside = UserId.random();
        EngagementFacts facts = facts(
            Set.of(boundary, outside),
            Map.of(
                boundary, Set.of(ATE.minusDays(29)),
                outside, Set.of(ATE.minusDays(30))),
            Map.of());

        AdoptionResult result = AdoptionCalculator.of(facts, ATE);

        assertThat(result.eligible()).isEqualTo(2);
        assertThat(result.active()).isEqualTo(1);
        assertThat(result.inactive()).isEqualTo(1);
        assertThat(result.adoptionRate()).isEqualByComparingTo("50.00");
        assertThat(result.dataStatus()).isEqualTo(DataStatus.COMPLETE);
    }

    @Test
    void en01_contaLicencaOciosaDeQuemNaoUsouNaJanela() {
        UserId active = UserId.random();
        UserId idle = UserId.random();
        LicenseFact license = new LicenseFact(UUID.randomUUID(),
            ATE.minusMonths(1), null, true);
        EngagementFacts facts = facts(
            Set.of(active, idle),
            Map.of(active, Set.of(ATE)),
            Map.of(idle, List.of(license)));

        AdoptionResult result = AdoptionCalculator.of(facts, ATE);

        assertThat(result.active()).isEqualTo(1);
        assertThat(result.idleLicenses()).isEqualTo(1);
    }

    @Test
    void en02_semElegiveisRetornaTaxaNulaEInsuficiente() {
        AdoptionResult result = AdoptionCalculator.of(facts(Set.of(), Map.of(), Map.of()), ATE);

        assertThat(result.adoptionRate()).isNull();
        assertThat(result.eligible()).isZero();
        assertThat(result.dataStatus()).isEqualTo(DataStatus.INSUFFICIENT_DATA);
    }

    private static EngagementFacts facts(Set<UserId> eligible,
                                         Map<UserId, Set<LocalDate>> activeDays,
                                         Map<UserId, List<LicenseFact>> licenses) {
        return new EngagementFacts(eligible, activeDays, licenses, Map.of(),
            new Coverage(eligible.size(), eligible.size(), 0, 0));
    }
}
