package br.com.qsoft.governance.iam.application;

/**
 * Dados de entrada para o registro de um tenant.
 * <p>
 * {@code emailDomain} é o domínio de e-mail corporativo (SPI-54), obrigatório e
 * único globalmente (ADR-32). O primeiro administrador (nome, e-mail e senha) é
 * criado na mesma transação (ADR-37); {@code adminEmail} deve pertencer a
 * {@code emailDomain}.
 */
public record RegisterTenantCommand(
    String slug,
    String name,
    String emailDomain,
    String adminName,
    String adminEmail,
    String adminPassword
) {
}
