package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.sharedkernel.id.TenantId;

/** Dados de entrada da criação de um Provider do catálogo. */
public record CreateProviderCommand(TenantId tenantId, String name) {
}
