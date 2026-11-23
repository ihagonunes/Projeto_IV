package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.agents.domain.FlowValidation;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Salva uma nova versão imutável do grafo e avança a versão corrente
 * (ADR-44). Exige `row_version` (If-Match); conflito → 412. Fluxo já publicado
 * não aceita nova versão (409): a versão publicada é imutável. O grafo é
 * revalidado no servidor e persistido como VALID/INVALID.
 */
@Service
public class SaveAgentFlowVersion {

    private final AgentFlowRepository flowRepository;
    private final AgentFlowVersionRepository versionRepository;
    private final AuthorizationQuery authorizationQuery;
    private final ValidateAgentFlowGraph validator;

    public SaveAgentFlowVersion(AgentFlowRepository flowRepository,
                                AgentFlowVersionRepository versionRepository,
                                AuthorizationQuery authorizationQuery,
                                ValidateAgentFlowGraph validator) {
        this.flowRepository = flowRepository;
        this.versionRepository = versionRepository;
        this.authorizationQuery = authorizationQuery;
        this.validator = validator;
    }

    @Transactional
    public AgentFlowDetail save(SaveAgentFlowVersionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        var tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        var actor = Objects.requireNonNull(command.actorUserId(), "actorUserId");
        var flowId = Objects.requireNonNull(command.flowId(), "flowId");
        if (command.expectedRowVersion() == null) {
            throw new IllegalArgumentException(
                "If-Match (row_version) é obrigatório para salvar uma nova versão.");
        }
        if (command.schemaVersion() < 1) {
            throw new IllegalArgumentException("schemaVersion deve ser maior que zero.");
        }
        String name = command.name() == null ? null : CreateAgentFlow.requireName(command.name());

        AgentFlow flow = flowRepository.findById(tenantId, flowId)
            .orElseThrow(AgentFlowNotFoundException::new);
        if (!AgentFlowAccess.visible(authorizationQuery.visibilityFor(
                tenantId, actor, Resource.AGENT, Action.MANAGE), flow)) {
            throw new AgentFlowNotFoundException();
        }
        long expected = command.expectedRowVersion();
        if (flow.rowVersion() != expected) {
            throw new AgentFlowPreconditionException();
        }
        if (flow.status() == AgentFlowStatus.PUBLISHED) {
            throw new AgentFlowStateConflictException();
        }

        FlowValidation validation = validator.validate(
            tenantId, command.schemaVersion(), command.graphJson());
        AgentValidationStatus validationStatus = validation.valid()
            ? AgentValidationStatus.VALID
            : AgentValidationStatus.INVALID;

        Instant now = Instant.now();
        AgentFlowVersion version = new AgentFlowVersion(UUID.randomUUID(), flowId,
            versionRepository.nextVersionNumber(tenantId, flowId), command.schemaVersion(),
            command.graphJson(), validationStatus, actor, now);
        try {
            versionRepository.save(tenantId, version);
        } catch (DuplicateKeyException ex) {
            throw new AgentFlowPreconditionException();
        }

        AgentFlow updated = new AgentFlow(flow.id(), flow.tenantId(), flow.ownerUserId(),
            flow.departmentId(), name == null ? flow.name() : name, flow.status(), version.id(),
            flow.rowVersion() + 1, flow.createdAt(), now, flow.deletedAt());
        if (!flowRepository.updateIfRowVersion(updated, expected)) {
            throw new AgentFlowPreconditionException();
        }
        return new AgentFlowDetail(updated, version);
    }
}
