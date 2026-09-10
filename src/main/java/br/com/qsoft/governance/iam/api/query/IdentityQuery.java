package br.com.qsoft.governance.iam.api.query;

import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Optional;

/**
 * Contrato público do módulo IAM para consulta de identidade/contexto de usuário.
 * Outros módulos consultam a hierarquia de um usuário aqui, em vez de
 * reinterpretar regras de nível (ADR-04).
 */
public interface IdentityQuery {

    Optional<UserContext> getUserContext(UserId userId);
}
