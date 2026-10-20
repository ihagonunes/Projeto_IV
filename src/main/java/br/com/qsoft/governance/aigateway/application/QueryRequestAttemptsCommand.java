package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.UUID;

/** Consulta paginada de tentativas (RF047); o tenant/ator vêm do contexto. */
public record QueryRequestAttemptsCommand(
    TenantId tenantId,
    UserId actor,
    Instant from,
    Instant to,
    ExecutionStatus status,
    DeliveryStatus deliveryStatus,
    UUID userId,
    UUID departmentId,
    int page,
    int size
) {
}
