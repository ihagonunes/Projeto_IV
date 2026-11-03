package br.com.qsoft.governance.chat.infrastructure.persistence;

import br.com.qsoft.governance.chat.application.port.ConversationRepository;
import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
        return jpaRepository.findByTenantIdAndIdAndOwnerUserIdAndStatusNot(
            tenantId.value(), id, owner.value(), ConversationStatus.DELETED)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Conversation> findByOwner(TenantId tenantId, UserId owner, int page, int size) {
        return search(tenantId, owner, ConversationStatus.ACTIVE, null, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Conversation> search(TenantId tenantId, UserId owner,
                                           ConversationStatus status, String titleQuery,
                                           int page, int size) {
        Page<ConversationJpaEntity> result = jpaRepository.findAll(
            specification(tenantId, owner, status, titleQuery),
            PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return new PageResult<>(
            result.getContent().stream().map(mapper::toDomain).toList(),
            result.getTotalElements(), page, size);
    }

    private Specification<ConversationJpaEntity> specification(TenantId tenantId, UserId owner,
                                                               ConversationStatus status,
                                                               String titleQuery) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId.value()));
            predicates.add(builder.equal(root.get("ownerUserId"), owner.value()));
            predicates.add(builder.equal(root.get("status"), status));
            if (titleQuery != null && !titleQuery.isBlank()) {
                predicates.add(builder.like(
                    builder.lower(root.get("title").as(String.class)),
                    likePattern(titleQuery), '\\'));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String likePattern(String value) {
        String escaped = value.trim().toLowerCase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
