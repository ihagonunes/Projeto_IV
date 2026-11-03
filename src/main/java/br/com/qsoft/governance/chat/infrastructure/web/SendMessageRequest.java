package br.com.qsoft.governance.chat.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Corpo de envio de mensagem (SPEC-04 §3 / SPEC-07 §3). */
public record SendMessageRequest(
    @NotBlank(message = "mensagem é obrigatória")
    @Size(max = 8000, message = "mensagem deve ter no máximo 8000 caracteres")
    String mensagem,

    UUID requestedModelId,

    @Pattern(regexp = "[A-Za-z0-9_-]{8,64}",
        message = "clientMessageId deve ter 8 a 64 caracteres [A-Za-z0-9_-]")
    String clientMessageId
) {
}
