package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.department.api.command.ConfigureDepartmentModelPolicy;
import br.com.qsoft.governance.department.api.query.OrganizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orquestração da configuração da ModelPolicy de um departamento (SPI-82).
 * <p>
 * ADR-07: a política pertence ao {@code department}, mas a validação dos
 * ModelIds é feita <b>no catálogo do aigateway</b>. Para não criar o ciclo
 * {@code department → aigateway}, é o aigateway que orquestra: valida os IDs no
 * seu catálogo (e a existência do departamento pelo contrato público) e então
 * delega a persistência ao comando público do department.
 */
@Service
public class ConfigureDepartmentPolicy {

    private final ModelRepository modelRepository;
    private final OrganizationQuery organizationQuery;
    private final ConfigureDepartmentModelPolicy configurePolicy;

    public ConfigureDepartmentPolicy(ModelRepository modelRepository,
                                     OrganizationQuery organizationQuery,
                                     ConfigureDepartmentModelPolicy configurePolicy) {
        this.modelRepository = modelRepository;
        this.organizationQuery = organizationQuery;
        this.configurePolicy = configurePolicy;
    }

    public void configure(TenantId tenantId, UUID departmentId,
                          Collection<UUID> allowedModelIds, UUID defaultModelId) {
        organizationQuery.findById(tenantId, departmentId)
            .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        Set<UUID> allowed = allowedModelIds == null
            ? Set.of()
            : new LinkedHashSet<>(allowedModelIds);

        if (!allowed.isEmpty()) {
            List<Model> found = modelRepository.findByTenantIdAndIdIn(tenantId, allowed);
            if (found.size() != allowed.size()) {
                UUID missing = allowed.stream()
                    .filter(id -> found.stream().noneMatch(model -> model.id().equals(id)))
                    .findFirst()
                    .orElseThrow();
                throw new ModelNotFoundException(missing);
            }
        }

        configurePolicy.configure(tenantId, departmentId, allowed, defaultModelId);
    }
}
