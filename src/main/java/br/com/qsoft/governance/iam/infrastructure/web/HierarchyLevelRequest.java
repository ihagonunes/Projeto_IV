package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

/**
 * DTO de entrada para alteração do nível hierárquico do usuário.
 */
public record HierarchyLevelRequest(
    @NotNull @JsonAlias({"nivelHierarquico", "hierarchyLevel"}) HierarchyLevel hierarchyLevel
) {
}
