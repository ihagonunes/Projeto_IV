package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import br.com.qsoft.governance.agentpolicies.application.port.KnowledgeSeederRepository;
import br.com.qsoft.governance.agentpolicies.domain.DocumentVersion;
import br.com.qsoft.governance.agentpolicies.domain.KnowledgeChunk;
import br.com.qsoft.governance.agentpolicies.domain.KnowledgeDocument;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Grava documento + escopos + versão 1 ACTIVE + trechos numa transação. */
@Component
public class KnowledgeSeederRepositoryAdapter implements KnowledgeSeederRepository {

    private final KnowledgeDocumentJpaRepository documentRepository;
    private final DocumentScopeJpaRepository scopeRepository;
    private final DocumentVersionJpaRepository versionRepository;
    private final KnowledgeChunkJpaRepository chunkRepository;

    public KnowledgeSeederRepositoryAdapter(KnowledgeDocumentJpaRepository documentRepository,
                                            DocumentScopeJpaRepository scopeRepository,
                                            DocumentVersionJpaRepository versionRepository,
                                            KnowledgeChunkJpaRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.scopeRepository = scopeRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTitle(TenantId tenantId, String title) {
        return documentRepository.existsByTenantIdAndTitle(tenantId.value(), title);
    }

    @Override
    @Transactional
    public void seed(TenantId tenantId, String title, boolean tenantWide, Set<UUID> departmentIds,
                     List<String> chunks) {
        if (documentRepository.existsByTenantIdAndTitle(tenantId.value(), title)) {
            return;
        }
        KnowledgeDocument document = KnowledgeDocument.create(UUID.randomUUID(), tenantId, title,
            tenantWide, departmentIds);
        documentRepository.save(toEntity(document));

        if (!tenantWide) {
            for (UUID departmentId : document.departmentIds()) {
                scopeRepository.save(new DocumentScopeJpaEntity(
                    document.id(), departmentId, tenantId.value()));
            }
        }

        DocumentVersion version = DocumentVersion.active(UUID.randomUUID(), tenantId,
            document.id(), 1);
        versionRepository.save(toEntity(version));

        for (int ordinal = 0; ordinal < chunks.size(); ordinal++) {
            KnowledgeChunk chunk = KnowledgeChunk.create(UUID.randomUUID(), tenantId,
                version.id(), ordinal, chunks.get(ordinal));
            chunkRepository.save(toEntity(chunk));
        }
    }

    private static KnowledgeDocumentJpaEntity toEntity(KnowledgeDocument document) {
        KnowledgeDocumentJpaEntity entity = new KnowledgeDocumentJpaEntity();
        entity.setId(document.id());
        entity.setTenantId(document.tenantId().value());
        entity.setTitle(document.title());
        entity.setTenantWide(document.isTenantWide());
        entity.setCreatedAt(document.createdAt());
        entity.setUpdatedAt(document.updatedAt());
        return entity;
    }

    private static DocumentVersionJpaEntity toEntity(DocumentVersion version) {
        DocumentVersionJpaEntity entity = new DocumentVersionJpaEntity();
        entity.setId(version.id());
        entity.setTenantId(version.tenantId().value());
        entity.setDocumentId(version.documentId());
        entity.setVersionNumber(version.versionNumber());
        entity.setStatus(version.status());
        entity.setCreatedAt(version.createdAt());
        return entity;
    }

    private static KnowledgeChunkJpaEntity toEntity(KnowledgeChunk chunk) {
        KnowledgeChunkJpaEntity entity = new KnowledgeChunkJpaEntity();
        entity.setId(chunk.id());
        entity.setTenantId(chunk.tenantId().value());
        entity.setVersionId(chunk.versionId());
        entity.setOrdinal(chunk.ordinal());
        entity.setText(chunk.text());
        return entity;
    }
}
