package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter de persistência das tentativas duráveis (ADR-38). */
@Component
public class RequestAttemptRepositoryAdapter implements RequestAttemptRepository {

    private static final String SELECT_DUE = """
        SELECT request_id FROM aigateway_request_attempts
         WHERE tenant_id = :tenantId
           AND delivery_status = 'PENDING'
           AND next_delivery_at <= :now
         ORDER BY next_delivery_at
         FOR UPDATE SKIP LOCKED
         LIMIT :limit
        """;

    private static final String SELECT_STALE = """
        SELECT request_id FROM aigateway_request_attempts
         WHERE tenant_id = :tenantId
           AND execution_status = 'IN_FLIGHT'
           AND created_at < :staleBefore
         ORDER BY created_at
         FOR UPDATE SKIP LOCKED
         LIMIT :limit
        """;

    private final RequestAttemptJpaRepository jpaRepository;
    private final RequestAttemptJpaMapper mapper = new RequestAttemptJpaMapper();

    @PersistenceContext
    private EntityManager entityManager;

    public RequestAttemptRepositoryAdapter(RequestAttemptJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public RequestAttempt save(RequestAttempt attempt) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(attempt)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RequestAttempt> findById(UUID requestId) {
        return jpaRepository.findById(requestId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> tenantsWithWork(Instant now, Instant staleBefore) {
        return entityManager.createNativeQuery("""
                SELECT DISTINCT tenant_id FROM aigateway_request_attempts
                 WHERE (delivery_status = 'PENDING' AND next_delivery_at <= :now)
                    OR (execution_status = 'IN_FLIGHT' AND created_at < :staleBefore)
                """, UUID.class)
            .setParameter("now", now)
            .setParameter("staleBefore", staleBefore)
            .getResultList();
    }

    /**
     * Reserva as entregas com {@code FOR UPDATE SKIP LOCKED} e empurra
     * {@code next_delivery_at} para frente (lease), liberando a transação antes
     * de publicar. Assim uma segunda instância não pega as mesmas linhas.
     */
    @Override
    @Transactional
    public List<UUID> claimDueDeliveries(UUID tenantId, Instant now, Instant leaseUntil, int limit) {
        List<UUID> requestIds = entityManager.createNativeQuery(SELECT_DUE, UUID.class)
            .setParameter("tenantId", tenantId)
            .setParameter("now", now)
            .setParameter("limit", limit)
            .getResultList();
        if (requestIds.isEmpty()) {
            return List.of();
        }
        entityManager.createNativeQuery("""
                UPDATE aigateway_request_attempts
                   SET next_delivery_at = :leaseUntil
                 WHERE request_id IN (:requestIds)
                """)
            .setParameter("leaseUntil", leaseUntil)
            .setParameter("requestIds", requestIds)
            .executeUpdate();
        return requestIds;
    }

    @Override
    @Transactional
    public List<UUID> markStaleInFlight(UUID tenantId, Instant staleBefore, Instant now, int limit) {
        List<UUID> requestIds = entityManager.createNativeQuery(SELECT_STALE, UUID.class)
            .setParameter("tenantId", tenantId)
            .setParameter("staleBefore", staleBefore)
            .setParameter("limit", limit)
            .getResultList();
        if (requestIds.isEmpty()) {
            return List.of();
        }
        entityManager.createNativeQuery("""
                UPDATE aigateway_request_attempts
                   SET execution_status = 'OUTCOME_UNKNOWN',
                       delivery_status  = 'NOT_APPLICABLE',
                       cost_status      = 'INCONCLUSIVE',
                       estimated_cost   = NULL,
                       updated_at       = :now
                 WHERE request_id IN (:requestIds)
                """)
            .setParameter("now", now)
            .setParameter("requestIds", requestIds)
            .executeUpdate();
        return requestIds;
    }
}
