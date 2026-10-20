package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.application.QueryRequestAttempts;
import br.com.qsoft.governance.aigateway.application.QueryRequestAttemptsCommand;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
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
 * RF047: consulta paginada de tentativas de IA (ADR-38). O tenant vem sempre do
 * contexto autenticado; o recorte fino é aplicado pelo
 * {@code visibilityFor(USAGE:READ)} no caso de uso.
 */
@RestController
@RequestMapping("/api/v1/ai-gateway")
public class AIGatewayAttemptController {

    private final QueryRequestAttempts queryRequestAttempts;

    public AIGatewayAttemptController(QueryRequestAttempts queryRequestAttempts) {
        this.queryRequestAttempts = queryRequestAttempts;
    }

    @GetMapping("/tentativas")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ,
        targeting = Targeting.BY_TARGET)
    public ResponseEntity<RequestAttemptPageResponse> attempts(
            @RequestParam(name = "de", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "ate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(required = false) DeliveryStatus deliveryStatus,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TenantContext context = TenantContextHolder.require();
        return ResponseEntity.ok(RequestAttemptPageResponse.from(
            queryRequestAttempts.query(new QueryRequestAttemptsCommand(
                context.tenantId(), context.userId(), from, to, status, deliveryStatus,
                userId, departmentId, page, size))));
    }
}
