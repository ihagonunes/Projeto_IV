package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de leitura de um departamento do tenant autenticado (ADR-03).
 */
@Service
public class GetDepartment {

    private final DepartmentRepository departmentRepository;

    public GetDepartment(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public Department get(TenantId tenantId, UUID departmentId) {
        TenantId tenant = Objects.requireNonNull(tenantId, "tenantId");
        UUID id = Objects.requireNonNull(departmentId, "departmentId");
        return departmentRepository.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new DepartmentNotFoundException(id));
    }
}
