package br.com.qsoft.governance.engagement.domain;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.util.HashMap;
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

    public EngagementFacts restrictTo(Set<UserId> users) {
        return new EngagementFacts(users, retainUsers(activeDays, users), retainUsers(licenses, users),
            retainUsers(trainings, users), coverage, licenseSourceAvailable, trainingSourceAvailable);
    }

    private static <V> Map<UserId, V> retainUsers(Map<UserId, V> values, Set<UserId> users) {
        Map<UserId, V> retained = new HashMap<>();
        for (UserId user : users) {
            V value = values.get(user);
            if (value != null) {
                retained.put(user, value);
            }
        }
        return retained;
    }
}
