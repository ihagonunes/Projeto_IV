package br.com.qsoft.governance.iam.application;

/**
 * Dados de entrada para o registro de um tenant.
 */
public record RegisterTenantCommand(String slug, String name) {
}
