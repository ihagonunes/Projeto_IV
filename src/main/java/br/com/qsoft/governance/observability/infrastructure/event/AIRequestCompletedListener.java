package br.com.qsoft.governance.observability.infrastructure.event;

import br.com.qsoft.governance.observability.application.RecordUsage;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consome {@link AIRequestCompleted} como <b>efeito secundário</b> da completion
 * (ADR-13/SPI-70).
 * <p>
 * SPI-74: uma falha do observability <b>não</b> invalida uma completion válida —
 * a exceção é contida e registrada de forma estruturada, <b>sem conteúdo
 * privado</b> (apenas o requestId e a mensagem técnica).
 */
@Component
public class AIRequestCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(AIRequestCompletedListener.class);

    private final RecordUsage recordUsage;

    public AIRequestCompletedListener(RecordUsage recordUsage) {
        this.recordUsage = recordUsage;
    }

    @EventListener
    public void on(AIRequestCompleted event) {
        try {
            recordUsage.record(event);
        } catch (RuntimeException ex) {
            log.warn("Falha ao registrar UsageRecord do request {}: {}",
                event.requestId(), ex.getMessage());
        }
    }
}
