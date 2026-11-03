package br.com.qsoft.governance.chat.infrastructure.web;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import java.time.Instant;
import java.util.UUID;

/** Mensagem exposta pela API do chat. O conteúdo nunca vai para log/evento. */
public record MessageResponse(
    UUID id,
    String role,
    String content,
    UUID requestId,
    Instant createdAt,
    ReceiptResponse recibo
) {

    public static MessageResponse from(ChatMessage message) {
        return from(message, null);
    }

    public static MessageResponse from(ChatMessage message, ReceiptInfo receipt) {
        return new MessageResponse(
            message.id(),
            message.role().name(),
            message.content(),
            message.requestId(),
            message.createdAt(),
            receipt == null ? null : ReceiptResponse.from(receipt)
        );
    }
}
