package br.com.qsoft.governance.aigateway.api.completion;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Dados de entrada de uma completion (SPEC-03 §2.1).
 * O tenant/usuário vêm do contexto autenticado. O {@code departmentId} e o
 * {@code requestId} são resolvidos pelo servidor.
 */
public record CompletionCommand(
    TenantId tenantId,
    UserId userId,
    UUID requestedModelId,
    String purpose,
    List<CompletionMessage> messages,
    List<ToolSpec> tools
) {

    public CompletionCommand {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(userId, "userId é obrigatório.");
        Objects.requireNonNull(purpose, "purpose é obrigatório.");
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
