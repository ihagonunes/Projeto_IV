package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.api.command.ConfigureDepartmentModelPolicy;
import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.application.port.ModelPolicyRepository;
import br.com.qsoft.governance.department.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação do comando público {@link ConfigureDepartmentModelPolicy}.
 * <p>
 * Garante que o departamento existe <b>no tenant</b> e aplica o invariante do
 * ADR-08 (via {@link ModelPolicy}). A validação de que os ModelIds existem no
 * catálogo é responsabilidade de quem orquestra (aigateway — ADR-07).
 */
@Service
public class ConfigureModelPolicy implements ConfigureDepartmentModelPolicy {

    private final DepartmentRepository departmentRepository;
    private final ModelPolicyRepository modelPolicyRepository;

    public ConfigureModelPolicy(DepartmentRepository departmentRepository,
                                ModelPolicyRepository modelPolicyRepository) {
        this.departmentRepository = departmentRepository;
        this.modelPolicyRepository = modelPolicyRepository;
    }

    @Override
    @Transactional
    public void configure(TenantId tenantId, UUID departmentId,
                          Collection<UUID> allowedModelIds, UUID defaultModelId) {
        TenantId tenant = Objects.requireNonNull(tenantId, "tenantId");
        UUID id = Objects.requireNonNull(departmentId, "departmentId");

        departmentRepository.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new DepartmentNotFoundException(id));

        ModelPolicy policy = modelPolicyRepository.findByTenantIdAndDepartmentId(tenant, id)
            .map(existing -> existing.change(allowedModelIds, defaultModelId))
            .orElseGet(() -> ModelPolicy.create(id, allowedModelIds, defaultModelId));

        modelPolicyRepository.save(policy);
    }
}
