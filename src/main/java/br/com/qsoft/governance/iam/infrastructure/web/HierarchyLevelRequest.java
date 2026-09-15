package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de alteração do nível hierárquico de um usuário.
 */
public record HierarchyLevelRequest(
    @NotNull(message = "hierarchyLevel é obrigatório")
    HierarchyLevel hierarchyLevel
) {
}
