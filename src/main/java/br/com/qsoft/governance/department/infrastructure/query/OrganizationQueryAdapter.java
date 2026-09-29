package br.com.qsoft.governance.department.infrastructure.query;

import br.com.qsoft.governance.department.api.model.DepartmentInfo;
import br.com.qsoft.governance.department.api.query.OrganizationQuery;
import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter que satisfaz o contrato público {@link OrganizationQuery} delegando à
 * porta de persistência do módulo (o domínio não vaza para outros módulos).
 */
@Component
public class OrganizationQueryAdapter implements OrganizationQuery {

    private final DepartmentRepository departmentRepository;

    public OrganizationQueryAdapter(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    public Optional<DepartmentInfo> findById(TenantId tenantId, UUID departmentId) {
        return departmentRepository.findByTenantIdAndId(tenantId, departmentId)
            .map(department -> new DepartmentInfo(
                department.id(),
                department.tenantId().value(),
                department.name(),
                department.leaderUserId()
            ));
    }
}
