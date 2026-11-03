package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.engagement.domain.Coverage;
import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Resultado do cruzamento treinamento × uso, com lista nominal filtrada. */
public record TrainingCrossReport(
    SourceKind sourceKind,
    LocalDate ate,
    QuadrantReport trainedActive,
    QuadrantReport trainedInactive,
    QuadrantReport notTrainedActive,
    QuadrantReport notTrainedInactive,
    boolean nominal,
    Coverage coverage
) {

    public record QuadrantReport(int total, List<UserView> users, boolean truncated) {
    }

    public record UserView(UserId userId, String name, UUID departmentId) {
    }
}
