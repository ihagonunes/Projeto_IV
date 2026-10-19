package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import java.util.Set;
import java.util.UUID;

/**
 * Filtros combináveis da busca de usuários (SPI-62/SPEC-01 §6).
 * <p>
 * {@code hierarchyLevel}/{@code departmentId}/{@code name}/{@code email} são o
 * filtro pedido pelo cliente. {@code visibleDepartmentIds}/{@code visibleUserId}
 * são o recorte de visibilidade derivado de {@code visibilityFor(USER:READ)}
 * (nunca do cliente): vazio + sem usuário = tenant inteiro.
 */
public record UserSearchFilter(
    HierarchyLevel hierarchyLevel,
    UUID departmentId,
    String name,
    String email,
    Set<UUID> visibleDepartmentIds,
    UUID visibleUserId
) {

    public UserSearchFilter {
        visibleDepartmentIds = visibleDepartmentIds == null ? Set.of() : Set.copyOf(visibleDepartmentIds);
    }
}
