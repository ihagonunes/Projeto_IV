package br.com.qsoft.governance.aigateway.infrastructure.query;

import br.com.qsoft.governance.aigateway.api.model.CatalogModelInfo;
import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIToolCatalogQueryAdapterTest {

    @Mock
    private ModelRepository modelRepository;

    @InjectMocks
    private AIToolCatalogQueryAdapter adapter;

    private final TenantId tenantId = TenantId.random();
    private final UUID modelId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();

    @Test
    void deve_retornar_catalog_model_info_quando_modelo_existir() {
        Model model = Model.create(modelId, tenantId, providerId, "gpt-4o", BigDecimal.ONE, BigDecimal.TEN);
        when(modelRepository.findByTenantIdAndId(tenantId, modelId)).thenReturn(Optional.of(model));

        Optional<CatalogModelInfo> result = adapter.findModel(tenantId, modelId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(modelId);
        assertThat(result.get().providerId()).isEqualTo(providerId);
        assertThat(result.get().name()).isEqualTo("gpt-4o");
        assertThat(result.get().status()).isEqualTo("AVAILABLE");

        verify(modelRepository).findByTenantIdAndId(tenantId, modelId);
    }

    @Test
    void deve_retornar_vazio_quando_modelo_nao_for_encontrado() {
        when(modelRepository.findByTenantIdAndId(tenantId, modelId)).thenReturn(Optional.empty());

        Optional<CatalogModelInfo> result = adapter.findModel(tenantId, modelId);

        assertThat(result).isEmpty();
        verify(modelRepository).findByTenantIdAndId(tenantId, modelId);
    }

    @Test
    void deve_retornar_true_em_all_models_exist_quando_colecao_de_ids_for_nula() {
        boolean exists = adapter.allModelsExist(tenantId, null);

        assertThat(exists).isTrue();
        verify(modelRepository, never()).findByTenantIdAndIdIn(any(), any());
    }

    @Test
    void deve_retornar_true_em_all_models_exist_quando_colecao_de_ids_for_vazia() {
        boolean exists = adapter.allModelsExist(tenantId, List.of());

        assertThat(exists).isTrue();
        verify(modelRepository, never()).findByTenantIdAndIdIn(any(), any());
    }

    @Test
    void deve_retornar_true_quando_todos_os_modelos_existirem() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Set<UUID> modelIds = Set.of(id1, id2);

        Model m1 = Model.create(id1, tenantId, providerId, "gpt-4o", BigDecimal.ONE, BigDecimal.TEN);
        Model m2 = Model.create(id2, tenantId, providerId, "claude-3-5", BigDecimal.ONE, BigDecimal.TEN);

        when(modelRepository.findByTenantIdAndIdIn(tenantId, modelIds)).thenReturn(List.of(m1, m2));

        boolean exists = adapter.allModelsExist(tenantId, modelIds);

        assertThat(exists).isTrue();
        verify(modelRepository).findByTenantIdAndIdIn(tenantId, modelIds);
    }

    @Test
    void deve_retornar_false_quando_algum_modelo_nao_existir() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Set<UUID> modelIds = Set.of(id1, id2);

        Model m1 = Model.create(id1, tenantId, providerId, "gpt-4o", BigDecimal.ONE, BigDecimal.TEN);

        when(modelRepository.findByTenantIdAndIdIn(tenantId, modelIds)).thenReturn(List.of(m1));

        boolean exists = adapter.allModelsExist(tenantId, modelIds);

        assertThat(exists).isFalse();
        verify(modelRepository).findByTenantIdAndIdIn(tenantId, modelIds);
    }
}
