package br.com.qsoft.governance.iam.domain.tenant;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Aggregate raiz do módulo IAM que representa a organização cliente.
 * É a raiz do tenant; portanto não possui um tenant_id próprio.
 * <p>
 * O {@code slug} identifica a organização no login e é imutável. O
 * {@code emailDomain} é o domínio de e-mail corporativo cadastrado (SPI-54),
 * único globalmente para que a resolução do tenant pelo domínio do e-mail não
 * seja ambígua (ADR-32). O {@code logoUrl} e as cores compõem a identidade
 * visual (white-label, SPI-53).
 */
public final class Tenant {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final TenantId id;
    private final String slug;
    private final String emailDomain;
    private String name;
    private String logoUrl;
    private String primaryColor;
    private String secondaryColor;
    private TenantStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Tenant(TenantId id, String slug, String emailDomain, String name,
                   String logoUrl, String primaryColor, String secondaryColor,
                   TenantStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.slug = Objects.requireNonNull(slug, "slug");
        this.emailDomain = emailDomain;
        this.name = Objects.requireNonNull(name, "name");
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Cria um tenant ativo com o domínio de e-mail corporativo cadastrado.
     * O domínio é obrigatório no cadastro (SPI-54); a identidade visual é
     * definida depois (SPI-53).
     */
    public static Tenant create(TenantId id, String slug, String name, String emailDomain) {
        Instant now = Instant.now();
        return new Tenant(id, slug, requireEmailDomain(emailDomain), name,
            null, null, null, TenantStatus.ACTIVE, now, now);
    }

    /**
     * Reconstrói o tenant a partir da persistência. O domínio e a identidade
     * visual podem ser nulos (tenant legado / ainda não configurado).
     */
    public static Tenant restore(TenantId id, String slug, String name, String emailDomain,
                                 String logoUrl, String primaryColor, String secondaryColor,
                                 TenantStatus status, Instant createdAt, Instant updatedAt) {
        return new Tenant(id, slug, normalizeDomain(emailDomain), name,
            logoUrl, primaryColor, secondaryColor, status, createdAt, updatedAt);
    }

    /** Sobrecarga para tenants sem identidade visual persistida. */
    public static Tenant restore(TenantId id, String slug, String name, String emailDomain,
                                 TenantStatus status, Instant createdAt, Instant updatedAt) {
        return restore(id, slug, name, emailDomain, null, null, null, status, createdAt, updatedAt);
    }

    /** Sobrecarga para tenants sem domínio cadastrado (legado). */
    public static Tenant restore(TenantId id, String slug, String name,
                                 TenantStatus status, Instant createdAt, Instant updatedAt) {
        return restore(id, slug, name, null, null, null, null, status, createdAt, updatedAt);
    }

    public TenantId id() {
        return id;
    }

    public String slug() {
        return slug;
    }

    public String name() {
        return name;
    }

    /** Domínio de e-mail corporativo cadastrado; {@code null} se ainda não houver. */
    public String emailDomain() {
        return emailDomain;
    }

    public String logoUrl() {
        return logoUrl;
    }

    public String primaryColor() {
        return primaryColor;
    }

    public String secondaryColor() {
        return secondaryColor;
    }

    public TenantStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Tenant activate() {
        if (status != TenantStatus.ACTIVE) {
            this.status = TenantStatus.ACTIVE;
            this.updatedAt = Instant.now();
        }
        return this;
    }

    public Tenant deactivate() {
        if (status != TenantStatus.INACTIVE) {
            this.status = TenantStatus.INACTIVE;
            this.updatedAt = Instant.now();
        }
        return this;
    }

    /**
     * Edita o nome (dado cadastral) do tenant (SPI-52).
     * O slug é imutável: identifica a organização na resolução do tenant.
     */
    public Tenant changeName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        this.name = newName.trim();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Edita a identidade visual (white-label) do tenant (SPI-53).
     * Campos nulos limpam o valor correspondente; cores, quando informadas,
     * devem estar no formato {@code #RRGGBB}.
     */
    public Tenant changeBranding(String logoUrl, String primaryColor, String secondaryColor) {
        this.logoUrl = normalizeLogoUrl(logoUrl);
        this.primaryColor = requireHexColor(primaryColor, "primaryColor");
        this.secondaryColor = requireHexColor(secondaryColor, "secondaryColor");
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Normaliza um domínio de e-mail: remove {@code @} inicial, espaços e
     * coloca em minúsculas. {@code null} permanece {@code null}.
     */
    public static String normalizeDomain(String domain) {
        if (domain == null) {
            return null;
        }
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String requireEmailDomain(String emailDomain) {
        String normalized = normalizeDomain(emailDomain);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("emailDomain é obrigatório.");
        }
        return normalized;
    }

    private static String normalizeLogoUrl(String logoUrl) {
        if (logoUrl == null) {
            return null;
        }
        String trimmed = logoUrl.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String requireHexColor(String color, String field) {
        if (color == null) {
            return null;
        }
        String trimmed = color.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!HEX_COLOR.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(field + " deve estar no formato #RRGGBB.");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
