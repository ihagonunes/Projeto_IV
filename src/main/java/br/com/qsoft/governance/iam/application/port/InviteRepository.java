package br.com.qsoft.governance.iam.application.port;

import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Optional;

/**
 * Porta de saída do módulo IAM para persistência de convites.
 */
public interface InviteRepository {

    Invite save(Invite invite);

    Optional<Invite> findById(InviteId id);

    Optional<Invite> findByToken(String token);

    boolean existsByToken(String token);

    List<Invite> findByTenantId(TenantId tenantId);
}
