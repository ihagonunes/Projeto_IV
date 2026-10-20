package br.com.qsoft.governance.department.application;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.DepartmentStatus;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de listagem de departamentos do tenant autenticado (ADR-03),
 * restrita pelo {@code visibilityFor(DEPARTMENT:READ)} do ator (ADR-36):
 * {@code TenantWide} → todos; {@code Departments} → só os ids do escopo;
 * {@code SelfOnly}/{@code None} → nenhum.
 */
@Service
public class ListDepartments {

    private final DepartmentRepository departmentRepository;
    private final AuthorizationQuery authorizationQuery;

    public ListDepartments(DepartmentRepository departmentRepository,
                           AuthorizationQuery authorizationQuery) {
        this.departmentRepository = departmentRepository;
        this.authorizationQuery = authorizationQuery;
    }

    public List<Department> list(TenantId tenantId, UserId actor, String search,
                                 DepartmentStatus status) {
        TenantId tenant = Objects.requireNonNull(tenantId, "tenantId");
        UserId user = Objects.requireNonNull(actor, "actor");
        DepartmentStatus effectiveStatus = status == null ? DepartmentStatus.ACTIVE : status;

        VisibilityScope visibility = authorizationQuery.visibilityFor(
            tenant, user, Resource.DEPARTMENT, Action.READ);

        return departmentRepository.findByTenantId(tenant).stream()
            .filter(department -> department.status() == effectiveStatus)
            .filter(department -> matchesSearch(department, search))
            .filter(department -> isVisible(visibility, department.id()))
            .toList();
    }

    private static boolean isVisible(VisibilityScope visibility, UUID departmentId) {
        if (visibility instanceof VisibilityScope.TenantWide) {
            return true;
        }
        if (visibility instanceof VisibilityScope.Departments departments) {
            return departments.ids().contains(departmentId);
        }
        return false;
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
