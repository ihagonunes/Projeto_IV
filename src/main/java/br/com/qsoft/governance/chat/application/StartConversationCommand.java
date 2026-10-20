package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/** Entrada da criação de conversa com a primeira mensagem. */
public record StartConversationCommand(
    TenantId tenantId,
    UserId owner,
    String message,
    UUID requestedModelId
) {
}
