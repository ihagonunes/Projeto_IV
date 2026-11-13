package br.com.qsoft.governance.iam.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantQueryAdapterTest {

    private final TenantRepository repository = mock(TenantRepository.class);
    private final TenantQueryAdapter adapter = new TenantQueryAdapter(repository);

    @Test
    void returnsTenantIdOnlyForKnownSlug() {
        TenantId id = TenantId.random();
        when(repository.findBySlug("qsoft"))
            .thenReturn(Optional.of(Tenant.create(id, "qsoft", "QSoft", "qsoft.example")));

        assertThat(adapter.findBySlug("qsoft")).contains(id);
        assertThat(adapter.findBySlug("missing")).isEmpty();
    }

    @Test
    void rejectsNullAndBlankSlugsBeforeLookup() {
        assertThat(adapter.findBySlug(null)).isEmpty();
        assertThat(adapter.findBySlug("  ")).isEmpty();
        verifyNoInteractions(repository);
    }
}
