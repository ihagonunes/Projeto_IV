package br.com.qsoft.governance.engagement.domain;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Cruzamento treinado × ativo (ADR-40 §6). */
public final class TrainingCrossCalculator {

    private TrainingCrossCalculator() {
    }

    public static TrainingCrossResult of(EngagementFacts facts, LocalDate ate) {
        LocalDate from = ate.minusDays(AdoptionCalculator.WINDOW_DAYS - 1L);
        Set<UserId> trained = new LinkedHashSet<>();
        Set<UserId> active = new LinkedHashSet<>();
        for (UserId userId : sorted(facts.eligible())) {
            boolean isTrained = facts.trainings().getOrDefault(userId, List.of()).stream()
                .anyMatch(training -> training.completedAt() != null
                    && !training.completedAt().isAfter(ate));
            if (isTrained) {
                trained.add(userId);
            }
            if (AdoptionCalculator.hasActiveDay(facts, userId, from, ate)) {
                active.add(userId);
            }
        }

        Set<UserId> trainedActive = intersection(trained, active);
        Set<UserId> trainedInactive = difference(trained, active);
        Set<UserId> notTrainedActive = difference(active, trained);
        Set<UserId> notTrainedInactive = difference(facts.eligible(), union(trained, active));

        return new TrainingCrossResult(
            new TrainingCrossResult.Quadrant(trainedActive),
            new TrainingCrossResult.Quadrant(trainedInactive),
            new TrainingCrossResult.Quadrant(notTrainedActive),
            new TrainingCrossResult.Quadrant(notTrainedInactive));
    }

    private static Set<UserId> sorted(Set<UserId> users) {
        return users.stream()
            .sorted((left, right) -> left.value().toString().compareTo(right.value().toString()))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<UserId> intersection(Set<UserId> left, Set<UserId> right) {
        return left.stream().filter(right::contains)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<UserId> difference(Set<UserId> left, Set<UserId> right) {
        return left.stream().filter(user -> !right.contains(user))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<UserId> union(Set<UserId> left, Set<UserId> right) {
        Set<UserId> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return result;
    }
}
