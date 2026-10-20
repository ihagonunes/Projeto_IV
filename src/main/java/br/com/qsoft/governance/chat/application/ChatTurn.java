package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;

/** Resultado de um turno do chat: conversa, mensagens e recibo da completion. */
public record ChatTurn(
    Conversation conversation,
    ChatMessage userMessage,
    ChatMessage assistantMessage,
    CompletionResult completion
) {
}
