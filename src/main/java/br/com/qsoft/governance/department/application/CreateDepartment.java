package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.exception.DepartmentNameAlreadyExistsException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de criação de departamento.
 * <p>
 * A AUTORIZAÇÃO (DEPARTMENT:MANAGE) é aplicada no endpoint via
 * {@code @RequiresPermission}. O tenant vem SEMPRE do contexto autenticado
 * (ADR-03) — nunca do cliente. O nome é único dentro do tenant.
 */
@Service
public class CreateDepartment {

    private final DepartmentRepository departmentRepository;

    public CreateDepartment(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public Department create(CreateDepartmentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        String name = requireName(command.name());

        if (departmentRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new DepartmentNameAlreadyExistsException(name);
        }

        Department department = Department.create(UUID.randomUUID(), tenantId, name,
            command.description(), command.leaderUserId(), command.monthlyBudget());
        return departmentRepository.save(department);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        return name.trim();
    }
}
