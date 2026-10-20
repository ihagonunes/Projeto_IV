package br.com.qsoft.governance.aigateway.infrastructure.persistence;

import br.com.qsoft.governance.aigateway.application.port.RequestAttemptRepository;
import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter de persistência das tentativas duráveis (ADR-38). */
@Component
public class RequestAttemptRepositoryAdapter implements RequestAttemptRepository {

    private final RequestAttemptJpaRepository jpaRepository;
    private final RequestAttemptJpaMapper mapper = new RequestAttemptJpaMapper();

    public RequestAttemptRepositoryAdapter(RequestAttemptJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public RequestAttempt save(RequestAttempt attempt) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(attempt)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RequestAttempt> findById(UUID requestId) {
        return jpaRepository.findById(requestId).map(mapper::toDomain);
    }
}
