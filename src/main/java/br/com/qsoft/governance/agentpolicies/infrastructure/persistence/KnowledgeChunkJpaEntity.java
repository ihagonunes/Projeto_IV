package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Persistência JPA do trecho. {@code search_vector} é coluna gerada pelo banco
 * e não é mapeada (o Hibernate não precisa dela para inserir/validar).
 */
@Entity
@Table(name = "agentpolicies_chunks")
public class KnowledgeChunkJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    protected KnowledgeChunkJpaEntity() {
        // para JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
