package br.com.qsoft.governance.aigateway.infrastructure.web;

import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import java.util.Set;
import java.util.UUID;

/** Resposta de {@code GET /api/v1/ai-gateway/departments/{id}/model-policy} (SPI-87). */
public record DepartmentModelPolicyResponse(
    UUID departmentId,
    UUID defaultModelId,
    Set<UUID> allowedModelIds
) {

    public static DepartmentModelPolicyResponse from(ModelPolicyInfo policy) {
        return new DepartmentModelPolicyResponse(policy.departmentId(), policy.defaultModelId(),
            policy.allowedModelIds());
    }
}
