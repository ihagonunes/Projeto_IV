package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentJpaRepository
    extends JpaRepository<KnowledgeDocumentJpaEntity, UUID> {

    boolean existsByTenantIdAndTitle(UUID tenantId, String title);
}
