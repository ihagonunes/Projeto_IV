package br.com.qsoft.governance.agentpolicies.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.agentpolicies.domain.DocumentVersionStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class KnowledgeSeederRepositoryAdapterTest {

    private final TenantId tenant = TenantId.random();
    private final KnowledgeDocumentJpaRepository documents = mock(KnowledgeDocumentJpaRepository.class);
    private final DocumentScopeJpaRepository scopes = mock(DocumentScopeJpaRepository.class);
    private final DocumentVersionJpaRepository versions = mock(DocumentVersionJpaRepository.class);
    private final KnowledgeChunkJpaRepository chunks = mock(KnowledgeChunkJpaRepository.class);
    private final KnowledgeSeederRepositoryAdapter adapter =
        new KnowledgeSeederRepositoryAdapter(documents, scopes, versions, chunks);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void persistsLinkedDocumentVersionAndOrderedChunks(boolean tenantWide) {
        UUID department = UUID.randomUUID();
        adapter.seed(tenant, "Política", tenantWide, tenantWide ? Set.of() : Set.of(department),
            List.of("Primeiro trecho", "Segundo trecho"));

        ArgumentCaptor<KnowledgeDocumentJpaEntity> documentCapture = ArgumentCaptor.captor();
        verify(documents).save(documentCapture.capture());
        KnowledgeDocumentJpaEntity document = documentCapture.getValue();
        assertThat(document.getId()).isNotNull();
        assertThat(document.getTenantId()).isEqualTo(tenant.value());
        assertThat(document.getTitle()).isEqualTo("Política");
        assertThat(document.isTenantWide()).isEqualTo(tenantWide);
        assertThat(document.getCreatedAt()).isNotNull();
        assertThat(document.getUpdatedAt()).isEqualTo(document.getCreatedAt());

        ArgumentCaptor<DocumentVersionJpaEntity> versionCapture = ArgumentCaptor.captor();
        verify(versions).save(versionCapture.capture());
        DocumentVersionJpaEntity version = versionCapture.getValue();
        assertThat(version.getId()).isNotNull();
        assertThat(version.getTenantId()).isEqualTo(tenant.value());
        assertThat(version.getDocumentId()).isEqualTo(document.getId());
        assertThat(version.getVersionNumber()).isEqualTo(1);
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.ACTIVE);
        assertThat(version.getCreatedAt()).isNotNull();

        ArgumentCaptor<KnowledgeChunkJpaEntity> chunkCapture = ArgumentCaptor.captor();
        verify(chunks, times(2)).save(chunkCapture.capture());
        assertThat(chunkCapture.getAllValues()).extracting(KnowledgeChunkJpaEntity::getText)
            .containsExactly("Primeiro trecho", "Segundo trecho");
        assertThat(chunkCapture.getAllValues()).extracting(KnowledgeChunkJpaEntity::getOrdinal)
            .containsExactly(0, 1);
        assertThat(chunkCapture.getAllValues()).allSatisfy(chunk -> {
            assertThat(chunk.getId()).isNotNull();
            assertThat(chunk.getVersionId()).isEqualTo(version.getId());
            assertThat(chunk.getTenantId()).isEqualTo(tenant.value());
        });

        if (tenantWide) {
            verifyNoInteractions(scopes);
        } else {
            ArgumentCaptor<DocumentScopeJpaEntity> scopeCapture = ArgumentCaptor.captor();
            verify(scopes).save(scopeCapture.capture());
            DocumentScopeJpaEntity scope = scopeCapture.getValue();
            assertThat(scope.getDocumentId()).isEqualTo(document.getId());
            assertThat(scope.getDepartmentId()).isEqualTo(department);
            assertThat(scope.getTenantId()).isEqualTo(tenant.value());
        }
    }

    @Test
    void checksTitleWithinTenantAndSkipsExistingSeed() {
        when(documents.existsByTenantIdAndTitle(tenant.value(), "Política")).thenReturn(true);

        assertThat(adapter.existsByTitle(tenant, "Política")).isTrue();
        assertThat(adapter.existsByTitle(TenantId.random(), "Política")).isFalse();
        adapter.seed(tenant, "Política", true, Set.of(), List.of("Trecho"));

        verify(documents, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(scopes, versions, chunks);
    }

    @Test
    void scopeIdUsesBothComponentsForIdentity() {
        UUID document = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        DocumentScopeId id = new DocumentScopeId(document, department);
        DocumentScopeId same = new DocumentScopeId(document, department);

        assertThat(id.getDocumentId()).isEqualTo(document);
        assertThat(id.getDepartmentId()).isEqualTo(department);
        assertThat(id.equals(id)).isTrue();
        assertThat(id.equals(same)).isTrue();
        assertThat(id.hashCode()).isEqualTo(same.hashCode());
        assertThat(id.equals(new DocumentScopeId(document, UUID.randomUUID()))).isFalse();
        assertThat(id.equals(new DocumentScopeId(UUID.randomUUID(), department))).isFalse();
        assertThat(id.equals(document)).isFalse();
    }
}
