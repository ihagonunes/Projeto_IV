package br.com.qsoft.governance.department.domain.modelpolicy;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Política de modelos de um departamento (ADR-07): o conjunto de modelos
 * permitidos e o modelo default.
 * <p>
 * Invariante (ADR-08): se {@code allowedModelIds} não estiver vazio, o
 * {@code defaultModelId} é obrigatório <b>e pertence ao conjunto</b>; conjunto
 * vazio implica default nulo. Os IDs são referências <b>cross-module</b> ao
 * catálogo do aigateway (sem FK — ADR-29); a validação de que existem no
 * catálogo é feita por quem orquestra (aigateway), não pelo domínio.
 */
public final class ModelPolicy {

    private final UUID departmentId;
    private final Set<UUID> allowedModelIds;
    private final UUID defaultModelId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ModelPolicy(UUID departmentId, Set<UUID> allowedModelIds, UUID defaultModelId,
                        Instant createdAt, Instant updatedAt) {
        this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
        this.allowedModelIds = Set.copyOf(allowedModelIds);
        this.defaultModelId = defaultModelId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ModelPolicy create(UUID departmentId, Collection<UUID> allowedModelIds,
                                     UUID defaultModelId) {
        Set<UUID> allowed = normalize(allowedModelIds);
        validate(allowed, defaultModelId);
        Instant now = Instant.now();
        return new ModelPolicy(departmentId, allowed, defaultModelId, now, now);
    }

    public static ModelPolicy restore(UUID departmentId, Set<UUID> allowedModelIds,
                                      UUID defaultModelId, Instant createdAt, Instant updatedAt) {
        return new ModelPolicy(departmentId, allowedModelIds, defaultModelId, createdAt, updatedAt);
    }

    /** Substitui o conjunto permitido e o default, revalidando o invariante. */
    public ModelPolicy change(Collection<UUID> allowedModelIds, UUID defaultModelId) {
        Set<UUID> allowed = normalize(allowedModelIds);
        validate(allowed, defaultModelId);
        return new ModelPolicy(departmentId, allowed, defaultModelId, createdAt, Instant.now());
    }

    public UUID departmentId() {
        return departmentId;
    }

    public Set<UUID> allowedModelIds() {
        return allowedModelIds;
    }

    public UUID defaultModelId() {
        return defaultModelId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static Set<UUID> normalize(Collection<UUID> allowedModelIds) {
        if (allowedModelIds == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(allowedModelIds);
    }

    private static void validate(Set<UUID> allowed, UUID defaultModelId) {
        if (allowed.isEmpty()) {
            if (defaultModelId != null) {
                throw new IllegalArgumentException(
                    "Sem modelos permitidos, o modelo default deve ser nulo.");
            }
            return;
        }
        if (defaultModelId == null) {
            throw new IllegalArgumentException(
                "Com modelos permitidos, o modelo default é obrigatório.");
        }
        if (!allowed.contains(defaultModelId)) {
            throw new IllegalArgumentException(
                "O modelo default deve pertencer aos modelos permitidos.");
        }
    }
}
