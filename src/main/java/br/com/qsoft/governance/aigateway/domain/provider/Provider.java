package br.com.qsoft.governance.aigateway.domain.provider;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado raiz do catálogo: um fornecedor de IA do tenant (ADR-09/ADR-38).
 * <p>
 * A credencial é apenas uma <b>referência</b> a uma variável de ambiente
 * ({@code credentialRef}); o segredo nunca é persistido aqui (ADR-10).
 */
public final class Provider {

    private static final int NAME_MAX = 150;
    private static final int DEFAULT_TIMEOUT_MS = 30000;
    private static final int MIN_TIMEOUT_MS = 1000;
    private static final int MAX_TIMEOUT_MS = 120000;

    private final UUID id;
    private final TenantId tenantId;
    private String name;
    private final ProviderKind kind;
    private final String baseUrl;
    private final String credentialRef;
    private final int timeoutMs;
    private ProviderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Provider(UUID id, TenantId tenantId, String name, ProviderKind kind, String baseUrl,
                     String credentialRef, int timeoutMs, ProviderStatus status,
                     Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = requireName(name);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.baseUrl = normalizeBaseUrl(kind, baseUrl);
        this.credentialRef = normalizeCredentialRef(credentialRef);
        this.timeoutMs = normalizeTimeout(timeoutMs);
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Provider create(UUID id, TenantId tenantId, String name, ProviderKind kind,
                                  String baseUrl, String credentialRef, Integer timeoutMs) {
        Instant now = Instant.now();
        return new Provider(id, tenantId, name, kind, baseUrl, credentialRef,
            timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs, ProviderStatus.ACTIVE, now, now);
    }

    public static Provider restore(UUID id, TenantId tenantId, String name, ProviderKind kind,
                                   String baseUrl, String credentialRef, int timeoutMs,
                                   ProviderStatus status, Instant createdAt, Instant updatedAt) {
        return new Provider(id, tenantId, name, kind, baseUrl, credentialRef, timeoutMs,
            status, createdAt, updatedAt);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public ProviderKind kind() {
        return kind;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String credentialRef() {
        return credentialRef;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public ProviderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == ProviderStatus.ACTIVE;
    }

    public Provider rename(String newName) {
        this.name = requireName(newName);
        return touch();
    }

    public Provider activate() {
        if (status != ProviderStatus.ACTIVE) {
            this.status = ProviderStatus.ACTIVE;
            touch();
        }
        return this;
    }

    public Provider deactivate() {
        if (status != ProviderStatus.INACTIVE) {
            this.status = ProviderStatus.INACTIVE;
            touch();
        }
        return this;
    }

    private Provider touch() {
        this.updatedAt = Instant.now();
        return this;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("name deve ter no máximo " + NAME_MAX + " caracteres.");
        }
        return trimmed;
    }

    private static String normalizeBaseUrl(ProviderKind kind, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            if (kind == ProviderKind.OPENAI_COMPATIBLE) {
                throw new IllegalArgumentException("baseUrl é obrigatório para OPENAI_COMPATIBLE.");
            }
            return null;
        }
        return baseUrl.trim();
    }

    private static String normalizeCredentialRef(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return null;
        }
        String trimmed = credentialRef.trim();
        if (!trimmed.matches("^[A-Z][A-Z0-9_]{2,99}$")) {
            throw new IllegalArgumentException("credentialRef inválido.");
        }
        return trimmed;
    }

    private static int normalizeTimeout(int timeoutMs) {
        if (timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException(
                "timeoutMs deve estar entre " + MIN_TIMEOUT_MS + " e " + MAX_TIMEOUT_MS + ".");
        }
        return timeoutMs;
    }
}
