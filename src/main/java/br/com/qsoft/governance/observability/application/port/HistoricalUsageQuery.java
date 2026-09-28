package br.com.qsoft.governance.observability.application.port;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.LocalDate;

/**
 * Porta de leitura (read model) do <b>histórico analítico</b> (SPI-75 / ADR-26).
 * <p>
 * O histórico vive no schema {@code historical} (dataset sintético da QSOFT),
 * <b>sem FK</b> para as tabelas operacionais. O acesso é somente leitura e
 * sempre <b>tenant-scoped</b>: o loader reescreve {@code company_id} para o
 * {@code tenant_id} da plataforma, e toda consulta nativa filtra por ele
 * (ADR-03).
 */
public interface HistoricalUsageQuery {

    /** Agregado de uso por período e (opcionalmente) departamento. */
    HistoricalUsageSummary summarize(HistoricalUsageFilter filter);

    /** Filtros do histórico. {@code from}/{@code to} são datas (ISO) do dataset. */
    record HistoricalUsageFilter(
        TenantId tenantId,
        String departmentId,
        LocalDate from,
        LocalDate to
    ) {
    }

    /** Agregado: requisições, tokens e latência média. */
    record HistoricalUsageSummary(
        long requests,
        long inputTokens,
        long outputTokens,
        double averageLatencyMs
    ) {
    }
}
