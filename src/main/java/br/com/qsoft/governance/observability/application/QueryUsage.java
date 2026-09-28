package br.com.qsoft.governance.observability.application;

import br.com.qsoft.governance.observability.application.port.UsageRecordRepository;
import br.com.qsoft.governance.observability.domain.UsageRecord;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Consulta operacional paginada de UsageRecord (SPI-73), com filtros por
 * departamento, usuário e período. O tenant vem SEMPRE do contexto autenticado
 * (ADR-03).
 */
@Service
public class QueryUsage {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final UsageRecordRepository usageRecordRepository;

    public QueryUsage(UsageRecordRepository usageRecordRepository) {
        this.usageRecordRepository = usageRecordRepository;
    }

    public PageResult<UsageRecord> query(QueryUsageCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        int page = Math.max(command.page(), 0);
        int size = command.size() <= 0 ? DEFAULT_SIZE : Math.min(command.size(), MAX_SIZE);

        return usageRecordRepository.search(new UsageRecordRepository.UsageRecordFilter(
            tenantId, command.departmentId(), command.userId(), command.from(), command.to()
        ), page, size);
    }
}
