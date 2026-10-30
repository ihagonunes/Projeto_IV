package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.YearMonth;
import java.util.UUID;

/** Entrada da consulta de AI Credits. */
public record CreditsCommand(
    TenantId tenantId,
    UserId actor,
    SourceKind source,
    YearMonth month,
    UUID departmentId
) {
}
