package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.UUID;

/** Filtros da consulta de UsageRecord (SPI-73). Campos nulos não restringem. */
public record QueryUsageCommand(
    TenantId tenantId,
    UUID departmentId,
    UUID userId,
    Instant from,
    Instant to,
    int page,
    int size
) {
}
