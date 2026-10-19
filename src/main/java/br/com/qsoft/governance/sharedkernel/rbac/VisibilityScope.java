package br.com.qsoft.governance.sharedkernel.rbac;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Visibilidade de consultas agregadas devolvida pelo IAM (ADR-36).
 * <p>
 * É a união das concessões ativas que contêm a permissão consultada. Não existe
 * caminho nulo-permissivo: sem concessão aplicável, o resultado é {@link None}.
 */
public sealed interface VisibilityScope {

    /** Todas as linhas do tenant. */
    record TenantWide() implements VisibilityScope {
    }

    /** Linhas dos departamentos de {@code ids} OU pertencentes a {@code self} ({@code ids} não vazio). */
    record Departments(Set<UUID> ids, UserId self) implements VisibilityScope {

        public Departments {
            Objects.requireNonNull(ids, "ids é obrigatório.");
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("ids não pode ser vazio.");
            }
            Objects.requireNonNull(self, "self é obrigatório.");
            ids = Set.copyOf(ids);
        }
    }

    /** Apenas as linhas do próprio {@code self}. */
    record SelfOnly(UserId self) implements VisibilityScope {

        public SelfOnly {
            Objects.requireNonNull(self, "self é obrigatório.");
        }
    }

    /** Nenhuma visibilidade. */
    record None() implements VisibilityScope {
    }

    /**
     * Indica se o departamento é coberto por este escopo.
     * {@code TenantWide} cobre todos; {@code Departments} cobre os ids; os demais, nenhum.
     */
    default boolean coversDepartment(UUID departmentId) {
        return this instanceof TenantWide
            || (this instanceof Departments departments && departments.ids().contains(departmentId));
    }
}
