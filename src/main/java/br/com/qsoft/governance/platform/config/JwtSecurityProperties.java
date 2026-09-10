package br.com.qsoft.governance.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de assinatura/validação do JWT (app.security.jwt).
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtSecurityProperties {

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
