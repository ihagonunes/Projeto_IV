package br.com.qsoft.governance.sharedkernel.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade imutável de um usuário da plataforma.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "UserId value cannot be null");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId random() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId fromString(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
