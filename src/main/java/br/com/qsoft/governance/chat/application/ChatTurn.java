package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.aigateway.api.completion.CompletionResult;
import br.com.qsoft.governance.chat.domain.ChatMessage;
import br.com.qsoft.governance.chat.domain.Conversation;

/** Resultado de um turno do chat: conversa, mensagens e recibo da completion. */
public record ChatTurn(
    Conversation conversation,
    ChatMessage userMessage,
    ChatMessage assistantMessage,
    CompletionResult completion,
    boolean replayed
) {

    public ChatTurn(Conversation conversation, ChatMessage userMessage,
                    ChatMessage assistantMessage, CompletionResult completion) {
        this(conversation, userMessage, assistantMessage, completion, false);
    }
}
