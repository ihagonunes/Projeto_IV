package br.com.qsoft.governance.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita o agendamento de tarefas de plataforma (ADR-38). O job de entrega
 * de uso é desligável por {@code app.ai.delivery.enabled} e é acionado
 * manualmente nos testes de integração.
 */
@Configuration
@EnableScheduling
public class AiDeliverySchedulingConfig {
}
