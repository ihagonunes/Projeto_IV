package br.com.qsoft.governance.department.domain.modelpolicy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelPolicyTest {

    private static final UUID DEPARTMENT = UUID.randomUUID();

    @Test
    void shouldCreateWithAllowedAndDefault() {
        UUID modelA = UUID.randomUUID();
        UUID modelB = UUID.randomUUID();

        ModelPolicy policy = ModelPolicy.create(DEPARTMENT, List.of(modelA, modelB), modelB);

        assertThat(policy.departmentId()).isEqualTo(DEPARTMENT);
        assertThat(policy.allowedModelIds()).containsExactlyInAnyOrder(modelA, modelB);
        assertThat(policy.defaultModelId()).isEqualTo(modelB);
        assertThat(policy.createdAt()).isNotNull();
        assertThat(policy.updatedAt()).isNotNull();
    }

    @Test
    void shouldRejectDefaultNotInAllowed() {
        UUID allowed = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertThatThrownBy(() -> ModelPolicy.create(DEPARTMENT, List.of(allowed), other))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDefaultWithoutAllowed() {
        assertThatThrownBy(() -> ModelPolicy.create(DEPARTMENT, List.of(), UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAllowedWithoutDefault() {
        assertThatThrownBy(() -> ModelPolicy.create(DEPARTMENT, List.of(UUID.randomUUID()), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowEmptyAllowedWithNullDefault() {
        ModelPolicy policy = ModelPolicy.create(DEPARTMENT, Set.of(), null);

        assertThat(policy.allowedModelIds()).isEmpty();
        assertThat(policy.defaultModelId()).isNull();
    }

    @Test
    void shouldAllowNullAllowedModelIdsCollectionWithNullDefault() {
        ModelPolicy policy = ModelPolicy.create(DEPARTMENT, null, null);

        assertThat(policy.allowedModelIds()).isEmpty();
        assertThat(policy.defaultModelId()).isNull();
    }

    @Test
    void shouldChangePolicyRevalidatingInvariant() {
        UUID modelA = UUID.randomUUID();
        ModelPolicy policy = ModelPolicy.create(DEPARTMENT, List.of(modelA), modelA);

        ModelPolicy changed = policy.change(List.of(), null);
        assertThat(changed.allowedModelIds()).isEmpty();
        assertThat(changed.defaultModelId()).isNull();

        assertThatThrownBy(() -> policy.change(List.of(modelA), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRestoreModelPolicy() {
        UUID modelA = UUID.randomUUID();
        Instant now = Instant.now();

        ModelPolicy restored = ModelPolicy.restore(DEPARTMENT, Set.of(modelA), modelA, now, now);

        assertThat(restored.departmentId()).isEqualTo(DEPARTMENT);
        assertThat(restored.allowedModelIds()).containsExactly(modelA);
        assertThat(restored.defaultModelId()).isEqualTo(modelA);
        assertThat(restored.createdAt()).isEqualTo(now);
        assertThat(restored.updatedAt()).isEqualTo(now);
    }
}
