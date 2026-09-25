package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.DepartmentStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de listagem de departamentos do tenant autenticado (ADR-03).
 * <p>
 * Por padrão retorna apenas os <b>ativos</b> (remoção é lógica); {@code status}
 * permite consultar os inativados. {@code search} filtra por nome/descrição.
 */
@Service
public class ListDepartments {

    private final DepartmentRepository departmentRepository;

    public ListDepartments(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<Department> list(TenantId tenantId, String search, DepartmentStatus status) {
        TenantId tenant = Objects.requireNonNull(tenantId, "tenantId");
        DepartmentStatus effectiveStatus = status == null ? DepartmentStatus.ACTIVE : status;

        return departmentRepository.findByTenantId(tenant).stream()
            .filter(department -> department.status() == effectiveStatus)
            .filter(department -> matchesSearch(department, search))
            .toList();
    }

    private static boolean matchesSearch(Department department, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        String description = department.description();
        return department.name().toLowerCase(Locale.ROOT).contains(term)
            || (description != null && description.toLowerCase(Locale.ROOT).contains(term));
    }
}
