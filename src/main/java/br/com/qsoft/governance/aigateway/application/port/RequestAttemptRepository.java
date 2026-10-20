package br.com.qsoft.governance.aigateway.application.port;

import br.com.qsoft.governance.aigateway.domain.attempt.RequestAttempt;
import java.util.Optional;
import java.util.UUID;

/** Persistência das tentativas duráveis de IA (ADR-38). */
public interface RequestAttemptRepository {

    RequestAttempt save(RequestAttempt attempt);

    Optional<RequestAttempt> findById(UUID requestId);
}
