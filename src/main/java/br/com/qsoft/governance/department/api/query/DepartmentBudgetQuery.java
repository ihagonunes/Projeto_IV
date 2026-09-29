package br.com.qsoft.governance.department.api.query;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato público do módulo {@code department} para o orçamento mensal.
 * <p>
 * ADR-06: o department é a <b>fonte de verdade</b> do {@code BudgetLimit}
 * mensal; o observability consulta por este contrato para comparar com o custo
 * realizado. Vazio quando o departamento não define teto.
 */
public interface DepartmentBudgetQuery {

    Optional<BigDecimal> monthlyBudgetOf(TenantId tenantId, UUID departmentId);
}
