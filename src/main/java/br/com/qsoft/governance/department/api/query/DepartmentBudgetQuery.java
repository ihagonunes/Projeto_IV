package br.com.qsoft.governance.department.api.query;

import br.com.qsoft.governance.department.api.model.BudgetLimitInfo;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato público do módulo {@code department} para o orçamento mensal.
 * <p>
 * ADR-06: o department é a <b>fonte de verdade</b> do {@code BudgetLimit}
 * mensal; o observability consulta por este contrato para comparar com o custo
 * realizado.
 */
public interface DepartmentBudgetQuery {

    /**
     * Orçamento padrão ({@code monthly_budget}) do departamento, quando definido.
     * Mantido para os consumidores anteriores à resolução por mês.
     */
    Optional<BigDecimal> monthlyBudgetOf(TenantId tenantId, UUID departmentId);

    /**
     * Orçamento efetivo do mês: linha do mês &gt; padrão (limiar 80) &gt; sem
     * orçamento. Vazio somente quando o departamento não existe no tenant.
     */
    Optional<BudgetLimitInfo> budgetFor(TenantId tenantId, UUID departmentId, YearMonth month);

    /**
     * Orçamento efetivo do mês para todos os departamentos <b>ativos</b> do
     * tenant. Departamentos sem orçamento entram com {@code amount == null}.
     */
    List<BudgetLimitInfo> budgetsFor(TenantId tenantId, YearMonth month);
}
