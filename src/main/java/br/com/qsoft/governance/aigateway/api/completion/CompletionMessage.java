package br.com.qsoft.governance.aigateway.api.completion;

import java.util.Objects;

/**
 * Mensagem de uma completion. {@code role}: SYSTEM | USER | ASSISTANT | TOOL.
 * {@code toolCallId} só é usado em mensagens TOOL.
 */
public record CompletionMessage(String role, String content, String toolCallId) {

    public CompletionMessage {
        Objects.requireNonNull(role, "role é obrigatório.");
    }

    public static CompletionMessage user(String content) {
        return new CompletionMessage("USER", content, null);
    }

    public static CompletionMessage system(String content) {
        return new CompletionMessage("SYSTEM", content, null);
    }
}
