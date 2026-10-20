package br.com.qsoft.governance.observability.application.port;

import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Porta de saída do módulo {@code observability} para persistência/consulta de
 * UsageRecord. O {@code tenantId} é sempre explícito (ADR-03) e o escopo vem do
 * {@link VisibilityScope} resolvido pelo IAM.
 */
public interface UsageRecordRepository {

    UsageRecord save(UsageRecord record);

    boolean existsByRequestId(UUID requestId);

    PageResult<UsageRecord> search(UsageRecordFilter filter, int page, int size);

    /**
     * Filtros da consulta operacional (SPI-73): visibilidade resolvida + filtros
     * pedidos (já validados contra o escopo) + período.
     */
    record UsageRecordFilter(
        TenantId tenantId,
        VisibilityScope visibility,
        UUID departmentId,
        UUID userId,
        Instant from,
        Instant to
    ) {
        public UsageRecordFilter {
            Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
            Objects.requireNonNull(visibility, "visibility é obrigatória.");
        }
    }
}
