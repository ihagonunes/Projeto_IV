package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.domain.FlowGraph;
import br.com.qsoft.governance.agents.domain.FlowGraphValidator;
import br.com.qsoft.governance.agents.domain.FlowValidation;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Validação server-side do grafo (ADR-44 §8): parse estrutural + catálogo
 * efetivo (ACTIVE, com override) + regras de DAG do domínio.
 */
@Service
public class ValidateAgentFlowGraph {

    private final AgentFlowGraphJson graphJson;
    private final QueryAgentCatalog queryAgentCatalog;
    private final AgentFlowRepository flowRepository;
    private final AuthorizationQuery authorizationQuery;

    public ValidateAgentFlowGraph(AgentFlowGraphJson graphJson, QueryAgentCatalog queryAgentCatalog,
                                  AgentFlowRepository flowRepository,
                                  AuthorizationQuery authorizationQuery) {
        this.graphJson = graphJson;
        this.queryAgentCatalog = queryAgentCatalog;
        this.flowRepository = flowRepository;
        this.authorizationQuery = authorizationQuery;
    }

    /** Validação sem persistir, sem exigir um fluxo existente. */
    public FlowValidation validate(TenantId tenantId, int schemaVersion, String rawGraphJson) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        FlowGraph graph = graphJson.parse(rawGraphJson);
        Set<String> activeAgentKeys = queryAgentCatalog.query(tenantId).stream()
            .filter(AgentCatalogEntry::executable)
            .map(AgentCatalogEntry::agentKey)
            .collect(Collectors.toSet());
        return FlowGraphValidator.validate(graph, schemaVersion, activeAgentKeys);
    }

    /** Validação para o endpoint, exigindo fluxo visível com `AGENT:MANAGE`. */
    public FlowValidation validateForFlow(TenantId tenantId, UserId actorUserId, UUID flowId,
                                          int schemaVersion, String rawGraphJson) {
        Objects.requireNonNull(actorUserId, "actorUserId é obrigatório.");
        Objects.requireNonNull(flowId, "flowId é obrigatório.");
        var flow = flowRepository.findById(tenantId, flowId)
            .orElseThrow(AgentFlowNotFoundException::new);
        if (!AgentFlowAccess.visible(authorizationQuery.visibilityFor(
                tenantId, actorUserId, Resource.AGENT, Action.MANAGE), flow)) {
            throw new AgentFlowNotFoundException();
        }
        return validate(tenantId, schemaVersion, rawGraphJson);
    }
}
