package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

/** Escopo departamental do documento (V019); sem FK cross-module para o department. */
@Entity
@Table(name = "agentpolicies_document_scopes")
@IdClass(DocumentScopeId.class)
public class DocumentScopeJpaEntity {

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Id
    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    protected DocumentScopeJpaEntity() {
        // para JPA
    }

    public DocumentScopeJpaEntity(UUID documentId, UUID departmentId, UUID tenantId) {
        this.documentId = documentId;
        this.departmentId = departmentId;
        this.tenantId = tenantId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
