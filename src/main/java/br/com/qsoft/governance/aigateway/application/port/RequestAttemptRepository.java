package br.com.qsoft.governance.aigateway.application.port;

import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import java.time.Instant;
import java.util.List;
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
}
