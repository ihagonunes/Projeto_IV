package br.com.qsoft.governance.chat.domain;

import java.util.UUID;

/**
 * O {@code clientMessageId} já foi recebido, mas o turno ainda não tem uma
 * resposta ASSISTANT com {@code request_id} (ADR-42/SPEC-07 §3).
 */
public class TurnInProgressOrFailedException extends RuntimeException {

    private final UUID messageId;
    private final UUID requestId;

    public TurnInProgressOrFailedException(UUID messageId, UUID requestId) {
        super("Turno em andamento ou sem resposta concluída.");
        this.messageId = messageId;
        this.requestId = requestId;
    }

    public UUID messageId() {
        return messageId;
    }

    public UUID requestId() {
        return requestId;
    }
}
