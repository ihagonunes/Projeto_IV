package br.com.qsoft.governance.agents.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Entrada do usuário para a execução do fluxo. */
public record ExecuteAgentFlowRequest(
    @NotBlank @Size(max = 8_000) String input
) {
}
