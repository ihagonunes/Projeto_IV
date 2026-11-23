package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica a versão corrente validada (ADR-44). Exige `row_version`
 * (If-Match); conflito → 412. A versão publicada é imutável: publicar não
 * altera `agents_flow_versions`, apenas fixa o fluxo como PUBLISHED e pina a
 * versão corrente. O grafo é revalidado contra o catálogo efetivo atual.
 */
@Service
public class PublishAgentFlow {

    private final AgentFlowRepository flowRepository;
    private final AgentFlowVersionRepository versionRepository;
    private final AuthorizationQuery authorizationQuery;
    private final ValidateAgentFlowGraph validator;

    public PublishAgentFlow(AgentFlowRepository flowRepository,
                            AgentFlowVersionRepository versionRepository,
                            AuthorizationQuery authorizationQuery,
                            ValidateAgentFlowGraph validator) {
        this.flowRepository = flowRepository;
        this.versionRepository = versionRepository;
        this.authorizationQuery = authorizationQuery;
        this.validator = validator;
    }

    @Transactional
    public AgentFlowDetail publish(TenantId tenantId, UserId actorUserId, UUID flowId,
                                   Long expectedRowVersion) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actorUserId, "actorUserId é obrigatório.");
        Objects.requireNonNull(flowId, "flowId é obrigatório.");
        if (expectedRowVersion == null) {
            throw new IllegalArgumentException(
                "If-Match (row_version) é obrigatório para publicar.");
        }

        AgentFlow flow = flowRepository.findById(tenantId, flowId)
            .orElseThrow(AgentFlowNotFoundException::new);
        if (!AgentFlowAccess.visible(authorizationQuery.visibilityFor(
                tenantId, actorUserId, Resource.AGENT, Action.MANAGE), flow)) {
            throw new AgentFlowNotFoundException();
        }
        if (flow.rowVersion() != expectedRowVersion) {
            throw new AgentFlowPreconditionException();
        }
        if (flow.status() != AgentFlowStatus.DRAFT) {
            throw new AgentFlowStateConflictException();
        }
        if (flow.currentVersionId() == null) {
            throw new AgentFlowNotValidatedException();
        }

        AgentFlowVersion version = versionRepository
            .findById(tenantId, flowId, flow.currentVersionId())
            .orElseThrow(AgentFlowNotValidatedException::new);
        if (version.validationStatus() != AgentValidationStatus.VALID) {
            throw new AgentFlowNotValidatedException();
        }
        if (!validator.validate(tenantId, version.schemaVersion(), version.graphJson()).valid()) {
            throw new AgentFlowNotValidatedException();
        }

        Instant now = Instant.now();
        AgentFlow published = new AgentFlow(flow.id(), flow.tenantId(), flow.ownerUserId(),
            flow.departmentId(), flow.name(), AgentFlowStatus.PUBLISHED, version.id(),
            flow.rowVersion() + 1, flow.createdAt(), now, flow.deletedAt());
        if (!flowRepository.updateIfRowVersion(published, expectedRowVersion)) {
            throw new AgentFlowPreconditionException();
        }
        return new AgentFlowDetail(published, version);
    }
}
