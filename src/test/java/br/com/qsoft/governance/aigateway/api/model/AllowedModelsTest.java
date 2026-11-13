package br.com.qsoft.governance.aigateway.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AllowedModelsTest {

    @Test
    void normalizesAbsentListAndCopiesSuppliedModels() {
        assertThat(new AllowedModels(null, null)).isEqualTo(AllowedModels.empty());

        AllowedModel model = new AllowedModel(UUID.randomUUID(), "Modelo", "Provider", true, null);
        List<AllowedModel> models = new ArrayList<>(List.of(model));
        AllowedModels allowed = new AllowedModels(model.id(), models);
        models.clear();

        assertThat(allowed.defaultModelId()).isEqualTo(model.id());
        assertThat(allowed.models()).containsExactly(model);
    }
}
