package br.com.qsoft.governance.chat.domain;

/**
 * Conversa inexistente para o ator. O mesmo 404 cobre "não existe" e
 * "pertence a outra pessoa" (não revela existência cross-user).
 */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversa não encontrada.");
    }
}
