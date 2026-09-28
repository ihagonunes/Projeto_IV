package br.com.qsoft.governance.observability.application.port;

import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.time.Instant;
import java.util.UUID;

/**
 * Porta de saída do módulo {@code observability} para persistência/consulta de
 * UsageRecord. O {@code tenantId} é sempre explícito (ADR-03).
 */
public interface UsageRecordRepository {

    UsageRecord save(UsageRecord record);

    boolean existsByRequestId(UUID requestId);

    PageResult<UsageRecord> search(UsageRecordFilter filter, int page, int size);

    /** Filtros combináveis da consulta operacional (SPI-73). */
    record UsageRecordFilter(
        TenantId tenantId,
        UUID departmentId,
        UUID userId,
        Instant from,
        Instant to
    ) {
    }
}
