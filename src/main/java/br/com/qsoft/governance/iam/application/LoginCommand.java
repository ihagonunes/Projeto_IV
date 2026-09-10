package br.com.qsoft.governance.iam.application;

/**
 * Dados de entrada do login (tenantSlug + email + password, ADR-30).
 */
public record LoginCommand(String tenantSlug, String email, String password) {
}
