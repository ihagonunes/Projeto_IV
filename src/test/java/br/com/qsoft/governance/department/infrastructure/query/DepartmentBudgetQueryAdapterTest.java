package br.com.qsoft.governance.department.infrastructure.query;

import br.com.qsoft.governance.department.application.port.DepartmentRepository;
import br.com.qsoft.governance.department.domain.Department;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentBudgetQueryAdapterTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentBudgetQueryAdapter adapter;

    private final TenantId tenantId = TenantId.random();
    private final UUID departmentId = UUID.randomUUID();

    @Test
    void deve_retornar_orcamento_mensal_quando_departamento_existir_com_orcamento() {
        BigDecimal budget = new BigDecimal("5000.00");
        Department department = Department.create(departmentId, tenantId, "Engenharia", "Desc", null, budget);

        when(departmentRepository.findByTenantIdAndId(tenantId, departmentId)).thenReturn(Optional.of(department));

        Optional<BigDecimal> result = adapter.monthlyBudgetOf(tenantId, departmentId);

        assertThat(result).contains(budget);
        verify(departmentRepository).findByTenantIdAndId(tenantId, departmentId);
    }

    @Test
    void deve_retornar_vazio_quando_departamento_existir_mas_orcamento_for_nulo() {
        Department department = Department.create(departmentId, tenantId, "Engenharia", "Desc", null, null);

        when(departmentRepository.findByTenantIdAndId(tenantId, departmentId)).thenReturn(Optional.of(department));

        Optional<BigDecimal> result = adapter.monthlyBudgetOf(tenantId, departmentId);

        assertThat(result).isEmpty();
        verify(departmentRepository).findByTenantIdAndId(tenantId, departmentId);
    }

    @Test
    void deve_retornar_vazio_quando_departamento_nao_for_encontrado() {
        when(departmentRepository.findByTenantIdAndId(tenantId, departmentId)).thenReturn(Optional.empty());

        Optional<BigDecimal> result = adapter.monthlyBudgetOf(tenantId, departmentId);

        assertThat(result).isEmpty();
        verify(departmentRepository).findByTenantIdAndId(tenantId, departmentId);
    }
}
