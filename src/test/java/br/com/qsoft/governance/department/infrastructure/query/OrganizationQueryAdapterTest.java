package br.com.qsoft.governance.department.infrastructure.query;

import br.com.qsoft.governance.department.api.model.DepartmentInfo;
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
class OrganizationQueryAdapterTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private OrganizationQueryAdapter adapter;

    private final TenantId tenantId = TenantId.random();
    private final UUID departmentId = UUID.randomUUID();

    @Test
    void deve_retornar_department_info_quando_departamento_existir() {
        UUID leaderId = UUID.randomUUID();
        Department department = Department.create(departmentId, tenantId, "Tecnologia", "TI", leaderId, new BigDecimal("10000.00"));

        when(departmentRepository.findByTenantIdAndId(tenantId, departmentId))
            .thenReturn(Optional.of(department));

        Optional<DepartmentInfo> result = adapter.findById(tenantId, departmentId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(departmentId);
        assertThat(result.get().tenantId()).isEqualTo(tenantId.value());
        assertThat(result.get().name()).isEqualTo("Tecnologia");
        assertThat(result.get().leaderUserId()).isEqualTo(leaderId);

        verify(departmentRepository).findByTenantIdAndId(tenantId, departmentId);
    }

    @Test
    void deve_retornar_vazio_quando_departamento_nao_for_encontrado() {
        when(departmentRepository.findByTenantIdAndId(tenantId, departmentId))
            .thenReturn(Optional.empty());

        Optional<DepartmentInfo> result = adapter.findById(tenantId, departmentId);

        assertThat(result).isEmpty();
        verify(departmentRepository).findByTenantIdAndId(tenantId, departmentId);
    }
}
