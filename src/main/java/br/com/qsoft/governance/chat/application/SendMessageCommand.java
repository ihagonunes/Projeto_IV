package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/** Entrada de uma nova mensagem em conversa existente. */
public record SendMessageCommand(
    TenantId tenantId,
    UserId owner,
    UUID conversationId,
    String message,
    UUID requestedModelId
) {
}
