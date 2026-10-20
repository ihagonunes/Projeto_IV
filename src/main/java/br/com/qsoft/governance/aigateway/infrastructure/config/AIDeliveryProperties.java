package br.com.qsoft.governance.aigateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração operacional da entrega durável de uso (ADR-38).
 * <p>
 * {@code enabled=false} mantém o bean do job disponível para acionamento manual
 * nos testes, mas impede a execução agendada.
 */
@Component
@ConfigurationProperties(prefix = "app.ai.delivery")
public class AIDeliveryProperties {

    private boolean enabled = true;
    private long intervalMs = 30000;
    private int maxAttempts = 8;
    private long inflightGraceMs = 90000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInflightGraceMs() {
        return inflightGraceMs;
    }

    public void setInflightGraceMs(long inflightGraceMs) {
        this.inflightGraceMs = inflightGraceMs;
    }
}
