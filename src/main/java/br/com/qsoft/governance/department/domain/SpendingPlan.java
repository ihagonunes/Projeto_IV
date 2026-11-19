package br.com.qsoft.governance.department.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Distribuição do orçamento do mês. Valores monetários em BRL. */
public record SpendingPlan(BigDecimal amount, List<DepartmentShare> departments,
                           List<Allocation> allocations, Map<String, BigDecimal> weights,
                           List<ModelRate> rates) {
    public record DepartmentShare(UUID departmentId, BigDecimal percentage) {}
    public record Allocation(UUID userId, UUID departmentId, BigDecimal limit) {}
    public record ModelRate(UUID modelId, String currency, BigDecimal exchangeRate, String reference) {}

    public SpendingPlan {
        money(amount);
        departments = List.copyOf(departments);
        allocations = List.copyOf(allocations);
        weights = Map.copyOf(weights);
        rates = List.copyOf(rates);
        if (departments.stream().map(DepartmentShare::departmentId).distinct().count() != departments.size()
                || allocations.stream().map(Allocation::userId).distinct().count() != allocations.size()
                || rates.stream().map(ModelRate::modelId).distinct().count() != rates.size()) {
            throw new IllegalArgumentException("Há departamentos, funcionários ou modelos repetidos.");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (DepartmentShare share : departments) {
            if (share.departmentId() == null || share.percentage() == null
                    || share.percentage().signum() < 0 || share.percentage().scale() > 4) {
                throw new IllegalArgumentException("Percentual de departamento inválido.");
            }
            total = total.add(share.percentage());
        }
        if (total.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Os percentuais ultrapassam 100%.");
        }
        for (Allocation allocation : allocations) {
            money(allocation.limit());
            if (allocation.userId() == null || allocation.departmentId() == null
                    || departments.stream().noneMatch(d -> d.departmentId().equals(allocation.departmentId()))) {
                throw new IllegalArgumentException("Atribuição sem departamento no orçamento.");
            }
        }
        for (DepartmentShare share : departments) {
            BigDecimal allocated = allocations.stream().filter(a -> a.departmentId().equals(share.departmentId()))
                .map(Allocation::limit).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (allocated.compareTo(shareAmount(amount, share.percentage())) > 0) {
                throw new IllegalArgumentException("Limites individuais ultrapassam a parcela do departamento.");
            }
        }
        if (weights.isEmpty() || weights.entrySet().stream().anyMatch(e ->
                !List.of("COLLABORATOR", "LEADER", "MANAGER", "EXECUTIVE").contains(e.getKey())
                || e.getValue() == null || e.getValue().signum() <= 0)) {
            throw new IllegalArgumentException("Pesos por cargo inválidos.");
        }
        for (ModelRate rate : rates) {
            if (rate.modelId() == null || rate.currency() == null || !rate.currency().matches("[A-Z]{3}")
                    || rate.exchangeRate() == null || rate.exchangeRate().signum() <= 0
                    || rate.exchangeRate().compareTo(new BigDecimal("99999999999999.99999999")) > 0
                    || rate.exchangeRate().scale() > 8 || rate.reference() == null
                    || rate.reference().isBlank() || rate.reference().length() > 200
                    || (rate.currency().equals("BRL") && rate.exchangeRate().compareTo(BigDecimal.ONE) != 0)) {
                throw new IllegalArgumentException("Moeda, taxa ou referência do modelo inválida.");
            }
            java.util.Currency.getInstance(rate.currency());
        }
    }

    public static BigDecimal shareAmount(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
    }

    private static void money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.scale() > 2
                || value.compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw new IllegalArgumentException("Valor deve ser não negativo e ter até duas casas decimais.");
        }
    }
}
