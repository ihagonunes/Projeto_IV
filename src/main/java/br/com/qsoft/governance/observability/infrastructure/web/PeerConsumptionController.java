package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.iam.api.rbac.RequiresPermission;
import br.com.qsoft.governance.iam.api.rbac.Targeting;
import br.com.qsoft.governance.observability.application.QueryPeerConsumption;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import br.com.qsoft.governance.observability.application.port.PeerConsumptionQuery.Average;
import java.time.YearMonth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PeerConsumptionController {
    private final QueryPeerConsumption query;

    public PeerConsumptionController(QueryPeerConsumption query) {
        this.query = query;
    }

    @GetMapping("/api/v1/observability/metrics/me/peer-consumption")
    @RequiresPermission(resource = Resource.USAGE, action = Action.READ, targeting = Targeting.BY_TARGET)
    public Average peerConsumption(@RequestParam YearMonth month, @RequestParam QueryPeerConsumption.Source source) {
        var context = TenantContextHolder.require();
        var average = query.average(context.tenantId(), context.userId(), month, source);
        return average == null ? new Average(null, null) : average;
    }
}
