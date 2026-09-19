package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * DTO de entrada para criação/convite de usuário.
 */
public record UserRequest(
    @NotBlank @Email String email,
    @NotBlank @JsonAlias({"nome", "name"}) String name,
    @JsonAlias({"nivelHierarquico", "hierarchyLevel"}) HierarchyLevel hierarchyLevel,
    @JsonAlias({"departamentoId", "departmentId"}) UUID departmentId
) {
}
