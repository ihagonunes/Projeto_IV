package br.com.qsoft.governance.sharedkernel.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade imutável de uma atribuição (policy) de papel a usuário.
 */
public record RoleAssignmentId(UUID value) {

    public RoleAssignmentId {
        Objects.requireNonNull(value, "RoleAssignmentId value cannot be null");
    }

    public static RoleAssignmentId of(UUID value) {
        return new RoleAssignmentId(value);
    }

    public static RoleAssignmentId random() {
        return new RoleAssignmentId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
