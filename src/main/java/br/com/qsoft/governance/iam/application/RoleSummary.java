package br.com.qsoft.governance.iam.application;

/**
 * Resumo de um papel de sistema para a listagem pública ({@code GET /papeis}).
 */
public record RoleSummary(String key, String scope, int rank, boolean grantable) {
}
