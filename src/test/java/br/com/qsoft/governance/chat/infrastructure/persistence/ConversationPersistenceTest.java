package br.com.qsoft.governance.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.chat.domain.Conversation;
import br.com.qsoft.governance.chat.domain.ConversationStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class ConversationPersistenceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-01T12:00:00Z");
    private static final Instant UPDATED_AT = CREATED_AT.plusSeconds(60);

    private final TenantId tenant = TenantId.random();
    private final UserId owner = UserId.random();
    private final UUID conversationId = UUID.randomUUID();
    private final ConversationJpaRepository jpa = mock(ConversationJpaRepository.class);
    private final ConversationRepositoryAdapter adapter = new ConversationRepositoryAdapter(jpa);
    private final ConversationJpaMapper mapper = new ConversationJpaMapper();

    @Test
    void savesAndRestoresSoftDeletedConversation() {
        Conversation conversation = conversation(ConversationStatus.DELETED, UPDATED_AT);
        when(jpa.save(any(ConversationJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(adapter.save(conversation)).usingRecursiveComparison().isEqualTo(conversation);

        ArgumentCaptor<ConversationJpaEntity> captured = ArgumentCaptor.forClass(ConversationJpaEntity.class);
        verify(jpa).save(captured.capture());
        ConversationJpaEntity entity = captured.getValue();
        assertThat(entity.getId()).isEqualTo(conversationId);
        assertThat(entity.getTenantId()).isEqualTo(tenant.value());
        assertThat(entity.getOwnerUserId()).isEqualTo(owner.value());
        assertThat(entity.getTitle()).isEqualTo(conversation.title());
        assertThat(entity.getStatus()).isEqualTo(ConversationStatus.DELETED);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(entity.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(entity.getDeletedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void findsNonDeletedConversationOnlyWithMatchingTenantAndOwner() {
        Conversation archived = conversation(ConversationStatus.ARCHIVED, null);
        when(jpa.findByTenantIdAndIdAndOwnerUserIdAndStatusNot(
            tenant.value(), conversationId, owner.value(), ConversationStatus.DELETED))
            .thenReturn(Optional.of(mapper.toEntity(archived)));
        TenantId otherTenant = TenantId.random();
        UserId otherOwner = UserId.random();

        assertThat(adapter.findByTenantIdAndIdAndOwner(tenant, conversationId, owner))
            .hasValueSatisfying(found -> assertThat(found).usingRecursiveComparison().isEqualTo(archived));
        assertThat(adapter.findByTenantIdAndIdAndOwner(otherTenant, conversationId, owner)).isEmpty();
        assertThat(adapter.findByTenantIdAndIdAndOwner(tenant, conversationId, otherOwner)).isEmpty();
        verify(jpa).findByTenantIdAndIdAndOwnerUserIdAndStatusNot(
            otherTenant.value(), conversationId, owner.value(), ConversationStatus.DELETED);
        verify(jpa).findByTenantIdAndIdAndOwnerUserIdAndStatusNot(
            tenant.value(), conversationId, otherOwner.value(), ConversationStatus.DELETED);
    }

    @Test
    void listsActiveConversationsWithStableSortAndPageMetadata() {
        PageRequest pageRequest = sortedPage(1, 2);
        Conversation active = conversation(ConversationStatus.ACTIVE, null);
        when(jpa.findAll(any(Specification.class), eq(pageRequest)))
            .thenReturn(new PageImpl<>(List.of(mapper.toEntity(active)), pageRequest, 3));

        PageResult<Conversation> page = adapter.findByOwner(tenant, owner, 1, 2);

        assertThat(page.content()).singleElement().usingRecursiveComparison().isEqualTo(active);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertSearchPredicates(pageRequest, ConversationStatus.ACTIVE, null);
    }

    @Test
    void ignoresBlankTitleWhileFilteringByRequestedStatus() {
        PageRequest pageRequest = sortedPage(0, 10);
        when(jpa.findAll(any(Specification.class), eq(pageRequest)))
            .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        assertThat(adapter.search(tenant, owner, ConversationStatus.ARCHIVED, "  ", 0, 10).content())
            .isEmpty();

        assertSearchPredicates(pageRequest, ConversationStatus.ARCHIVED, null);
    }

    @Test
    void searchesTitleCaseInsensitivelyAndEscapesWildcardsAndBackslash() {
        PageRequest pageRequest = sortedPage(0, 10);
        when(jpa.findAll(any(Specification.class), eq(pageRequest)))
            .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        adapter.search(tenant, owner, ConversationStatus.ARCHIVED, "  PLANO_50%\\QSOFT  ", 0, 10);

        assertSearchPredicates(pageRequest, ConversationStatus.ARCHIVED, "%plano\\_50\\%\\\\qsoft%");
    }

    private void assertSearchPredicates(PageRequest pageRequest, ConversationStatus status, String pattern) {
        ArgumentCaptor<Specification<ConversationJpaEntity>> spec = ArgumentCaptor.captor();
        verify(jpa).findAll(spec.capture(), eq(pageRequest));

        Root<ConversationJpaEntity> root = mock();
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<UUID> tenantPath = mock();
        Path<UUID> ownerPath = mock();
        Path<ConversationStatus> statusPath = mock();
        Predicate byTenant = mock(Predicate.class);
        Predicate byOwner = mock(Predicate.class);
        Predicate byStatus = mock(Predicate.class);
        when(root.<UUID>get("tenantId")).thenReturn(tenantPath);
        when(root.<UUID>get("ownerUserId")).thenReturn(ownerPath);
        when(root.<ConversationStatus>get("status")).thenReturn(statusPath);
        when(builder.equal(tenantPath, tenant.value())).thenReturn(byTenant);
        when(builder.equal(ownerPath, owner.value())).thenReturn(byOwner);
        when(builder.equal(statusPath, status)).thenReturn(byStatus);

        List<Predicate> expected = List.of(byTenant, byOwner, byStatus);
        if (pattern != null) {
            Path<String> title = mock();
            Expression<String> lowerTitle = mock();
            Predicate byTitle = mock(Predicate.class);
            when(root.<String>get("title")).thenReturn(title);
            when(title.as(String.class)).thenReturn(title);
            when(builder.lower(title)).thenReturn(lowerTitle);
            when(builder.like(lowerTitle, pattern, '\\')).thenReturn(byTitle);
            expected = List.of(byTenant, byOwner, byStatus, byTitle);
        }

        spec.getValue().toPredicate(root, null, builder);

        ArgumentCaptor<Predicate[]> predicates = ArgumentCaptor.forClass(Predicate[].class);
        verify(builder).and(predicates.capture());
        assertThat(predicates.getValue()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static PageRequest sortedPage(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    private Conversation conversation(ConversationStatus status, Instant deletedAt) {
        return Conversation.restore(conversationId, tenant, owner, "Conversa de teste", status,
            CREATED_AT, UPDATED_AT, deletedAt);
    }
}
