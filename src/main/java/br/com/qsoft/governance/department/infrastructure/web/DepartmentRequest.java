package br.com.qsoft.governance.department.infrastructure.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload de criação/edição de departamento.
 * O tenant NÃO vem no corpo — é derivado do contexto autenticado (ADR-03).
 */
public record DepartmentRequest(
    @NotBlank(message = "name é obrigatório")
    @Size(max = 150, message = "name deve ter no máximo 150 caracteres")
    String name,

    @Size(max = 500, message = "description deve ter no máximo 500 caracteres")
    String description,

    UUID leaderUserId,

    @DecimalMin(value = "0.00", message = "monthlyBudget não pode ser negativo")
    BigDecimal monthlyBudget
) {
}
