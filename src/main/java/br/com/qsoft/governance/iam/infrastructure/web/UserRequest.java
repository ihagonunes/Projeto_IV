package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Payload de criação de usuário.
 * <p>
 * O tenant é derivado do contexto autenticado (JWT) — nunca do cliente — e as
 * permissões não são definidas aqui. <b>Não há senha</b>: o caminho legítimo
 * para o usuário obter acesso é o <b>convite</b> (SPI-29/30), que define a senha
 * no aceite. Isso evita que um administrador fixe/transmita senhas por fora.
 */
public record UserRequest(
    @NotBlank(message = "email é obrigatório")
    @Email(message = "email inválido")
    @Size(max = 255, message = "email deve ter no máximo 255 caracteres")
    String email,

    @NotBlank(message = "name é obrigatório")
    @Size(max = 200, message = "name deve ter no máximo 200 caracteres")
    String name,

    @NotNull(message = "hierarchyLevel é obrigatório")
    br.com.qsoft.governance.iam.domain.user.HierarchyLevel hierarchyLevel,

    UUID departmentId
) {
}
