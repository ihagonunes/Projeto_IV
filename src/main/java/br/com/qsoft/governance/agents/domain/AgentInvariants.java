package br.com.qsoft.governance.agents.domain;

import java.util.Objects;

/**
 * Validações estruturais compartilhadas pelo modelo de agentes (ADR-44).
 * Mantém o domínio independente de Spring/JPA e sem regra de negócio de fluxo.
 */
final class AgentInvariants {

    private AgentInvariants() {
    }

    static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                field + " excede " + maxLength + " caracteres.");
        }
        return normalized;
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }

    static String requireJson(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value;
    }

    static int requirePositive(int value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " deve ser maior que zero.");
        }
        return value;
    }

    static <T> T requireNonNull(T value, String field) {
        return Objects.requireNonNull(value, field + " é obrigatório.");
    }
}
