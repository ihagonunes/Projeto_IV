package br.com.qsoft.governance.chat.application.port;

import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationStatus;
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

    /**
     * Busca por dono, status e título (ILIKE escapado), em ordem
     * {@code updated_at DESC, id DESC}. Implementações em memória podem herdar
     * o default, que ignora status/busca e delega para {@link #findByOwner}.
     */
    default PageResult<Conversation> search(TenantId tenantId, UserId owner,
                                            ConversationStatus status, String titleQuery,
                                            int page, int size) {
        return findByOwner(tenantId, owner, page, size);
    }
}
