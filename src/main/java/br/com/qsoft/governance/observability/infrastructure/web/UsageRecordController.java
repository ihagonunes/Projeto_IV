package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.observability.application.QueryUsage;
import br.com.qsoft.governance.observability.application.QueryUsageCommand;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta operacional de uso (SPI-73). O tenant vem SEMPRE do contexto
 * autenticado (ADR-03); requer {@code USAGE:READ} (ADR-34).
 */
@RestController
@RequestMapping("/api/v1/observability")
public class UsageRecordController {

    private final QueryUsage queryUsage;

    public UsageRecordController(QueryUsage queryUsage) {
        this.queryUsage = queryUsage;
    }

    @GetMapping("/usage")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ)
    public ResponseEntity<UsagePageResponse> usage(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(UsagePageResponse.from(queryUsage.query(new QueryUsageCommand(
            context.tenantId(), departmentId, userId, from, to, page, size))));
    }
}
