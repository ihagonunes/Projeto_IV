package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dados de entrada da criação de um departamento.
 */
public record CreateDepartmentCommand(
    TenantId tenantId,
    String name,
    String description,
    UUID leaderUserId,
    BigDecimal monthlyBudget
) {
}
