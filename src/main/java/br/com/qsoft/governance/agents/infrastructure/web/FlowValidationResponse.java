package br.com.qsoft.governance.agents.infrastructure.web;

import br.com.qsoft.governance.agents.domain.FlowValidation;
import java.util.List;

/** Resultado da validação: `valid`, `needsMerge` e erros com código/caminho. */
public record FlowValidationResponse(
    boolean valid,
    boolean needsMerge,
    List<FlowValidationErrorResponse> errors
) {

    static FlowValidationResponse from(FlowValidation validation) {
        return new FlowValidationResponse(validation.valid(), validation.needsMerge(),
            validation.errors().stream().map(FlowValidationErrorResponse::from).toList());
    }
}
