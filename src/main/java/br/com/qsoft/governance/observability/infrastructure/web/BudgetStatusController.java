package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.observability.application.QueryBudgetStatus;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.DateTimeException;
import java.time.YearMonth;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Status do orçamento por departamento (SPEC-02 §4; USAGE:READ). */
@RestController
@RequestMapping("/api/v1/observability/orcamentos")
public class BudgetStatusController {

    private final QueryBudgetStatus queryBudgetStatus;
    private final AuthorizationQuery authorizationQuery;

    public BudgetStatusController(QueryBudgetStatus queryBudgetStatus,
                                  AuthorizationQuery authorizationQuery) {
        this.queryBudgetStatus = queryBudgetStatus;
        this.authorizationQuery = authorizationQuery;
    }

    @GetMapping
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<BudgetStatusResponse> status(@RequestParam String mes) {
        TenantContext context = TenantContextHolder.require();
        VisibilityScope visibility = authorizationQuery.visibilityFor(
            context.tenantId(), context.userId(), Resource.USAGE, Action.READ);
        return ResponseEntity.ok(BudgetStatusResponse.from(
            queryBudgetStatus.query(context.tenantId(), month(mes), visibility)));
    }

    private static YearMonth month(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("mes inválido (use AAAA-MM): " + raw + ".");
        }
    }
}
