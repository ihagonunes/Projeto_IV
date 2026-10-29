package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/** Dados de atualização de uma conversa (título e/ou status). */
public record UpdateConversationCommand(
    TenantId tenantId,
    UserId owner,
    UUID conversationId,
    String title,
    String status
) {
}
