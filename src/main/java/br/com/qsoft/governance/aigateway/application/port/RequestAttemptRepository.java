package br.com.qsoft.governance.aigateway.application.port;

import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistência das tentativas duráveis de IA (ADR-38). */
public interface RequestAttemptRepository {

    RequestAttempt save(RequestAttempt attempt);

    Optional<RequestAttempt> findById(UUID requestId);

    /** Tenants com trabalho pendente (entrega vencida ou IN_FLIGHT expirado). */
    List<UUID> tenantsWithWork(Instant now, Instant staleBefore);

    /**
     * Reserva (lease) as entregas vencidas do tenant com
     * {@code FOR UPDATE SKIP LOCKED}; devolve os {@code request_id} reservados.
     */
    List<UUID> claimDueDeliveries(UUID tenantId, Instant now, Instant leaseUntil, int limit);

    /**
     * Converte IN_FLIGHT expirado em OUTCOME_UNKNOWN/INCONCLUSIVE, com
     * {@code FOR UPDATE SKIP LOCKED}; devolve os {@code request_id} afetados.
     */
    List<UUID> markStaleInFlight(UUID tenantId, Instant staleBefore, Instant now, int limit);

    /** Consulta paginada com escopo de visibilidade já validado pelo caso de uso (RF047). */
    PageResult<RequestAttempt> search(AttemptFilter filter, int page, int size);

    /** Filtros da RF047; {@code visibility} recorta por usuário/departamento. */
    record AttemptFilter(
        TenantId tenantId,
        VisibilityScope visibility,
        Instant from,
        Instant to,
        ExecutionStatus status,
        DeliveryStatus deliveryStatus,
        UUID userId,
        UUID departmentId
    ) {
        public AttemptFilter {
            Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
            Objects.requireNonNull(visibility, "visibility é obrigatória.");
        }
    }
}
