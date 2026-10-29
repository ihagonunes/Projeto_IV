package br.com.qsoft.governance.chat.infrastructure.web;

import jakarta.validation.constraints.Size;

/** Corpo do PATCH de conversa (SPEC-07 §3). Campos ausentes permanecem. */
public record UpdateConversationRequest(
    @Size(max = 200, message = "título deve ter no máximo 200 caracteres")
    String titulo,

    String status
) {
}
