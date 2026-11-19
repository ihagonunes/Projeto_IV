package br.com.qsoft.governance.integration;

import br.com.qsoft.governance.aigateway.application.ListModels;
import br.com.qsoft.governance.department.application.ManageSpending;
import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.SpendingPlan;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.support.TestTenants;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Configura orçamento pelo caso de uso real para os ITs de chat e providers. */
@Component
public class FinancialTestBudgets {
    private final TestTenants tenants;
    private final DepartmentRepository departments;
    private final ListModels models;
    private final ManageSpending spending;
    public FinancialTestBudgets(TestTenants tenants, DepartmentRepository departments,
                                ListModels models, ManageSpending spending) {
        this.tenants = tenants;
        this.departments = departments;
        this.models = models;
        this.spending = spending;
    }
    public void configure(Tenant tenant, String adminEmail, String password) {
        var department = departments.findByTenantId(tenant.id()).get(0);
        var executive = tenants.ensureMember(tenant, tenants.admin(tenant, adminEmail).id(),
            "budget-it@" + tenant.slug() + ".com", "Executivo do orçamento de teste", password,
            HierarchyLevel.EXECUTIVE, department.id(), "EXECUTIVE");
        var rates = models.list(tenant.id()).stream().map(model -> new SpendingPlan.ModelRate(
            model.id(), "BRL", BigDecimal.ONE, "Preços sintéticos dos testes em BRL")).toList();
        var draft = new SpendingPlan(new BigDecimal("10000"),
            List.of(new SpendingPlan.DepartmentShare(department.id(), new BigDecimal("100"))), List.of(),
            Map.of("COLLABORATOR", BigDecimal.ONE, "LEADER", BigDecimal.ONE,
                "MANAGER", BigDecimal.ONE, "EXECUTIVE", BigDecimal.ONE), rates);
        var previous = br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder.get();
        br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder.set(
            new br.com.qsoft.governance.sharedkernel.tenant.TenantContext(tenant.id(), executive.id()));
        try {
            var proposal = spending.suggest(tenant.id(), executive.id(), draft);
            spending.save(tenant.id(), executive.id(), YearMonth.now(ZoneId.of("America/Sao_Paulo")), proposal);
        } finally {
            if (previous == null) br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder.clear();
            else br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder.set(previous);
        }
    }
}
