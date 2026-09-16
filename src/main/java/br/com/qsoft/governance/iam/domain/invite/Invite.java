package br.com.qsoft.governance.iam.domain.invite;

import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Convite de usuário para um tenant: e-mail, nível hierárquico (papel) e
 * Department opcional (referência por ID), com token aleatório e expiração.
 * O domínio não depende de Spring/JPA (ADR-31).
 */
public final class Invite {

    private final InviteId id;
    private final TenantId tenantId;
    private final String email;
    private final HierarchyLevel hierarchyLevel;
    private final UUID departmentId;
    private final String token;
    private InviteStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant acceptedAt;

    private Invite(InviteId id, TenantId tenantId, String email, HierarchyLevel hierarchyLevel,
                   UUID departmentId, String token, InviteStatus status,
                   Instant createdAt, Instant expiresAt, Instant acceptedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.email = requireEmail(email);
        this.hierarchyLevel = Objects.requireNonNull(hierarchyLevel, "hierarchyLevel");
        this.departmentId = departmentId;
        this.token = Objects.requireNonNull(token, "token");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.acceptedAt = acceptedAt;
    }

    public static Invite create(InviteId id, TenantId tenantId, String email,
                                HierarchyLevel hierarchyLevel, UUID departmentId,
                                String token, Instant expiresAt) {
        return new Invite(id, tenantId, email, hierarchyLevel, departmentId, token,
            InviteStatus.PENDING, Instant.now(), expiresAt, null);
    }

    public static Invite restore(InviteId id, TenantId tenantId, String email,
                                 HierarchyLevel hierarchyLevel, UUID departmentId,
                                 String token, InviteStatus status, Instant createdAt,
                                 Instant expiresAt, Instant acceptedAt) {
        return new Invite(id, tenantId, email, hierarchyLevel, departmentId, token,
            status, createdAt, expiresAt, acceptedAt);
    }

    public InviteId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String email() {
        return email;
    }

    public HierarchyLevel hierarchyLevel() {
        return hierarchyLevel;
    }

    public UUID departmentId() {
        return departmentId;
    }

    public String token() {
        return token;
    }

    public InviteStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public boolean isPending() {
        return status == InviteStatus.PENDING;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** Aceita o convite. Rejeita reutilização e convite expirado. */
    public Invite accept(Instant now) {
        if (status != InviteStatus.PENDING || isExpired(now)) {
            throw new InvalidInviteException();
        }
        this.status = InviteStatus.ACCEPTED;
        this.acceptedAt = now;
        return this;
    }

    public Invite revoke() {
        if (status == InviteStatus.PENDING) {
            this.status = InviteStatus.REVOKED;
        }
        return this;
    }

    private static String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email é obrigatório.");
        }
        return email.trim();
    }
}
