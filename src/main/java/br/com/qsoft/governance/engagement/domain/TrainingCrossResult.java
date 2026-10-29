package br.com.qsoft.governance.engagement.domain;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Set;

/** Quadrantes treinado × ativo (ADR-40 §6). */
public record TrainingCrossResult(
    Quadrant trainedActive,
    Quadrant trainedInactive,
    Quadrant notTrainedActive,
    Quadrant notTrainedInactive
) {

    public record Quadrant(Set<UserId> users) {

        public Quadrant {
            users = users == null ? Set.of() : Set.copyOf(users);
        }

        public int total() {
            return users.size();
        }
    }
}
