package br.com.qsoft.governance.sharedkernel.query;

import java.util.List;

/**
 * Resultado paginado genérico do sharedkernel.
 * <p>
 * Convenção de paginação do ADR-28: {@code page}/{@code size} zero-based,
 * {@code size} default 20 e máximo 100 (o limite é aplicado pelo caso de uso).
 * Não depende de Spring/Spring Data para permanecer no sharedkernel (ADR-31).
 */
public record PageResult<T>(List<T> content, long totalElements, int page, int size) {

    public PageResult {
        content = content == null ? List.of() : List.copyOf(content);
        if (page < 0) {
            throw new IllegalArgumentException("page não pode ser negativo.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size deve ser maior que zero.");
        }
    }

    public int totalPages() {
        return (int) Math.ceil((double) totalElements / size);
    }
}
