package br.com.qsoft.governance.iam.domain.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

/**
 * Testes unitários da matriz de permissões (POPROJ-52): invariantes do
 * agregado PermissionGrant e catálogos fixos (não tabelas).
 */
class PermissionGrantTest {

    @Test
    void shouldExposeRoleLevelResourceAndAction() {
        PermissionGrant grant = new PermissionGrant(
            UUID.randomUUID(), HierarchyLevel.LEADER, Resource.USER, Action.READ);

        assertThat(grant.roleLevel()).isEqualTo(HierarchyLevel.LEADER);
        assertThat(grant.resource()).isEqualTo(Resource.USER);
        assertThat(grant.action()).isEqualTo(Action.READ);
    }

    @Test
    void shouldRejectNullId() {
        assertThatThrownBy(() -> new PermissionGrant(null, HierarchyLevel.LEADER, Resource.USER, Action.READ))
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @MethodSource("nullFieldSource")
    void shouldRejectNullField(HierarchyLevel roleLevel, Resource resource, Action action) {
        assertThatThrownBy(() -> new PermissionGrant(UUID.randomUUID(), roleLevel, resource, action))
            .isInstanceOf(NullPointerException.class);
    }

    static Stream<Arguments> nullFieldSource() {
        return Stream.of(
            Arguments.of(null, Resource.USER, Action.READ),
            Arguments.of(HierarchyLevel.LEADER, null, Action.READ),
            Arguments.of(HierarchyLevel.LEADER, Resource.USER, null)
        );
    }

    @Test
    void roleLevelShouldBeFixedCatalogWithoutAdmin() {
        // ADMIN não é nível hierárquico; é conjunto de permissões (ADR-04).
        assertThat(Arrays.stream(HierarchyLevel.values()).map(Enum::name))
            .containsExactly("COLLABORATOR", "LEADER", "MANAGER", "EXECUTIVE");
        assertThat(Arrays.stream(HierarchyLevel.values()).map(Enum::name))
            .doesNotContain("ADMIN");
    }

    @Test
    void resourceShouldMatchInitialCatalog() {
        assertThat(Arrays.stream(Resource.values()).map(Enum::name))
            .containsExactly("TENANT", "USER", "MODEL", "MODEL_POLICY", "USAGE_RECORD", "PERMISSION_MATRIX");
    }

    @Test
    void actionShouldBeFixedCatalog() {
        assertThat(Arrays.stream(Action.values()).map(Enum::name))
            .containsExactly("READ", "WRITE", "ADMIN");
    }
}