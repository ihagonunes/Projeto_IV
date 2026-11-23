package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.agents.domain.FlowValidation;
import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria um fluxo em rascunho com a primeira versão e o grafo (ADR-44).
 * Dono e tenant vêm do contexto; o corpo nunca os define. O grafo é validado
 * no servidor e persistido como VALID/INVALID.
 */
@Service
public class CreateAgentFlow {

    private final AgentFlowRepository flowRepository;
    private final AgentFlowVersionRepository versionRepository;
    private final AuthorizationQuery authorizationQuery;
    private final IdentityQuery identityQuery;
    private final ValidateAgentFlowGraph validator;

    public CreateAgentFlow(AgentFlowRepository flowRepository,
                           AgentFlowVersionRepository versionRepository,
                           AuthorizationQuery authorizationQuery,
                           IdentityQuery identityQuery,
                           ValidateAgentFlowGraph validator) {
        this.flowRepository = flowRepository;
        this.versionRepository = versionRepository;
        this.authorizationQuery = authorizationQuery;
        this.identityQuery = identityQuery;
        this.validator = validator;
    }

    @Transactional
    public AgentFlowDetail create(CreateAgentFlowCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        var tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        var owner = Objects.requireNonNull(command.ownerUserId(), "ownerUserId");
        String name = requireName(command.name());
        if (command.schemaVersion() < 1) {
            throw new IllegalArgumentException("schemaVersion deve ser maior que zero.");
        }

        VisibilityScope scope = authorizationQuery.visibilityFor(
            tenantId, owner, Resource.AGENT, Action.MANAGE);
        if (scope instanceof VisibilityScope.None) {
            throw new AgentFlowAccessDeniedException();
        }
        UUID departmentId = command.departmentId() != null
            ? command.departmentId()
            : departmentOf(owner);
        if (departmentId != null && !scope.coversDepartment(departmentId)) {
            throw new AgentFlowAccessDeniedException();
        }

        FlowValidation validation = validator.validate(
            tenantId, command.schemaVersion(), command.graphJson());
        AgentValidationStatus validationStatus = validation.valid()
            ? AgentValidationStatus.VALID
            : AgentValidationStatus.INVALID;

        Instant now = Instant.now();
        AgentFlow flow = flowRepository.save(AgentFlow.create(
            UUID.randomUUID(), tenantId, owner, departmentId, name, now));

        AgentFlowVersion version = versionRepository.save(tenantId, new AgentFlowVersion(
            UUID.randomUUID(), flow.id(), 1, command.schemaVersion(), command.graphJson(),
            validationStatus, owner, now));

        flow = flowRepository.save(withCurrentVersion(flow, version.id(), now));
        return new AgentFlowDetail(flow, version);
    }

    private UUID departmentOf(UserId owner) {
        return identityQuery.getUserContext(owner)
            .map(UserContext::departmentId)
            .orElse(null);
    }

    static AgentFlow withCurrentVersion(AgentFlow flow, UUID versionId, Instant now) {
        return new AgentFlow(flow.id(), flow.tenantId(), flow.ownerUserId(), flow.departmentId(),
            flow.name(), flow.status(), versionId, flow.rowVersion(), flow.createdAt(), now,
            flow.deletedAt());
    }

    static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("name excede 120 caracteres.");
        }
        return normalized;
    }
}
