package br.com.qsoft.governance.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EngagementDomainBoundariesTest {
    private static final LocalDate REFERENCE = LocalDate.of(2026, 7, 31);
    private final UserId member = UserId.random();

    @Test
    void absentFactsAndQuadrantUsersDefaultToEmpty() {
        var facts = new EngagementFacts(null, null, null, null, new Coverage(0, 0, 0, 0));

        assertThat(facts.eligible()).isEmpty();
        assertThat(facts.activeDays()).isEmpty();
        assertThat(facts.licenses()).isEmpty();
        assertThat(facts.trainings()).isEmpty();
        assertThat(new TrainingCrossResult.Quadrant(null).total()).isZero();
    }

    @Test
    void adoptionIgnoresFutureUsageAndLicensesOutsideTheirValidity() {
        var licenses = List.of(new LicenseFact(null, null, null, true),
            new LicenseFact(null, REFERENCE.plusDays(1), null, true),
            new LicenseFact(null, REFERENCE.minusDays(2), REFERENCE.minusDays(1), true),
            new LicenseFact(null, REFERENCE.minusDays(2), null, false),
            new LicenseFact(null, REFERENCE, REFERENCE, true));
        var facts = new EngagementFacts(Set.of(member), Map.of(member, Set.of(REFERENCE.plusDays(1))),
            Map.of(member, licenses), Map.of(), new Coverage(1, 1, 0, 0), false, true);

        assertThat(AdoptionCalculator.of(facts, REFERENCE).active()).isZero();
        assertThat(AdoptionCalculator.of(facts, REFERENCE).dataStatus()).isEqualTo(DataStatus.PARTIAL);
        assertThat(AdoptionCalculator.applicableLicenses(licenses, REFERENCE)).containsExactly(licenses.getLast());
    }

    @Test
    void unknownTrainingDateDoesNotMarkMemberTrainedAndUnavailableSourcesArePartial() {
        var facts = new EngagementFacts(Set.of(member), Map.of(), Map.of(),
            Map.of(member, List.of(new TrainingFact("IA", null))), new Coverage(1, 1, 0, 0));
        assertThat(TrainingCrossCalculator.of(facts, REFERENCE).notTrainedInactive().users()).containsExactly(member);
        assertThat(CreditsCalculator.calculate(facts, YearMonth.from(REFERENCE)).getFirst().training()).isZero();

        var partial = new EngagementFacts(facts.eligible(), facts.activeDays(), facts.licenses(), facts.trainings(),
            facts.coverage(), false, false);
        CreditsItem item = CreditsCalculator.calculate(partial, YearMonth.from(REFERENCE)).getFirst();

        assertThat(item.training()).isNull();
        assertThat(item.license()).isNull();
        assertThat(item.score()).isZero();
        assertThat(item.dataStatus()).isEqualTo(DataStatus.PARTIAL);
    }

    @Test
    void creditsReweightWhenOnlyOneSourceIsUnavailable() {
        YearMonth month = YearMonth.from(REFERENCE);
        var activity = Map.of(member, Set.of(REFERENCE));
        var licenses = Map.of(member, List.of(new LicenseFact(null, REFERENCE, null, true)));
        var training = Map.of(member, List.of(new TrainingFact("IA", REFERENCE)));
        var coverage = new Coverage(1, 1, 0, 0);
        var withoutLicense = new EngagementFacts(Set.of(member), activity, licenses, training, coverage, false, true);
        var withoutTraining = new EngagementFacts(Set.of(member), activity, licenses, training, coverage, true, false);

        CreditsItem licenseMissing = CreditsCalculator.calculate(withoutLicense, month).getFirst();
        assertThat(licenseMissing.score()).isEqualByComparingTo("43.75");
        assertThat(licenseMissing.dataStatus()).isEqualTo(DataStatus.PARTIAL);
        CreditsItem trainingMissing = CreditsCalculator.calculate(withoutTraining, month).getFirst();
        assertThat(trainingMissing.score()).isEqualByComparingTo("35.71");
        assertThat(trainingMissing.dataStatus()).isEqualTo(DataStatus.PARTIAL);
    }
}
