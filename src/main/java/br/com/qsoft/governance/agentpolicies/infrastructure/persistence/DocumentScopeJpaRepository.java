package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentScopeJpaRepository
    extends JpaRepository<DocumentScopeJpaEntity, DocumentScopeId> {
}
