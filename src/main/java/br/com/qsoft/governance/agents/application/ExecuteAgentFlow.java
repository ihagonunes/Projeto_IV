package br.com.qsoft.governance.agents.application;

import br.com.qsoft.governance.agents.application.port.AgentCompletionGatewayFactory;import br.com.qsoft.governance.agents.application.port.AgentFlowExecutionRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowRepository;
import br.com.qsoft.governance.agents.application.port.AgentFlowVersionRepository;
import br.com.qsoft.governance.agents.domain.AgentFlow;
import br.com.qsoft.governance.agents.domain.AgentFlowExecutor;
import br.com.qsoft.governance.agents.domain.AgentFlowStatus;
import br.com.qsoft.governance.agents.domain.AgentFlowVersion;
import br.com.qsoft.governance.agents.domain.AgentValidationStatus;
import br.com.qsoft.governance.agents.domain.ExecutionLimits;
import br.com.qsoft.governance.agents.domain.ExecutionStatus;
import br.com.qsoft.governance.agents.domain.ExecutionStep;
import br.com.qsoft.governance.agents.domain.ExecutionStepStatus;
import br.com.qsoft.governance.agents.domain.FlowExecution;
import br.com.qsoft.governance.agents.domain.FlowExecutionResult;
import br.com.qsoft.governance.agents.domain.FlowGraph;
import br.com.qsoft.governance.agents.domain.FlowStepResult;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executa a versão publicada de forma síncrona e determinística (ADR-45).
 * Idempotência por `Idempotency-Key`: repetição devolve a mesma execução sem
 * chamar o gateway novamente.
 */
@Service
public class ExecuteAgentFlow {

    private static final int MAX_INPUT = 8_000;
    private static final String IDEMPOTENCY_PATTERN = "[A-Za-z0-9_-]{8,80}";

    private final AgentFlowRepository flowRepository;
    private final AgentFlowVersionRepository versionRepository;
    private final AgentFlowExecutionRepository executionRepository;
    private final AuthorizationQuery authorizationQuery;
    private final ValidateAgentFlowGraph validator;
    private final AgentFlowGraphJson graphJson;
    private final ResolveAgentPrompts promptResolver;
    private final AgentCompletionGatewayFactory gatewayFactory;
    private final AgentExecutionAssembler assembler;
    private final AgentFlowExecutor executor = new AgentFlowExecutor();
    private final ExecutionLimits limits;

    public ExecuteAgentFlow(AgentFlowRepository flowRepository,
                            AgentFlowVersionRepository versionRepository,
                            AgentFlowExecutionRepository executionRepository,
                            AuthorizationQuery authorizationQuery,
                            ValidateAgentFlowGraph validator,
                            AgentFlowGraphJson graphJson,
                            ResolveAgentPrompts promptResolver,
                            AgentCompletionGatewayFactory gatewayFactory,
                            AgentExecutionAssembler assembler,
                            @Value("${agents.execution.max-parallelism:2}") int maxParallelism,
                            @Value("${agents.execution.step-timeout-seconds:30}") long stepTimeout,
                            @Value("${agents.execution.total-timeout-seconds:120}") long totalTimeout) {
        this.flowRepository = flowRepository;
        this.versionRepository = versionRepository;
        this.executionRepository = executionRepository;
        this.authorizationQuery = authorizationQuery;
        this.validator = validator;
        this.graphJson = graphJson;
        this.promptResolver = promptResolver;
        this.gatewayFactory = gatewayFactory;
        this.assembler = assembler;
        this.limits = new ExecutionLimits(maxParallelism, Duration.ofSeconds(stepTimeout),
            Duration.ofSeconds(totalTimeout));
    }

    @Transactional
    public AgentExecutionView execute(TenantId tenantId, UserId actorUserId, UUID flowId,
                                      String idempotencyKey, String input) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(actorUserId, "actorUserId é obrigatório.");
        Objects.requireNonNull(flowId, "flowId é obrigatório.");
        if (input == null || input.isBlank() || input.length() > MAX_INPUT) {
            throw new IllegalArgumentException("input é obrigatório e deve ter no máximo 8000 caracteres.");
        }
        if (idempotencyKey == null || !idempotencyKey.matches(IDEMPOTENCY_PATTERN)) {
            throw new IllegalArgumentException(
                "Idempotency-Key é obrigatória (8-80 caracteres A-Z, a-z, 0-9, _ ou -).");
        }

        AgentFlow flow = flowRepository.findById(tenantId, flowId)
            .orElseThrow(AgentFlowNotFoundException::new);
        if (!AgentFlowAccess.visible(authorizationQuery.visibilityFor(
                tenantId, actorUserId, Resource.AGENT, Action.RUN), flow)) {
            throw new AgentFlowNotFoundException();
        }
        if (flow.status() != AgentFlowStatus.PUBLISHED || flow.currentVersionId() == null) {
            throw new AgentFlowStateConflictException();
        }
        AgentFlowVersion version = versionRepository
            .findById(tenantId, flowId, flow.currentVersionId())
            .orElseThrow(AgentFlowNotValidatedException::new);
        if (version.validationStatus() != AgentValidationStatus.VALID
            || !validator.validate(tenantId, version.schemaVersion(), version.graphJson()).valid()) {
            throw new AgentFlowNotValidatedException();
        }
        FlowGraph graph = graphJson.parse(version.graphJson());

        Instant now = Instant.now();
        FlowExecution execution = new FlowExecution(UUID.randomUUID(), tenantId, flow.id(),
            version.id(), actorUserId, ExecutionStatus.RUNNING, idempotencyKey, null, null,
            now, null, now);
        if (!executionRepository.insertIfAbsent(execution)) {
            FlowExecution existing = executionRepository
                .findByIdempotencyKey(tenantId, actorUserId, idempotencyKey)
                .orElseThrow(AgentExecutionNotFoundException::new);
            return assembler.view(tenantId, existing);
        }

        List<ExecutionStep> steps = new ArrayList<>();
        for (int index = 0; index < graph.nodes().size(); index++) {
            var node = graph.nodes().get(index);
            steps.add(new ExecutionStep(UUID.randomUUID(), execution.id(), index, node.id(),
                node.kind(), ExecutionStepStatus.PENDING, null, null, null, null));
        }
        executionRepository.insertSteps(steps);

        try {
            Map<String, String> prompts = promptResolver.resolve(tenantId, graph);
            FlowExecutionResult result = executor.execute(graph, input, prompts,
                gatewayFactory.forExecution(tenantId, actorUserId), limits);
            Map<String, FlowStepResult> byNode = result.steps().stream()
                .collect(Collectors.toMap(FlowStepResult::nodeId, Function.identity(),
                    (left, right) -> left));
            for (ExecutionStep step : steps) {
                FlowStepResult stepResult = byNode.get(step.nodeId());
                if (stepResult == null) {
                    continue;
                }
                executionRepository.updateStep(new ExecutionStep(step.id(), step.executionId(),
                    step.position(), step.nodeId(), step.nodeKind(), stepResult.status(),
                    stepResult.requestId(), stepResult.errorCode(), stepResult.startedAt(),
                    stepResult.finishedAt()));
            }
            FlowExecution stored = new FlowExecution(execution.id(), tenantId, flow.id(),
                version.id(), actorUserId, result.status(), idempotencyKey, null,
                result.errorCode(), now, Instant.now(), execution.createdAt());
            executionRepository.finish(stored);
            // Decisão 4-B: o conteúdo final não é persistido; só a resposta síncrona o carrega.
            FlowExecution response = new FlowExecution(stored.id(), tenantId, flow.id(),
                version.id(), actorUserId, stored.status(), idempotencyKey, result.result(),
                stored.errorCode(), stored.startedAt(), stored.finishedAt(), stored.createdAt());
            return assembler.view(tenantId, response);
        } catch (RuntimeException ex) {
            FlowExecution failed = new FlowExecution(execution.id(), tenantId, flow.id(),
                version.id(), actorUserId, ExecutionStatus.FAILED, idempotencyKey, null,
                "AGENT_EXECUTION_FAILED", now, Instant.now(), execution.createdAt());
            executionRepository.finish(failed);
            throw ex;
        }
    }
}
