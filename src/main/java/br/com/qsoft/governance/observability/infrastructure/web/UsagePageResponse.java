package br.com.qsoft.governance.observability.infrastructure.web;

import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.List;

/** Página de UsageRecord (paginação do ADR-28). */
public record UsagePageResponse(
    List<UsageRecordResponse> content,
    long totalElements,
    int page,
    int size,
    int totalPages
) {

    public static UsagePageResponse from(PageResult<UsageRecord> result) {
        return new UsagePageResponse(
            result.content().stream().map(UsageRecordResponse::from).toList(),
            result.totalElements(),
            result.page(),
            result.size(),
            result.totalPages()
        );
    }
}
