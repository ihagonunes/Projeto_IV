package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionJpaRepository
    extends JpaRepository<DocumentVersionJpaEntity, UUID> {
}
