package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import java.util.UUID;

/**
 * Filtros combináveis da busca de usuários (SPI-62).
 * Campos nulos não restringem o resultado.
 */
public record UserSearchFilter(
    HierarchyLevel hierarchyLevel,
    UUID departmentId,
    String name,
    String email
) {
}
