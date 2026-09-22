package br.com.qsoft.governance.iam.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.domain.rbac.PermissionMatrix;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProvisionDefaultRolesTest {

    private final List<Role> roles = new ArrayList<>();

    private final RoleRepository roleRepository = new RoleRepository() {
        @Override
        public Role save(Role role) {
            roles.add(role);
            return role;
        }

        @Override
        public Optional<Role> findById(RoleId id) {
            return roles.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Role> findByTenantIdAndName(TenantId tenantId, String name) {
            return roles.stream()
                .filter(r -> r.tenantId().equals(tenantId) && r.name().equals(name))
                .findFirst();
        }

        @Override
        public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
            return findByTenantIdAndName(tenantId, name).isPresent();
        }
    };

    private final ProvisionDefaultRoles useCase = new ProvisionDefaultRoles(roleRepository);

    @Test
    void shouldProvisionOneRolePerLevelWithMatrixPermissions() {
        TenantId tenantId = TenantId.random();

        List<Role> provisioned = useCase.provision(tenantId);

        assertThat(provisioned).hasSize(HierarchyLevel.values().length);
        assertThat(roles).extracting(Role::name)
            .containsExactlyInAnyOrder("COLLABORATOR", "LEADER", "MANAGER", "EXECUTIVE");

        Role manager = roles.stream().filter(r -> r.name().equals("MANAGER")).findFirst().orElseThrow();
        assertThat(manager.permissions())
            .containsExactlyInAnyOrderElementsOf(PermissionMatrix.permissionsFor(HierarchyLevel.MANAGER));
    }

    @Test
    void shouldBeIdempotent() {
        TenantId tenantId = TenantId.random();

        useCase.provision(tenantId);
        List<Role> second = useCase.provision(tenantId);

        assertThat(second).isEmpty();
        assertThat(roles).hasSize(HierarchyLevel.values().length);
    }
}
