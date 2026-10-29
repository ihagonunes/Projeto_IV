package br.com.qsoft.governance.engagement.domain;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fatos normalizados de engajamento (ADR-40 §2/§3). As duas fontes
 * (operacional e histórica) produzem este mesmo agregado; os cálculos são
 * puros.
 */
public record EngagementFacts(
    Set<UserId> eligible,
    Map<UserId, Set<LocalDate>> activeDays,
    Map<UserId, List<LicenseFact>> licenses,
    Map<UserId, List<TrainingFact>> trainings,
    Coverage coverage,
    boolean licenseSourceAvailable,
    boolean trainingSourceAvailable
) {

    public EngagementFacts {
        eligible = eligible == null ? Set.of() : Set.copyOf(eligible);
        activeDays = activeDays == null ? Map.of() : Map.copyOf(activeDays);
        licenses = licenses == null ? Map.of() : Map.copyOf(licenses);
        trainings = trainings == null ? Map.of() : Map.copyOf(trainings);
    }

    /** Fontes disponíveis por padrão (adaptadores operacionais e testes). */
    public EngagementFacts(Set<UserId> eligible, Map<UserId, Set<LocalDate>> activeDays,
                           Map<UserId, List<LicenseFact>> licenses,
                           Map<UserId, List<TrainingFact>> trainings, Coverage coverage) {
        this(eligible, activeDays, licenses, trainings, coverage, true, true);
    }
}
