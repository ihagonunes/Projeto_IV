package br.com.qsoft.governance.iam.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Payload de edição do <b>Department</b> de um usuário (SPI-63).
 * <p>
 * O <b>papel/nível</b> não é editado aqui: o modelo de autorização é por
 * <b>papéis por tenant</b> (ADR-33) e o {@code PolicyEvaluator} não lê o
 * {@code hierarchyLevel} — alterá-lo neste endpoint daria a falsa impressão de
 * mover privilégios. A alteração de nível continua em
 * {@code POST /api/v1/usuarios/{id}/nivel}, como rótulo organizacional.
 */
public record UpdateUserRequest(
    @NotNull(message = "departmentId é obrigatório")
    UUID departmentId
) {
}
