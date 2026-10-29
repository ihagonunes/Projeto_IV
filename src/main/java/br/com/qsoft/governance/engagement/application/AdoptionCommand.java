package br.com.qsoft.governance.engagement.application;

import br.com.qsoft.governance.engagement.domain.SourceKind;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.LocalDate;
import java.util.UUID;

/** Entrada da consulta de adoção. */
public record AdoptionCommand(
    TenantId tenantId,
    UserId actor,
    SourceKind source,
    LocalDate ate,
    UUID departmentId
) {
}
