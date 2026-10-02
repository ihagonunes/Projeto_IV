package br.com.qsoft.governance.department.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.department.domain.DepartmentStatus;
import br.com.qsoft.governance.department.domain.exception.DepartmentNameAlreadyExistsException;
import br.com.qsoft.governance.department.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Testes dos casos de uso de departamento. Os fakes em memória respeitam o
 * escopo de tenant (todas as consultas são tenant-scoped).
 */
class DepartmentUseCasesTest {

    private final Map<UUID, Department> departments = new LinkedHashMap<>();

    private final DepartmentRepository departmentRepository = new DepartmentRepository() {
        @Override
        public Department save(Department department) {
            departments.put(department.id(), department);
            return department;
        }

        @Override
        public Optional<Department> findByTenantIdAndId(TenantId tenantId, UUID departmentId) {
            return Optional.ofNullable(departments.get(departmentId))
                .filter(department -> department.tenantId().equals(tenantId));
        }

        @Override
        public List<Department> findByTenantId(TenantId tenantId) {
            return departments.values().stream()
                .filter(department -> department.tenantId().equals(tenantId))
                .toList();
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
            return departments.values().stream()
                .anyMatch(d -> d.tenantId().equals(tenantId) && d.name().equals(name));
        }

        @Override
        public boolean existsByTenantIdAndNameAndIdNot(TenantId tenantId, String name, UUID departmentId) {
            return departments.values().stream()
                .anyMatch(d -> d.tenantId().equals(tenantId)
                    && d.name().equals(name)
                    && !d.id().equals(departmentId));
        }
    };

    private final CreateDepartment createDepartment = new CreateDepartment(departmentRepository);
    private final UpdateDepartment updateDepartment = new UpdateDepartment(departmentRepository);
    private final DeleteDepartment deleteDepartment = new DeleteDepartment(departmentRepository);
    private final ListDepartments listDepartments = new ListDepartments(departmentRepository);

    private Department create(TenantId tenantId, String name) {
        return createDepartment.create(new CreateDepartmentCommand(
            tenantId, name, "desc " + name, null, new BigDecimal("100.00")));
    }

    @Test
    void shouldCreateDepartmentInTenant() {
        TenantId tenantId = TenantId.random();

        Department department = create(tenantId, "Marketing");

        assertThat(department.id()).isNotNull();
        assertThat(department.tenantId()).isEqualTo(tenantId);
        assertThat(department.status()).isEqualTo(DepartmentStatus.ACTIVE);
    }

    @Test
    void shouldRejectDuplicateNameInSameTenant() {
        TenantId tenantId = TenantId.random();
        create(tenantId, "Marketing");

        assertThatThrownBy(() -> create(tenantId, "Marketing"))
            .isInstanceOf(DepartmentNameAlreadyExistsException.class);
    }

    @Test
    void shouldAllowSameNameInDifferentTenants() {
        create(TenantId.random(), "Marketing");

        Department other = create(TenantId.random(), "Marketing");

        assertThat(other.id()).isNotNull();
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> createDepartment.create(new CreateDepartmentCommand(
            TenantId.random(), "  ", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullNameOnCreate() {
        assertThatThrownBy(() -> createDepartment.create(new CreateDepartmentCommand(
            TenantId.random(), null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    void shouldUpdateDepartment() {
        TenantId tenantId = TenantId.random();
        Department department = create(tenantId, "Marketing");
        UUID leader = UUID.randomUUID();

        Department updated = updateDepartment.update(new UpdateDepartmentCommand(
            tenantId, department.id(), "Vendas", "Time de vendas", leader, new BigDecimal("999.00")));

        assertThat(updated.name()).isEqualTo("Vendas");
        assertThat(updated.description()).isEqualTo("Time de vendas");
        assertThat(updated.leaderUserId()).isEqualTo(leader);
        assertThat(updated.monthlyBudget()).isEqualByComparingTo("999.00");
    }

    @Test
    void shouldRejectRenameToExistingNameInSameTenant() {
        TenantId tenantId = TenantId.random();
        create(tenantId, "Marketing");
        Department vendas = create(tenantId, "Vendas");

        assertThatThrownBy(() -> updateDepartment.update(new UpdateDepartmentCommand(
            tenantId, vendas.id(), "Marketing", null, null, null)))
            .isInstanceOf(DepartmentNameAlreadyExistsException.class);
    }

    @Test
    void shouldAllowKeepingOwnNameOnUpdate() {
        TenantId tenantId = TenantId.random();
        Department department = create(tenantId, "Marketing");

        Department updated = updateDepartment.update(new UpdateDepartmentCommand(
            tenantId, department.id(), "Marketing", "novo", null, null));

        assertThat(updated.description()).isEqualTo("novo");
    }

    @Test
    void shouldRejectUpdateOfDepartmentFromAnotherTenant() {
        Department department = create(TenantId.random(), "Marketing");

        assertThatThrownBy(() -> updateDepartment.update(new UpdateDepartmentCommand(
            TenantId.random(), department.id(), "Vendas", null, null, null)))
            .isInstanceOf(DepartmentNotFoundException.class);
    }

    @Test
    void shouldDeleteLogically() {
        TenantId tenantId = TenantId.random();
        Department department = create(tenantId, "Marketing");

        deleteDepartment.delete(tenantId, department.id());

        assertThat(departments.get(department.id()).status()).isEqualTo(DepartmentStatus.INACTIVE);
        assertThat(listDepartments.list(tenantId, null, null)).isEmpty();
        assertThat(listDepartments.list(tenantId, null, DepartmentStatus.INACTIVE))
            .extracting(Department::id).containsExactly(department.id());
    }

    @Test
    void shouldRejectDeleteOfDepartmentFromAnotherTenant() {
        Department department = create(TenantId.random(), "Marketing");

        assertThatThrownBy(() -> deleteDepartment.delete(TenantId.random(), department.id()))
            .isInstanceOf(DepartmentNotFoundException.class);
    }

    @Test
    void shouldListActiveDepartmentsFilteredBySearch() {
        TenantId tenantId = TenantId.random();
        create(tenantId, "Marketing");
        create(tenantId, "Vendas");

        List<Department> result = listDepartments.list(tenantId, "vend", null);

        assertThat(result).extracting(Department::name).containsExactly("Vendas");
    }

    @Test
    void shouldListOnlyDepartmentsOfTenant() {
        TenantId tenantId = TenantId.random();
        create(tenantId, "Marketing");
        create(TenantId.random(), "Outro");

        assertThat(listDepartments.list(tenantId, null, null))
            .extracting(Department::name).containsExactly("Marketing");
    }

    @Test
    void shouldUpdateDescriptionOnly_quando_nomeNuloNoUpdate() {
        TenantId tenantId = TenantId.random();
        Department department = create(tenantId, "Marketing");

        Department updated = updateDepartment.update(new UpdateDepartmentCommand(
            tenantId, department.id(), null, "Nova descrição", null, null));

        assertThat(updated.name()).isEqualTo("Marketing");
        assertThat(updated.description()).isEqualTo("Nova descrição");
    }

    @Test
    void shouldUpdateDescriptionOnly_quando_nomeEmBrancoNoUpdate() {
        TenantId tenantId = TenantId.random();
        Department department = create(tenantId, "Vendas");

        Department updated = updateDepartment.update(new UpdateDepartmentCommand(
            tenantId, department.id(), "   ", "desc atualizada", null, null));

        assertThat(updated.name()).isEqualTo("Vendas");
        assertThat(updated.description()).isEqualTo("desc atualizada");
    }

    @Test
    void shouldRejectNullCommandOnUpdate() {
        assertThatThrownBy(() -> updateDepartment.update(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    @Test
    void shouldRejectNullCommandOnCreate() {
        assertThatThrownBy(() -> createDepartment.create(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    @Test
    void shouldGetDepartmentById() {
        TenantId tenantId = TenantId.random();
        Department created = create(tenantId, "Financeiro");
        GetDepartment getDepartment = new GetDepartment(departmentRepository);

        Department found = getDepartment.get(tenantId, created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.name()).isEqualTo("Financeiro");

        assertThatThrownBy(() -> getDepartment.get(tenantId, UUID.randomUUID()))
            .isInstanceOf(DepartmentNotFoundException.class);
    }

    @Test
    void shouldMatchDescriptionOnListDepartments() {
        TenantId tenantId = TenantId.random();
        Department d = create(tenantId, "TI");
        updateDepartment.update(new UpdateDepartmentCommand(tenantId, d.id(), "TI", "Suporte Tecnico Especializado", null, null));

        List<Department> result = listDepartments.list(tenantId, "especializado", null);

        assertThat(result).extracting(Department::name).containsExactly("TI");
    }

    @Test
    void shouldConfigureModelPolicyNewAndUpdate() {
        TenantId tenantId = TenantId.random();
        Department dept = create(tenantId, "Engenharia");
        UUID model1 = UUID.randomUUID();
        UUID model2 = UUID.randomUUID();

        Map<UUID, br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy> policies = new LinkedHashMap<>();
        br.com.qsoft.governance.department.application.port.ModelPolicyRepository modelPolicyRepo =
            new br.com.qsoft.governance.department.application.port.ModelPolicyRepository() {
                @Override
                public br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy save(
                    br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy policy) {
                    policies.put(policy.departmentId(), policy);
                    return policy;
                }

                @Override
                public Optional<br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy> findByTenantIdAndDepartmentId(
                    TenantId t, UUID d) {
                    return Optional.ofNullable(policies.get(d));
                }
            };

        ConfigureModelPolicy configPolicy = new ConfigureModelPolicy(departmentRepository, modelPolicyRepo);

        // First configure (new)
        configPolicy.configure(tenantId, dept.id(), List.of(model1, model2), model1);
        assertThat(policies.get(dept.id())).isNotNull();

        // Second configure (update existing)
        configPolicy.configure(tenantId, dept.id(), List.of(model1), model1);
        assertThat(policies.get(dept.id()).allowedModelIds()).containsExactly(model1);

        // Non existing department
        assertThatThrownBy(() -> configPolicy.configure(tenantId, UUID.randomUUID(), List.of(model1), model1))
            .isInstanceOf(DepartmentNotFoundException.class);
    }

    @Test
    void shouldReturnAllActiveDepartmentsWhenSearchIsBlank() {
        TenantId tenantId = TenantId.random();
        create(tenantId, "RH");
        create(tenantId, "Financeiro");

        List<Department> result = listDepartments.list(tenantId, "   ", null);

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldNotMatchSearchWhenDescriptionIsNull() {
        TenantId tenantId = TenantId.random();
        // Create department with null description
        Department dept = createDepartment.create(new CreateDepartmentCommand(
            tenantId, "Suporte", null, null, null));

        List<Department> result = listDepartments.list(tenantId, "comercial", null);

        assertThat(result).isEmpty();
    }
}


