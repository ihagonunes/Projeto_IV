package br.com.qsoft.governance.agents.domain;

import java.time.Duration;
import java.util.Objects;

/** Limites duros do MVP (ADR-45 §3). */
public record ExecutionLimits(int maxParallelism, Duration stepTimeout, Duration totalTimeout) {

    public ExecutionLimits {
        if (maxParallelism < 1 || maxParallelism > 8) {
            throw new IllegalArgumentException("maxParallelism deve estar entre 1 e 8.");
        }
        Objects.requireNonNull(stepTimeout, "stepTimeout é obrigatório.");
        Objects.requireNonNull(totalTimeout, "totalTimeout é obrigatório.");
        if (stepTimeout.isZero() || stepTimeout.isNegative()
            || totalTimeout.isZero() || totalTimeout.isNegative()) {
            throw new IllegalArgumentException("timeouts devem ser positivos.");
        }
    }

    public static ExecutionLimits defaults() {
        return new ExecutionLimits(2, Duration.ofSeconds(30), Duration.ofSeconds(120));
    }
}
