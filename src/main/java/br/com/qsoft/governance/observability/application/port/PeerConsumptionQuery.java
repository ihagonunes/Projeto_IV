package br.com.qsoft.governance.observability.application.port;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collection;
import java.util.UUID;

public interface PeerConsumptionQuery {
    record Average(BigDecimal averageTokens, BigDecimal averageRequests) { }

    Average operational(TenantId tenant, UUID department, Collection<UUID> peers, YearMonth month);
    Average historical(TenantId tenant, Collection<String> departments, Collection<String> peers, YearMonth month);
}
