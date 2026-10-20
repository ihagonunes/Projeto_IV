package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import br.com.qsoft.governance.aigateway.api.exception.AIAttemptAccessDeniedException;
import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.AuthorizationStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.CostStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.DeliveryStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.ExecutionStatus;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestPurpose;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final String SELECT_ATTEMPT = """
        SELECT request_id, tenant_id, user_id, department_id, purpose, model_id, provider_id,
               authorization_status, denial_reason, execution_status, delivery_status,
               input_tokens, output_tokens, cost_status, estimated_cost,
               unit_price_input, unit_price_output, latency_ms, error_code,
               delivery_attempts, next_delivery_at, created_at, updated_at
          FROM aigateway_request_attempts
        """;

    private final RequestAttemptJpaRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RequestAttemptJpaMapper mapper = new RequestAttemptJpaMapper();

    @PersistenceContext
    private EntityManager entityManager;

    public RequestAttemptRepositoryAdapter(RequestAttemptJpaRepository jpaRepository,
                                           JdbcTemplate jdbcTemplate) {
        this.jpaRepository = jpaRepository;
        this.jdbcTemplate = jdbcTemplate;
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

    @Override
    @Transactional(readOnly = true)
    public PageResult<RequestAttempt> search(AttemptFilter filter, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE tenant_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(filter.tenantId().value());
        appendVisibility(where, params, filter.visibility());
        if (filter.from() != null) {
            where.append(" AND created_at >= ?");
            params.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND created_at <= ?");
            params.add(Timestamp.from(filter.to()));
        }
        if (filter.status() != null) {
            where.append(" AND execution_status = ?");
            params.add(filter.status().name());
        }
        if (filter.deliveryStatus() != null) {
            where.append(" AND delivery_status = ?");
            params.add(filter.deliveryStatus().name());
        }
        if (filter.costStatus() != null) {
            where.append(" AND cost_status = ?");
            params.add(filter.costStatus().name());
        }
        if (filter.userId() != null) {
            where.append(" AND user_id = ?");
            params.add(filter.userId());
        }
        if (filter.departmentId() != null) {
            where.append(" AND department_id = ?");
            params.add(filter.departmentId());
        }

        Long total = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM aigateway_request_attempts" + where, Long.class,
            params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<RequestAttempt> content = jdbcTemplate.query(
            SELECT_ATTEMPT + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            this::mapAttempt, pageParams.toArray());

        return new PageResult<>(content, total == null ? 0 : total, page, size);
    }

    private static void appendVisibility(StringBuilder where, List<Object> params,
                                         VisibilityScope visibility) {
        if (visibility instanceof VisibilityScope.TenantWide) {
            return;
        }
        if (visibility instanceof VisibilityScope.Departments departments) {
            where.append(" AND (department_id IN (")
                .append(String.join(",", Collections.nCopies(departments.ids().size(), "?")))
                .append(") OR user_id = ?)");
            params.addAll(departments.ids());
            params.add(departments.self().value());
            return;
        }
        if (visibility instanceof VisibilityScope.SelfOnly selfOnly) {
            where.append(" AND user_id = ?");
            params.add(selfOnly.self().value());
            return;
        }
        throw new AIAttemptAccessDeniedException();
    }

    private RequestAttempt mapAttempt(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime nextDelivery = rs.getObject("next_delivery_at", OffsetDateTime.class);
        return new RequestAttempt(
            rs.getObject("request_id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("department_id", UUID.class),
            RequestPurpose.valueOf(rs.getString("purpose")),
            rs.getObject("model_id", UUID.class),
            rs.getObject("provider_id", UUID.class),
            AuthorizationStatus.valueOf(rs.getString("authorization_status")),
            rs.getString("denial_reason"),
            ExecutionStatus.valueOf(rs.getString("execution_status")),
            DeliveryStatus.valueOf(rs.getString("delivery_status")),
            rs.getObject("input_tokens", Long.class),
            rs.getObject("output_tokens", Long.class),
            CostStatus.valueOf(rs.getString("cost_status")),
            rs.getBigDecimal("estimated_cost"),
            rs.getBigDecimal("unit_price_input"),
            rs.getBigDecimal("unit_price_output"),
            rs.getObject("latency_ms", Long.class),
            rs.getString("error_code"),
            rs.getInt("delivery_attempts"),
            nextDelivery == null ? null : nextDelivery.toInstant(),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
