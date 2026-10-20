package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.List;

/** Página de tentativas (RF047), no envelope de paginação do ADR-28. */
public record RequestAttemptPageResponse(
    List<RequestAttemptResponse> content,
    long totalElements,
    int page,
    int size,
    int totalPages
) {

    public static RequestAttemptPageResponse from(PageResult<RequestAttempt> result) {
        return new RequestAttemptPageResponse(
            result.content().stream().map(RequestAttemptResponse::from).toList(),
            result.totalElements(),
            result.page(),
            result.size(),
            result.totalPages()
        );
    }
}
