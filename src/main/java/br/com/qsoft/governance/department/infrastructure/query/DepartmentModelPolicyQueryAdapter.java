package br.com.qsoft.governance.department.infrastructure.query;

import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import br.com.qsoft.governance.department.api.query.DepartmentModelPolicyQuery;
import br.com.qsoft.governance.department.application.port.ModelPolicyRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter que satisfaz o contrato público {@link DepartmentModelPolicyQuery},
 * consumido pelo aigateway para o roteamento determinístico (ADR-07).
 */
@Component
public class DepartmentModelPolicyQueryAdapter implements DepartmentModelPolicyQuery {

    private final ModelPolicyRepository modelPolicyRepository;

    public DepartmentModelPolicyQueryAdapter(ModelPolicyRepository modelPolicyRepository) {
        this.modelPolicyRepository = modelPolicyRepository;
    }

    @Override
    public Optional<ModelPolicyInfo> findByDepartmentId(TenantId tenantId, UUID departmentId) {
        return modelPolicyRepository.findByTenantIdAndDepartmentId(tenantId, departmentId)
            .map(policy -> new ModelPolicyInfo(
                policy.departmentId(),
                policy.allowedModelIds(),
                policy.defaultModelId()
            ));
    }
}
