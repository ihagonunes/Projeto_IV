package br.com.qsoft.governance.department.infrastructure.web;

import br.com.qsoft.governance.department.application.ListMonthlyBudgets;
import br.com.qsoft.governance.department.application.UpsertMonthlyBudget;
import br.com.qsoft.governance.department.domain.MonthlyBudget;
import br.com.qsoft.governance.department.domain.exception.DepartmentAccessDeniedException;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Orçamento mensal por departamento (SPEC-02 §4; SPI-125). */
@RestController
@RequestMapping("/api/v1/departamentos")
public class DepartmentBudgetController {

    private final UpsertMonthlyBudget upsertMonthlyBudget;
    private final ListMonthlyBudgets listMonthlyBudgets;
    private final AuthorizationQuery authorizationQuery;

    public DepartmentBudgetController(UpsertMonthlyBudget upsertMonthlyBudget,
                                      ListMonthlyBudgets listMonthlyBudgets,
                                      AuthorizationQuery authorizationQuery) {
        this.upsertMonthlyBudget = upsertMonthlyBudget;
        this.listMonthlyBudgets = listMonthlyBudgets;
        this.authorizationQuery = authorizationQuery;
    }

    /** Upsert do orçamento do mês. Requer DEPARTMENT_BUDGET:MANAGE sobre o departamento. */
    @PutMapping("/{id}/orcamentos/{mes}")
    @RequiresPermission(resource = Resource.DEPARTMENT_BUDGET, action = Action.MANAGE,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<MonthlyBudgetResponse> put(@PathVariable UUID id,
                                                     @PathVariable String mes,
                                                     @RequestBody MonthlyBudgetRequest request) {
        TenantContext context = TenantContextHolder.require();
        boolean allowed = authorizationQuery.isAllowed(context.tenantId(), context.userId(),
            Resource.DEPARTMENT_BUDGET, Action.MANAGE, new AccessTarget.DepartmentTarget(id));
        if (!allowed) {
            throw new DepartmentAccessDeniedException();
        }
        if (request.amount() == null) {
            throw new IllegalArgumentException("amount é obrigatório.");
        }
        int threshold = request.alertThresholdPct() == null ? 80 : request.alertThresholdPct();

        MonthlyBudget budget = upsertMonthlyBudget.upsert(new UpsertMonthlyBudget.Command(
            context.tenantId(), id, month(mes), request.amount(), threshold, context.userId()));
        return ResponseEntity.ok(MonthlyBudgetResponse.from(budget));
    }

    /** Histórico do orçamento (máximo 24 meses). Requer DEPARTMENT:READ sobre o departamento. */
    @GetMapping("/{id}/orcamentos")
    @RequiresPermission(resource = Resource.DEPARTMENT, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<List<MonthlyBudgetResponse>> history(@PathVariable UUID id,
                                                               @RequestParam String de,
                                                               @RequestParam String ate) {
        TenantContext context = TenantContextHolder.require();
        boolean allowed = authorizationQuery.isAllowed(context.tenantId(), context.userId(),
            Resource.DEPARTMENT, Action.READ, new AccessTarget.DepartmentTarget(id));
        if (!allowed) {
            throw new DepartmentAccessDeniedException();
        }
        List<MonthlyBudgetResponse> body = listMonthlyBudgets.list(context.tenantId(), id,
                month(de), month(ate)).stream()
            .map(MonthlyBudgetResponse::from)
            .toList();
        return ResponseEntity.ok(body);
    }

    private static YearMonth month(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("mes inválido (use AAAA-MM): " + raw + ".");
        }
    }
}
