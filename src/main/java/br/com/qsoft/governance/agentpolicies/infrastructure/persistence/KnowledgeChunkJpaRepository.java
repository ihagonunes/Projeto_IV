package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkJpaRepository
    extends JpaRepository<KnowledgeChunkJpaEntity, UUID> {
}
