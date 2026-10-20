package br.com.qsoft.governance.agentpolicies.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Pergunta ao agente (SPEC-05 §4): 1 a 2000 caracteres. */
public record PolicyQuestionRequest(
    @NotBlank(message = "pergunta é obrigatória")
    @Size(max = 2000, message = "pergunta deve ter no máximo 2000 caracteres")
    String pergunta,

    UUID requestedModelId
) {
}
