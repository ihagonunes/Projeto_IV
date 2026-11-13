package br.com.qsoft.governance.agentpolicies.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class KnowledgeDomainTest {

    private final TenantId tenant = TenantId.random();
    private final UUID documentId = UUID.randomUUID();
    private final UUID department = UUID.randomUUID();

    @Test
    void restoresDepartmentScopeAndTenantWideDocuments() {
        Instant updated = Instant.EPOCH.plusSeconds(60);
        KnowledgeDocument scoped = KnowledgeDocument.restore(documentId, tenant, " Política ",
            false, Set.of(department), Instant.EPOCH, updated);

        assertThat(scoped.title()).isEqualTo("Política");
        assertThat(scoped.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(scoped.updatedAt()).isEqualTo(updated);
        assertThat(scoped.isVisibleTo(department)).isTrue();
        assertThat(scoped.isVisibleTo(UUID.randomUUID())).isFalse();
        assertThat(scoped.isVisibleTo(null)).isFalse();
        KnowledgeDocument wide = KnowledgeDocument.create(documentId, tenant, "Política", true, null);
        assertThat(wide.departmentIds()).isEmpty();
        assertThat(wide.isVisibleTo(null)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingTitleAndChunkText(String value) {
        assertThatThrownBy(() -> KnowledgeDocument.create(documentId, tenant, value, true, Set.of()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("título");
        assertThatThrownBy(() -> KnowledgeChunk.create(UUID.randomUUID(), tenant, documentId, 0, value))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("texto");
    }

    @Test
    void rejectsInvalidScopeTitleLengthAndChunkOrdinal() {
        assertThatThrownBy(() -> KnowledgeDocument.create(documentId, tenant, "Política", false, Set.of()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("departamento");
        assertThatThrownBy(() -> KnowledgeDocument.create(documentId, tenant, "a".repeat(201), true, Set.of()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("200");
        assertThatThrownBy(() -> KnowledgeChunk.create(UUID.randomUUID(), tenant, documentId, -1, "Texto"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ordinal");
    }

    @Test
    void restoresVersionAndTransitionsBetweenActiveAndSuperseded() {
        UUID versionId = UUID.randomUUID();
        DocumentVersion version = DocumentVersion.restore(versionId, tenant, documentId, 3,
            DocumentVersionStatus.SUPERSEDED, Instant.EPOCH);

        assertThat(version.activate().status()).isEqualTo(DocumentVersionStatus.ACTIVE);
        assertThat(version.supersede().status()).isEqualTo(DocumentVersionStatus.SUPERSEDED);
        assertThat(version.versionNumber()).isEqualTo(3);
        assertThat(version.createdAt()).isEqualTo(Instant.EPOCH);
        assertThatThrownBy(() -> DocumentVersion.active(versionId, tenant, documentId, 0))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("versionNumber");
    }
}
