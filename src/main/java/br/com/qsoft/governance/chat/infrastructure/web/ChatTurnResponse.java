package br.com.qsoft.governance.chat.infrastructure.web;

import br.com.qsoft.governance.chat.application.ChatTurn;
import java.util.List;

/** Resposta de criação de conversa/envio: conversa, mensagens do turno e recibo. */
public record ChatTurnResponse(
    ConversationResponse conversa,
    List<MessageResponse> mensagens,
    ReceiptResponse recibo
) {

    public static ChatTurnResponse from(ChatTurn turn) {
        return new ChatTurnResponse(
            ConversationResponse.from(turn.conversation()),
            List.of(MessageResponse.from(turn.userMessage()),
                MessageResponse.from(turn.assistantMessage())),
            ReceiptResponse.from(turn.completion())
        );
    }
}
