package br.com.qsoft.governance.agentpolicies.domain;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versão do documento (ADR-25): no máximo uma {@code ACTIVE} por documento,
 * garantida pelo índice parcial da V019.
 */
public final class DocumentVersion {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID documentId;
    private final int versionNumber;
    private DocumentVersionStatus status;
    private final Instant createdAt;

    private DocumentVersion(UUID id, TenantId tenantId, UUID documentId, int versionNumber,
                            DocumentVersionStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber deve ser >= 1.");
        }
        this.versionNumber = versionNumber;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static DocumentVersion active(UUID id, TenantId tenantId, UUID documentId,
                                         int versionNumber) {
        return new DocumentVersion(id, tenantId, documentId, versionNumber,
            DocumentVersionStatus.ACTIVE, Instant.now());
    }

    public static DocumentVersion restore(UUID id, TenantId tenantId, UUID documentId,
                                          int versionNumber, DocumentVersionStatus status,
                                          Instant createdAt) {
        return new DocumentVersion(id, tenantId, documentId, versionNumber, status, createdAt);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID documentId() {
        return documentId;
    }

    public int versionNumber() {
        return versionNumber;
    }

    public DocumentVersionStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public DocumentVersion activate() {
        this.status = DocumentVersionStatus.ACTIVE;
        return this;
    }

    public DocumentVersion supersede() {
        this.status = DocumentVersionStatus.SUPERSEDED;
        return this;
    }
}
