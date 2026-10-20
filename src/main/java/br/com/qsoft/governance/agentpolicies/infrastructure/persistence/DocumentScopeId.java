package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Chave composta (document_id, department_id) da tabela de escopos. */
public class DocumentScopeId implements Serializable {

    private UUID documentId;
    private UUID departmentId;

    public DocumentScopeId() {
    }

    public DocumentScopeId(UUID documentId, UUID departmentId) {
        this.documentId = documentId;
        this.departmentId = departmentId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DocumentScopeId that)) {
            return false;
        }
        return Objects.equals(documentId, that.documentId)
            && Objects.equals(departmentId, that.departmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId, departmentId);
    }
}
