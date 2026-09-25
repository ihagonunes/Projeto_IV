package br.com.qsoft.governance.department.api.model;

import java.util.UUID;

/**
 * Visão organizacional de um departamento exposta a outros módulos
 * (contrato público do módulo {@code department} — ADR-02/ADR-31).
 */
public record DepartmentInfo(
    UUID id,
    UUID tenantId,
    String name,
    UUID leaderUserId
) {
}
