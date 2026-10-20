package br.com.qsoft.governance.aigateway.application.port;

import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;

/** Seleciona o adaptador de provider conforme o {@code kind} configurado (ADR-38). */
public interface ProviderClientRegistry {

    ProviderClient forKind(ProviderKind kind);
}
