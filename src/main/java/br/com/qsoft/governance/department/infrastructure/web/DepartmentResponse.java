package br.com.qsoft.governance.department.infrastructure.web;

import br.com.qsoft.governance.department.domain.Department;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representação pública de um departamento.
 */
public record DepartmentResponse(
    UUID id,
    UUID tenantId,
    String name,
    String description,
    UUID leaderUserId,
    BigDecimal monthlyBudget,
    String status,
    Instant createdAt,
    Instant updatedAt
) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
            department.id(),
            department.tenantId().value(),
            department.name(),
            department.description(),
            department.leaderUserId(),
            department.monthlyBudget(),
            department.status().name(),
            department.createdAt(),
            department.updatedAt()
        );
    }
}
