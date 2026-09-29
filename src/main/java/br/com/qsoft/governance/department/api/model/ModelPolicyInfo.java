package br.com.qsoft.governance.department.api.model;

import java.util.Set;
import java.util.UUID;

/**
 * Visão pública da ModelPolicy de um departamento (contrato do módulo
 * {@code department} — ADR-07).
 */
public record ModelPolicyInfo(
    UUID departmentId,
    Set<UUID> allowedModelIds,
    UUID defaultModelId
) {
}
