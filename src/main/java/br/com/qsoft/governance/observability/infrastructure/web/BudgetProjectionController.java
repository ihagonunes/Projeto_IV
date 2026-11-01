package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.observability.application.ProjectDepartmentBudget;
import br.com.qsoft.governance.observability.application.UsageVisibilityDeniedException;
import br.com.qsoft.governance.observability.domain.BudgetProjection;
import br.com.qsoft.governance.sharedkernel.rbac.AccessTarget;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Projeção determinística do orçamento (SPI-131/ADR-41/SPEC-09). */
@RestController
@RequestMapping("/api/v1/observability/orcamentos")
public class BudgetProjectionController {

    private final ProjectDepartmentBudget projectDepartmentBudget;
    private final AuthorizationQuery authorizationQuery;

    public BudgetProjectionController(ProjectDepartmentBudget projectDepartmentBudget,
                                      AuthorizationQuery authorizationQuery) {
        this.projectDepartmentBudget = projectDepartmentBudget;
        this.authorizationQuery = authorizationQuery;
    }

    @GetMapping("/projecao")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<BudgetProjectionResponse> projection(
            @RequestParam UUID departmentId,
            @RequestParam(required = false) String mes,
            @RequestParam(required = false) Integer janelaDias) {
        TenantContext context = TenantContextHolder.require();
        if (!authorizationQuery.isAllowed(context.tenantId(), context.userId(),
            Resource.USAGE, Action.READ, new AccessTarget.DepartmentTarget(departmentId))) {
            throw new UsageVisibilityDeniedException();
        }
        VisibilityScope visibility = authorizationQuery.visibilityFor(
            context.tenantId(), context.userId(), Resource.USAGE, Action.READ);
        int windowDays = janelaDias == null
            ? ProjectDepartmentBudget.DEFAULT_WINDOW_DAYS : janelaDias;
        YearMonth month = mes == null ? YearMonth.now(ZoneOffset.UTC) : parseMonth(mes);
        BudgetProjection projection = projectDepartmentBudget.query(
            context.tenantId(), departmentId, month, windowDays, visibility);
        return ResponseEntity.ok(BudgetProjectionResponse.from(departmentId, projection));
    }

    private static YearMonth parseMonth(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("mes inválido (use AAAA-MM): " + raw + ".");
        }
    }
}
