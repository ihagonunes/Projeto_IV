package br.com.qsoft.governance.sharedkernel.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade imutável de um convite (invite).
 */
public record InviteId(UUID value) {

    public InviteId {
        Objects.requireNonNull(value, "InviteId value cannot be null");
    }

    public static InviteId of(UUID value) {
        return new InviteId(value);
    }

    public static InviteId random() {
        return new InviteId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
