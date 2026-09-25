package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.exception.DepartmentNameAlreadyExistsException;
import br.com.qsoft.governance.department.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de edição de departamento (substituição dos campos cadastrais).
 * <p>
 * A AUTORIZAÇÃO (DEPARTMENT:MANAGE) é aplicada no endpoint; o tenant vem do
 * contexto autenticado (ADR-03). Departamento de outro tenant resulta em
 * {@code DepartmentNotFoundException} (não revela existência cross-tenant).
 */
@Service
public class UpdateDepartment {

    private final DepartmentRepository departmentRepository;

    public UpdateDepartment(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public Department update(UpdateDepartmentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        UUID departmentId = Objects.requireNonNull(command.departmentId(), "departmentId");

        Department department = departmentRepository.findByTenantIdAndId(tenantId, departmentId)
            .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        String name = command.name();
        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            if (!trimmed.equals(department.name())
                && departmentRepository.existsByTenantIdAndNameAndIdNot(tenantId, trimmed, departmentId)) {
                throw new DepartmentNameAlreadyExistsException(trimmed);
            }
            department.rename(trimmed);
        }

        department.changeDescription(command.description());
        department.changeLeader(command.leaderUserId());
        department.changeMonthlyBudget(command.monthlyBudget());
        return departmentRepository.save(department);
    }
}
