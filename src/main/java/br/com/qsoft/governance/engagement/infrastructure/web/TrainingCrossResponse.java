package br.com.qsoft.governance.engagement.infrastructure.web;

import br.com.qsoft.governance.engagement.application.TrainingCrossReport;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Resposta de `GET /api/v1/engajamento/treinamentos/cruzamento` (SPEC-06 §5.2). */
public record TrainingCrossResponse(
    String sourceKind,
    LocalDate ate,
    Map<String, QuadrantResponse> quadrantes,
    boolean listaNominal,
    CoverageResponse cobertura
) {

    public static TrainingCrossResponse from(TrainingCrossReport report) {
        Map<String, QuadrantResponse> quadrants = new LinkedHashMap<>();
        quadrants.put("TREINADO_ATIVO", QuadrantResponse.from(report.trainedActive()));
        quadrants.put("TREINADO_INATIVO", QuadrantResponse.from(report.trainedInactive()));
        quadrants.put("NAO_TREINADO_ATIVO", QuadrantResponse.from(report.notTrainedActive()));
        quadrants.put("NAO_TREINADO_INATIVO", QuadrantResponse.from(report.notTrainedInactive()));
        return new TrainingCrossResponse(report.sourceKind().name(), report.ate(), quadrants,
            report.nominal(), CoverageResponse.from(report.coverage()));
    }

    public record QuadrantResponse(
        int total,
        List<QuadrantUserResponse> usuarios,
        boolean truncado
    ) {

        public static QuadrantResponse from(TrainingCrossReport.QuadrantReport report) {
            return new QuadrantResponse(report.total(),
                report.users().stream().map(QuadrantUserResponse::from).toList(),
                report.truncated());
        }
    }

    public record QuadrantUserResponse(UUID userId, String nome, UUID departmentId) {

        public static QuadrantUserResponse from(TrainingCrossReport.UserView user) {
            return new QuadrantUserResponse(user.userId().value(), user.name(),
                user.departmentId());
        }
    }
}
