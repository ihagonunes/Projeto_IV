package br.com.qsoft.governance.chat.infrastructure.persistence;

import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter de conversas; toda leitura filtra tenant + dono. */
@Component
public class ConversationRepositoryAdapter implements ConversationRepository {

    private final ConversationJpaRepository jpaRepository;
    private final ConversationJpaMapper mapper = new ConversationJpaMapper();

    public ConversationRepositoryAdapter(ConversationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Conversation save(Conversation conversation) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(conversation)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findByTenantIdAndIdAndOwner(TenantId tenantId, UUID id,
                                                              UserId owner) {
        return jpaRepository.findByTenantIdAndIdAndOwnerUserId(
            tenantId.value(), id, owner.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Conversation> findByOwner(TenantId tenantId, UserId owner, int page, int size) {
        Page<ConversationJpaEntity> result = jpaRepository.findByTenantIdAndOwnerUserId(
            tenantId.value(), owner.value(),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return new PageResult<>(
            result.getContent().stream().map(mapper::toDomain).toList(),
            result.getTotalElements(), page, size);
    }
}
