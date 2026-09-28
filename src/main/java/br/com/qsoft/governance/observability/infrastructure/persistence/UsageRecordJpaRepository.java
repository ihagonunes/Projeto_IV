package br.com.qsoft.governance.observability.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UsageRecordJpaRepository extends JpaRepository<UsageRecordJpaEntity, UUID>,
    JpaSpecificationExecutor<UsageRecordJpaEntity> {

    boolean existsByRequestId(UUID requestId);
}
