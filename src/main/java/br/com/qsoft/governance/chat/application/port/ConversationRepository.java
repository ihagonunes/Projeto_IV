package br.com.qsoft.governance.chat.application.port;

import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.Optional;
import java.util.UUID;

/** Persistência de conversas; toda leitura é por tenant + dono. */
public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findByTenantIdAndIdAndOwner(TenantId tenantId, UUID id, UserId owner);

    PageResult<Conversation> findByOwner(TenantId tenantId, UserId owner, int page, int size);
}
