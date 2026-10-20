package br.com.qsoft.governance.observability.infrastructure.event;

import br.com.qsoft.governance.observability.application.RecordUsage;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AIRequestCompletedListenerTest {

    @Mock
    private RecordUsage recordUsage;

    @InjectMocks
    private AIRequestCompletedListener listener;

    @Test
    void deve_registrar_uso_ao_receber_evento_ai_request_completed() {
        AIRequestCompleted event = new AIRequestCompleted(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            100L,
            200L,
            new BigDecimal("0.002000"),
            150L,
            "SUCCESS"
        );

        listener.on(event);

        verify(recordUsage).record(event);
    }

    @Test
    void deve_propagar_excecao_para_o_publicador_agendar_reentrega() {
        AIRequestCompleted event = new AIRequestCompleted(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            100L,
            200L,
            new BigDecimal("0.002000"),
            150L,
            "SUCCESS"
        );

        doThrow(new RuntimeException("Database error")).when(recordUsage).record(event);

        assertThatThrownBy(() -> listener.on(event))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database error");
        verify(recordUsage).record(event);
    }
}
