package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;

/**
 * DTO de entrada para edição de perfil, departamento e permissões do usuário (SPI-63).
 */
public record UpdateUserProfileRequest(
    @JsonAlias({"departamento", "departmentId"}) UUID departmentId,
    @JsonAlias({"nivelHierarquico", "hierarchyLevel"}) HierarchyLevel hierarchyLevel,
    @JsonAlias({"permissaoAdmin", "adminPermission"}) String adminPermission
) {
    public Boolean isAdmin() {
        if (adminPermission == null || adminPermission.isBlank()) {
            return null;
        }
        return "ADMIN".equalsIgnoreCase(adminPermission.trim());
    }
}
