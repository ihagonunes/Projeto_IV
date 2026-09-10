package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Optional;

/**
 * Porta de saída do módulo IAM para persistência de User.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email);

    boolean existsByTenantIdAndEmail(TenantId tenantId, String email);
}
