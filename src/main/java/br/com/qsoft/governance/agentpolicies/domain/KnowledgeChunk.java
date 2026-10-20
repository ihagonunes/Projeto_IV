package br.com.qsoft.governance.agentpolicies.domain;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Objects;
import java.util.UUID;

/**
 * Trecho indexado de uma versão. O {@code search_vector} é gerado pelo banco
 * (V019) e não é mapeado no domínio.
 */
public final class KnowledgeChunk {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID versionId;
    private final int ordinal;
    private final String text;

    private KnowledgeChunk(UUID id, TenantId tenantId, UUID versionId, int ordinal, String text) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.versionId = Objects.requireNonNull(versionId, "versionId");
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal não pode ser negativo.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("texto do trecho é obrigatório.");
        }
        this.ordinal = ordinal;
        this.text = text;
    }

    public static KnowledgeChunk create(UUID id, TenantId tenantId, UUID versionId, int ordinal,
                                        String text) {
        return new KnowledgeChunk(id, tenantId, versionId, ordinal, text);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID versionId() {
        return versionId;
    }

    public int ordinal() {
        return ordinal;
    }

    public String text() {
        return text;
    }
}
