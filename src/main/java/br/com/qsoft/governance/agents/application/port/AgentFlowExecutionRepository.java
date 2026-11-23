package br.com.qsoft.governance.agents.application.port;

import br.com.qsoft.governance.agents.domain.ExecutionStep;
import br.com.qsoft.governance.agents.domain.FlowExecution;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência da execução e das etapas (V030), sempre tenant-scoped. */
public interface AgentFlowExecutionRepository {

    /** Insere a execução; `false` quando a Idempotency-Key já existe. */
    boolean insertIfAbsent(FlowExecution execution);

    Optional<FlowExecution> findByIdempotencyKey(TenantId tenantId, UserId ownerUserId,
                                                 String idempotencyKey);

    Optional<FlowExecution> findById(TenantId tenantId, UUID executionId);

    void insertSteps(List<ExecutionStep> steps);

    void updateStep(ExecutionStep step);

    void finish(FlowExecution execution);

    List<ExecutionStep> findSteps(TenantId tenantId, UUID executionId);
}
