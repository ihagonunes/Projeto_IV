package br.com.qsoft.governance.chat.infrastructure.web;

import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.List;

/** Página de conversas do ator (paginação do ADR-28). */
public record ConversationPageResponse(
    List<ConversationResponse> content,
    long totalElements,
    int page,
    int size,
    int totalPages
) {

    public static ConversationPageResponse from(PageResult<Conversation> result) {
        return new ConversationPageResponse(
            result.content().stream().map(ConversationResponse::from).toList(),
            result.totalElements(),
            result.page(),
            result.size(),
            result.totalPages()
        );
    }
}
