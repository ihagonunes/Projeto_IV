package br.com.qsoft.governance.iam.application;

/**
 * Dados de entrada para o registro de um tenant.
 * <code>emailDomain</code> é o domínio de e-mail corporativo (SPI-54),
 * obrigatório e único globalmente (ADR-32).
 */
public record RegisterTenantCommand(String slug, String name, String emailDomain) {
}
