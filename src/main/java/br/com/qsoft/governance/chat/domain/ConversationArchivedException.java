package br.com.qsoft.governance.chat.domain;

/**
 * A conversa está arquivada e não aceita novo envio (ADR-42).
 * Restaurar a conversa antes de enviar resolve o conflito.
 */
public class ConversationArchivedException extends RuntimeException {

    public ConversationArchivedException() {
        super("Conversa arquivada. Restaure-a para enviar novas mensagens.");
    }
}
