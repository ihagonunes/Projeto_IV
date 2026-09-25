package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dados de entrada da edição de um departamento (substituição dos campos).
 */
public record UpdateDepartmentCommand(
    TenantId tenantId,
    UUID departmentId,
    String name,
    String description,
    UUID leaderUserId,
    BigDecimal monthlyBudget
) {
}
