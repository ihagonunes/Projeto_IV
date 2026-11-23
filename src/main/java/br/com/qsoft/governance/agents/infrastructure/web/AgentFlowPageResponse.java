package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.List;

/** Página de fluxos visíveis (ADR-28: page/size zero-based). */
public record AgentFlowPageResponse(
    List<AgentFlowSummaryResponse> content,
    long totalElements,
    int page,
    int size,
    int totalPages
) {

    static AgentFlowPageResponse from(PageResult<AgentFlow> result) {
        return new AgentFlowPageResponse(
            result.content().stream().map(AgentFlowSummaryResponse::from).toList(),
            result.totalElements(), result.page(), result.size(), result.totalPages());
    }
}
