package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.observability.application.port.UsageMetricsRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.UUID;

/** Filtros pedidos pelo cliente (antes da resolução de escopo — SPI-90/91/97/98). */
public record DashboardQueryCommand(
    TenantId tenantId,
    UserId userId,
    UUID departmentId,
    UUID modelId,
    UUID targetUserId,
    Instant from,
    Instant to,
    UsageMetricsRepository.Granularity granularity
) {

    /** Compatibilidade com os consumidores anteriores aos filtros de modelo/usuário. */
    public DashboardQueryCommand(TenantId tenantId, UserId userId, UUID departmentId,
                                 Instant from, Instant to,
                                 UsageMetricsRepository.Granularity granularity) {
        this(tenantId, userId, departmentId, null, null, from, to, granularity);
    }
}
