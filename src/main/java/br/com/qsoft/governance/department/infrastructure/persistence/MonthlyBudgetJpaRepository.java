package br.com.qsoft.governance.department.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBudgetJpaRepository
    extends JpaRepository<MonthlyBudgetJpaEntity, UUID> {

    Optional<MonthlyBudgetJpaEntity> findByTenantIdAndDepartmentIdAndMonth(
        UUID tenantId, UUID departmentId, LocalDate month);

    List<MonthlyBudgetJpaEntity> findByTenantIdAndDepartmentIdAndMonthBetweenOrderByMonthAsc(
        UUID tenantId, UUID departmentId, LocalDate from, LocalDate to);
}
