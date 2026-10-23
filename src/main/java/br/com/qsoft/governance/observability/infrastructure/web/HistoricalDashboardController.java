package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.observability.application.QueryHistoricalDashboard;
import br.com.qsoft.governance.observability.application.port.HistoricalDashboardQuery.Dashboard;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/observability/metrics/historical")
public class HistoricalDashboardController {
    private final QueryHistoricalDashboard query;
    public HistoricalDashboardController(QueryHistoricalDashboard query) { this.query = query; }

    @GetMapping("/me")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ, targeting = Targeting.BY_TARGET)
    public Dashboard own(@RequestParam(required = false) LocalDate from,
                         @RequestParam(required = false) LocalDate to,
                         @RequestParam(required = false) String modelId) {
        var context = TenantContextHolder.require();
        return query.own(context.tenantId(), context.userId(), from, to, modelId);
    }

    @GetMapping
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ, targeting = Targeting.BY_TARGET)
    public Dashboard dashboard(@RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to,
                               @RequestParam(required = false) String departmentId,
                               @RequestParam(required = false) String modelId,
                               @RequestParam(required = false) UUID userId) {
        var context = TenantContextHolder.require();
        return query.selected(context.tenantId(), context.userId(), from, to, departmentId, modelId, userId);
    }
}
