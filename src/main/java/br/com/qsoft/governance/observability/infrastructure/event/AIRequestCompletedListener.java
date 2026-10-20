package br.com.qsoft.governance.observability.infrastructure.event;

import br.com.qsoft.governance.observability.application.RecordUsage;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consome {@link AIRequestCompleted} como <b>efeito secundário</b> da completion
 * (ADR-13/ADR-38).
 * <p>
 * A exceção <b>não</b> é contida aqui: o publicador do Gateway
 * ({@code UsageDeliveryPublisher}) a captura e agenda a reentrega, sem
 * invalidar a resposta ao cliente. A gravação é idempotente por
 * {@code request_id} ({@code ON CONFLICT DO NOTHING}).
 */
@Component
public class AIRequestCompletedListener {

    private final RecordUsage recordUsage;

    public AIRequestCompletedListener(RecordUsage recordUsage) {
        this.recordUsage = recordUsage;
    }

    @EventListener
    public void on(AIRequestCompleted event) {
        recordUsage.record(event);
    }
}
