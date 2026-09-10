package br.com.qsoft.governance.sharedkernel.id;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidade imutável de um Tenant (organização) na plataforma.
 * Pertence ao sharedkernel por ser um tipo transversal, conforme ADR-31.
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "TenantId value cannot be null");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId random() {
        return new TenantId(UUID.randomUUID());
    }

    public static TenantId fromString(String value) {
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
