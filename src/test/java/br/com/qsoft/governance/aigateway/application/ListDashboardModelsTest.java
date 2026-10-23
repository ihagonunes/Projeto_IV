package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ListDashboardModelsTest {
    @Test
    void listsOnlyAvailableModelsFromAuthenticatedTenant() {
        TenantId tenant = TenantId.random();
        ModelRepository repository = mock(ModelRepository.class);
        Model available = Model.create(UUID.randomUUID(), tenant, UUID.randomUUID(), "Disponível",
            null, BigDecimal.ONE, BigDecimal.ONE);
        Model disabled = Model.create(UUID.randomUUID(), tenant, UUID.randomUUID(), "Desativado",
            null, BigDecimal.ONE, BigDecimal.ONE);
        disabled.disable();
        when(repository.findByTenantId(tenant)).thenReturn(List.of(available, disabled));
        assertThat(new ListModels(repository).availableForDashboard(tenant)).containsExactly(available);
        verify(repository).findByTenantId(tenant);
        verifyNoMoreInteractions(repository);
    }
}
