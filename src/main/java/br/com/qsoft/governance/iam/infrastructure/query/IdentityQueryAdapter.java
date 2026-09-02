package br.com.qsoft.governance.iam.infrastructure.query;

import br.com.qsoft.governance.iam.api.model.UserContext;
import br.com.qsoft.governance.iam.api.query.IdentityQuery;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Adapter que satisfaz o contrato público IdentityQuery do IAM, expondo o
 * contexto de hierarquia de um usuário sem vazar tipos do domínio.
 */
@Component
public class IdentityQueryAdapter implements IdentityQuery {

    private final UserRepository userRepository;

    public IdentityQueryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserContext> getUserContext(UserId userId) {
        return userRepository.findById(userId).map(this::toUserContext);
    }

    private UserContext toUserContext(User user) {
        return new UserContext(
            user.id(),
            user.tenantId(),
            user.hierarchyLevel().name(),
            user.departmentId(),
            user.status().name()
        );
    }
}
