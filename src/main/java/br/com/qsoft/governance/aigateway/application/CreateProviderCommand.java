package br.com.qsoft.governance.aigateway.application;

import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import br.com.qsoft.governance.sharedkernel.id.TenantId;

/** Dados de entrada da criação de um Provider do catálogo (ADR-38). */
public record CreateProviderCommand(
    TenantId tenantId,
    String name,
    ProviderKind kind,
    String baseUrl,
    String credentialRef,
    Integer timeoutMs
) {
}
