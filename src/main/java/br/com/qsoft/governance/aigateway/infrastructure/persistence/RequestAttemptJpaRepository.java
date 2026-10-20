package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestAttemptJpaRepository
    extends JpaRepository<RequestAttemptJpaEntity, UUID> {
}
