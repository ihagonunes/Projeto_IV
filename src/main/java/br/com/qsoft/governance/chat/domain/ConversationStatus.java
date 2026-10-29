package br.com.qsoft.governance.chat.domain;

/** Estado da conversa (V018/V022). DELETED é exclusão lógica e não volta. */
public enum ConversationStatus {
    ACTIVE,
    ARCHIVED,
    DELETED
}
