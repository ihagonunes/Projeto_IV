package br.com.qsoft.governance.sharedkernel.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade imutável de um papel (Role) de autorização.
 */
public record RoleId(UUID value) {

    public RoleId {
        Objects.requireNonNull(value, "RoleId value cannot be null");
    }

    public static RoleId of(UUID value) {
        return new RoleId(value);
    }

    public static RoleId random() {
        return new RoleId(UUID.randomUUID());
    }

    public static RoleId fromString(String value) {
        return new RoleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
