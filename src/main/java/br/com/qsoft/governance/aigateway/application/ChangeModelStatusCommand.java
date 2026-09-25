package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.UUID;

/** Dados de entrada da ativação/desativação de um Model (SPI-81). */
public record ChangeModelStatusCommand(TenantId tenantId, UUID modelId, ModelStatus status) {
}
