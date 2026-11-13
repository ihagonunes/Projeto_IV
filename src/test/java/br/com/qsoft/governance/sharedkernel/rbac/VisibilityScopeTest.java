package br.com.qsoft.governance.sharedkernel.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisibilityScopeTest {
    @Test
    void departmentScopeRequiresAtLeastOneDepartmentAndCopiesIds() {
        UserId self = UserId.random();
        assertThatThrownBy(() -> new VisibilityScope.Departments(Set.of(), self))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vazio");
        UUID department = UUID.randomUUID();
        var ids = new HashSet<>(Set.of(department));
        var scope = new VisibilityScope.Departments(ids, self);
        ids.clear();

        assertThat(scope.coversDepartment(department)).isTrue();
        assertThat(scope.coversDepartment(UUID.randomUUID())).isFalse();
    }
}
