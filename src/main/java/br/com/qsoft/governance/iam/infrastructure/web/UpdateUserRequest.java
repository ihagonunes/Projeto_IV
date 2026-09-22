package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import java.util.UUID;

/**
 * Payload de edição de papel (HierarchyLevel) e Department de um usuário (SPI-63).
 * Campos ausentes/nulos não são alterados.
 */
public record UpdateUserRequest(
    HierarchyLevel hierarchyLevel,
    UUID departmentId
) {
}
