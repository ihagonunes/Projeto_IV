package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de remoção <b>lógica</b> de departamento (SPI: decisão aprovada).
 * O registro é preservado com {@code status = INACTIVE} para não deixar
 * referências órfãs em {@code app_user.department_id} (cross-module por ID).
 */
@Service
public class DeleteDepartment {

    private final DepartmentRepository departmentRepository;

    public DeleteDepartment(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public void delete(TenantId tenantId, UUID departmentId) {
        TenantId tenant = Objects.requireNonNull(tenantId, "tenantId");
        UUID id = Objects.requireNonNull(departmentId, "departmentId");

        Department department = departmentRepository.findByTenantIdAndId(tenant, id)
            .orElseThrow(() -> new DepartmentNotFoundException(id));
        departmentRepository.save(department.deactivate());
    }
}
