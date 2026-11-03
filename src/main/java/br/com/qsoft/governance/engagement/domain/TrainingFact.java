package br.com.qsoft.governance.engagement.domain;

import java.time.LocalDate;

/** Fato de treinamento concluído (ADR-17). */
public record TrainingFact(String course, LocalDate completedAt) {
}
