package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.observability.application.DashboardQueryCommand;
import br.com.qsoft.governance.observability.application.QueryCostMetrics;
import br.com.qsoft.governance.observability.application.QueryUsageMetrics;
import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agregações do dashboard (SPI-88/89) com filtros de período (SPI-90) e
 * departamento (SPI-91).
 * <p>
 * Requer {@code USAGE:READ}; o <b>escopo</b> (próprio / departamento / tenant) é
 * aplicado no backend a partir do {@code AuthorizationScope} do IAM (SPI-94) —
 * o frontend não substitui esse enforcement (ADR-04/ADR-30).
 */
@RestController
@RequestMapping("/api/v1/observability/metrics")
public class DashboardController {

    private final QueryUsageMetrics queryUsageMetrics;
    private final QueryCostMetrics queryCostMetrics;

    public DashboardController(QueryUsageMetrics queryUsageMetrics,
                               QueryCostMetrics queryCostMetrics) {
        this.queryUsageMetrics = queryUsageMetrics;
        this.queryCostMetrics = queryCostMetrics;
    }

    /** Série temporal de requisições + usuários ativos (SPI-88). */
    @GetMapping("/usage")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ)
    public ResponseEntity<UsageMetricsResponse> usage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UsageMetricsRepository.Granularity granularity) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(UsageMetricsResponse.from(queryUsageMetrics.series(
            new DashboardQueryCommand(context.tenantId(), context.userId(),
                departmentId, from, to, granularity))));
    }

    /** Tokens e custo por departamento (SPI-89). */
    @GetMapping("/costs")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ)
    public ResponseEntity<List<DepartmentCostResponse>> costs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID departmentId) {
        TenantContext context = TenantContextHolder.require();
        List<DepartmentCostResponse> costs = queryCostMetrics.byDepartment(
                new DashboardQueryCommand(context.tenantId(), context.userId(),
                    departmentId, from, to, UsageMetricsRepository.Granularity.DAY))
            .stream()
            .map(DepartmentCostResponse::from)
            .toList();
        return ResponseEntity.ok(costs);
    }
}
