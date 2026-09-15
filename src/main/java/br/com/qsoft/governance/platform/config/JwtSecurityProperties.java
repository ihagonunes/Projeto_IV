package br.com.qsoft.governance.platform.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades de assinatura/validação do JWT (app.security.jwt).
 * O segredo é OBRIGATÓRIO e validado no boot (nunca use um default fraco).
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtSecurityProperties {

    @NotBlank(message = "JWT_SECRET é obrigatório (defina a variável de ambiente JWT_SECRET).")
    @Size(min = 32, message = "JWT_SECRET deve ter ao menos 32 caracteres.")
    private String secret;

    private long ttlMinutes = 30;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }
}
