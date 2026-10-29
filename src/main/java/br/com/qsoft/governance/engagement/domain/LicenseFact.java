package br.com.qsoft.governance.engagement.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Fato de licença de IA de um usuário (ADR-17). */
public record LicenseFact(
    UUID aiToolId,
    LocalDate assignedAt,
    LocalDate expiresAt,
    boolean active
) {
}
