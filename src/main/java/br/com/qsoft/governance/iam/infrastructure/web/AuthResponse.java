package br.com.qsoft.governance.iam.infrastructure.web;

/**
 * Resposta do login com o token JWT e o tipo de autenticação.
 */
public record AuthResponse(String token, String tokenType) {
}
