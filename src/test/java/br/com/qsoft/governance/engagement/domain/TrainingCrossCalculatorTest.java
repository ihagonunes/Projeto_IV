package br.com.qsoft.governance.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrainingCrossCalculatorTest {

    private static final LocalDate ATE = LocalDate.of(2026, 7, 31);

    @Test
    void en04_treinamentoConcluidoDepoisDoAteNaoConta() {
        UserId completed = UserId.random();
        UserId future = UserId.random();
        EngagementFacts facts = new EngagementFacts(
            Set.of(completed, future),
            Map.of(completed, Set.of(ATE), future, Set.of(ATE)),
            Map.of(),
            Map.of(
                completed, List.of(new TrainingFact("segurança", ATE)),
                future, List.of(new TrainingFact("segurança", ATE.plusDays(1)))),
            new Coverage(2, 2, 0, 0));

        TrainingCrossResult result = TrainingCrossCalculator.of(facts, ATE);

        assertThat(result.trainedActive().users()).containsExactly(completed);
        assertThat(result.trainedInactive().users()).isEmpty();
        assertThat(result.notTrainedActive().users()).containsExactly(future);
        assertThat(result.notTrainedInactive().users()).isEmpty();
    }

    @Test
    void separaOsQuatroQuadrantes() {
        UserId trainedActive = UserId.random();
        UserId trainedInactive = UserId.random();
        UserId activeOnly = UserId.random();
        UserId neither = UserId.random();
        EngagementFacts facts = new EngagementFacts(
            Set.of(trainedActive, trainedInactive, activeOnly, neither),
            Map.of(trainedActive, Set.of(ATE), activeOnly, Set.of(ATE)),
            Map.of(),
            Map.of(
                trainedActive, List.of(new TrainingFact("c1", ATE)),
                trainedInactive, List.of(new TrainingFact("c1", ATE))),
            new Coverage(4, 4, 0, 0));

        TrainingCrossResult result = TrainingCrossCalculator.of(facts, ATE);

        assertThat(result.trainedActive().users()).containsExactly(trainedActive);
        assertThat(result.trainedInactive().users()).containsExactly(trainedInactive);
        assertThat(result.notTrainedActive().users()).containsExactly(activeOnly);
        assertThat(result.notTrainedInactive().users()).containsExactly(neither);
    }
}
