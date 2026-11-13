package br.com.qsoft.governance.iam.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IamRepositoryDefaultsTest {

    private final TenantId tenant = TenantId.random();

    @Test
    void inMemoryUserLookupDefaultsDelegateToIdLookup() {
        UserRepository repository = mock(UserRepository.class, CALLS_REAL_METHODS);
        User user = User.create(UserId.random(), tenant, "ana@example.com", "Ana", HierarchyLevel.COLLABORATOR, null);
        doReturn(Optional.of(user)).when(repository).findById(user.id());

        assertThat(repository.findByTenantIdAndId(tenant, user.id())).contains(user);
        assertThat(repository.findByIdUnscoped(user.id())).contains(user);
    }

    @Test
    void inMemoryRoleLockFallbackUsesTenantAndNameLookup() {
        RoleRepository repository = mock(RoleRepository.class, CALLS_REAL_METHODS);
        Role role = Role.create(RoleId.random(), tenant, "MANAGER");
        doReturn(Optional.of(role)).when(repository).findByTenantIdAndName(tenant, "MANAGER");

        assertThat(repository.findByTenantIdAndNameForUpdate(tenant, "MANAGER")).contains(role);
    }

    @Test
    void unsupportedEligibilityLookupsFailExplicitly() {
        UserRepository users = mock(UserRepository.class, CALLS_REAL_METHODS);
        RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class, CALLS_REAL_METHODS);

        assertThatThrownBy(() -> users.findActiveByTenantId(tenant))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> assignments.findUserIdsWithOnlyTenantAdminGrant(tenant))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
