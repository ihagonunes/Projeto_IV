package br.com.qsoft.governance.department.infrastructure.persistence;

import br.com.qsoft.governance.department.application.port.MonthlyBudgetRepository;
import br.com.qsoft.governance.department.domain.MonthlyBudget;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter do orçamento mensal; toda leitura filtra tenant + departamento. */
@Component
public class MonthlyBudgetRepositoryAdapter implements MonthlyBudgetRepository {

    private final MonthlyBudgetJpaRepository jpaRepository;
    private final MonthlyBudgetJpaMapper mapper = new MonthlyBudgetJpaMapper();

    public MonthlyBudgetRepositoryAdapter(MonthlyBudgetJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MonthlyBudget> findByTenantIdAndDepartmentIdAndMonth(
            TenantId tenantId, UUID departmentId, YearMonth month) {
        return jpaRepository.findByTenantIdAndDepartmentIdAndMonth(
                tenantId.value(), departmentId, month.atDay(1))
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyBudget> findRange(TenantId tenantId, UUID departmentId,
                                         YearMonth from, YearMonth to) {
        return jpaRepository.findByTenantIdAndDepartmentIdAndMonthBetweenOrderByMonthAsc(
                tenantId.value(), departmentId, from.atDay(1), to.atDay(1)).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public MonthlyBudget save(MonthlyBudget budget) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(budget)));
    }
}
