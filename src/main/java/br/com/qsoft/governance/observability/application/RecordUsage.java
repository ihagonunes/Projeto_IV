package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.observability.application.port.UsageRecordRepository;
import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste o UsageRecord a partir de {@link AIRequestCompleted} (SPI-71).
 * <p>
 * <b>Idempotente por requestId</b> (ADR-13): um evento repetido não gera um
 * segundo registro. É um handler <b>interno</b> — não há endpoint público
 * genérico de logs.
 */
@Service
public class RecordUsage {

    private final UsageRecordRepository usageRecordRepository;

    public RecordUsage(UsageRecordRepository usageRecordRepository) {
        this.usageRecordRepository = usageRecordRepository;
    }

    /** @return {@code true} se um novo registro foi gravado; {@code false} se já existia. */
    @Transactional
    public boolean record(AIRequestCompleted event) {
        Objects.requireNonNull(event, "event");
        if (usageRecordRepository.existsByRequestId(event.requestId())) {
            return false;
        }
        usageRecordRepository.save(UsageRecord.fromEvent(UUID.randomUUID(), event));
        return true;
    }
}
