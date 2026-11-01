package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import br.com.qsoft.governance.department.api.query.DepartmentModelPolicyQuery;
import br.com.qsoft.governance.department.api.query.OrganizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Leitura da política de modelos do departamento (SPI-87).
 * <p>
 * O aigateway valida a existência do departamento pelo contrato público
 * {@link OrganizationQuery} e lê a política pelo contrato do department. Sem
 * política persistida, devolve o formato vazio canônico (ADR-08).
 */
@Service
public class ReadDepartmentModelPolicy {

    private final OrganizationQuery organizationQuery;
    private final DepartmentModelPolicyQuery policyQuery;

    public ReadDepartmentModelPolicy(OrganizationQuery organizationQuery,
                                     DepartmentModelPolicyQuery policyQuery) {
        this.organizationQuery = organizationQuery;
        this.policyQuery = policyQuery;
    }

    public ModelPolicyInfo read(TenantId tenantId, UUID departmentId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório.");
        Objects.requireNonNull(departmentId, "departmentId é obrigatório.");
        organizationQuery.findById(tenantId, departmentId)
            .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
        return policyQuery.findByDepartmentId(tenantId, departmentId)
            .orElseGet(() -> new ModelPolicyInfo(departmentId, Set.of(), null));
    }
}
