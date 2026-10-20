package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.observability.application.port.UsageRecordRepository;
import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.event.AIRequestCompleted;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Idempotência do consumo de AIRequestCompleted (SPI-71 / ADR-13). */
class RecordUsageTest {

    private final Map<UUID, UsageRecord> recordsByRequest = new LinkedHashMap<>();

    private final UsageRecordRepository repository = new UsageRecordRepository() {
        @Override
        public UsageRecord save(UsageRecord record) {
            recordsByRequest.put(record.requestId(), record);
            return record;
        }

        @Override
        public boolean insertIfAbsent(UsageRecord record) {
            if (recordsByRequest.containsKey(record.requestId())) {
                return false;
            }
            recordsByRequest.put(record.requestId(), record);
            return true;
        }

        @Override
        public boolean existsByRequestId(UUID requestId) {
            return recordsByRequest.containsKey(requestId);
        }

        @Override
        public PageResult<UsageRecord> search(UsageRecordFilter filter, int page, int size) {
            return new PageResult<>(java.util.List.copyOf(recordsByRequest.values()), recordsByRequest.size(), page, size);
        }
    };

    private final RecordUsage useCase = new RecordUsage(repository);

    private AIRequestCompleted event(UUID requestId) {
        return new AIRequestCompleted(requestId, UUID.randomUUID(), null, null,
            UUID.randomUUID(), UUID.randomUUID(), 100, 200, new BigDecimal("0.07"), 120, "COMPLETED");
    }

    @Test
    void shouldRecordOnceAndBeIdempotentByRequestId() {
        UUID requestId = UUID.randomUUID();

        assertThat(useCase.record(event(requestId))).isTrue();
        assertThat(useCase.record(event(requestId))).isFalse();

        assertThat(recordsByRequest).hasSize(1);
    }

    @Test
    void shouldRecordDistinctRequests() {
        assertThat(useCase.record(event(UUID.randomUUID()))).isTrue();
        assertThat(useCase.record(event(UUID.randomUUID()))).isTrue();

        assertThat(recordsByRequest).hasSize(2);
    }

    @Test
    void shouldKeepTenantFromEvent() {
        UUID requestId = UUID.randomUUID();
        AIRequestCompleted event = event(requestId);

        useCase.record(event);

        assertThat(recordsByRequest.get(requestId).tenantId()).isEqualTo(TenantId.of(event.tenantId()));
        assertThat(recordsByRequest.get(requestId).estimatedCost()).isEqualByComparingTo("0.07");
    }
}
