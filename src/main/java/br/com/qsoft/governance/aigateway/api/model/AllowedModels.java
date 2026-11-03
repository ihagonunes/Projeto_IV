package br.com.qsoft.governance.aigateway.api.model;

import java.util.List;
import java.util.UUID;

/** Modelos permitidos ao usuário, com o padrão da política do departamento. */
public record AllowedModels(
    UUID defaultModelId,
    List<AllowedModel> models
) {

    public AllowedModels {
        models = models == null ? List.of() : List.copyOf(models);
    }

    public static AllowedModels empty() {
        return new AllowedModels(null, List.of());
    }
}
