package br.com.qsoft.governance.chat.application;

import br.com.qsoft.governance.aigateway.api.model.ReceiptInfo;
import br.com.qsoft.governance.chat.domain.ChatMessage;

/** Mensagem da listagem com o recibo (apenas ASSISTANT). */
public record ChatMessageView(ChatMessage message, ReceiptInfo receipt) {
}
