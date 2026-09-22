package br.com.qsoft.governance.iam.application;

/**
 * Dados de entrada para aceitar um convite e definir a senha.
 */
public record AcceptInviteCommand(String token, String name, String password) {
}
