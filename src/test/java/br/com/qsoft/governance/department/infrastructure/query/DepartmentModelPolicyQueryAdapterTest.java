package br.com.qsoft.governance.department.infrastructure.query;

import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import br.com.qsoft.governance.department.application.port.ModelPolicyRepository;
import br.com.qsoft.governance.department.domain.modelpolicy.ModelPolicy;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import java.util.Set;
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
class DepartmentModelPolicyQueryAdapterTest {

    @Mock
    private ModelPolicyRepository modelPolicyRepository;

    @InjectMocks
    private DepartmentModelPolicyQueryAdapter adapter;

    private final TenantId tenantId = TenantId.random();
    private final UUID departmentId = UUID.randomUUID();

    @Test
    void deve_retornar_model_policy_info_quando_policy_existir() {
        UUID modelId = UUID.randomUUID();
        ModelPolicy policy = ModelPolicy.create(departmentId, Set.of(modelId), modelId);

        when(modelPolicyRepository.findByTenantIdAndDepartmentId(tenantId, departmentId))
            .thenReturn(Optional.of(policy));

        Optional<ModelPolicyInfo> result = adapter.findByDepartmentId(tenantId, departmentId);

        assertThat(result).isPresent();
        assertThat(result.get().departmentId()).isEqualTo(departmentId);
        assertThat(result.get().allowedModelIds()).containsExactly(modelId);
        assertThat(result.get().defaultModelId()).isEqualTo(modelId);

        verify(modelPolicyRepository).findByTenantIdAndDepartmentId(tenantId, departmentId);
    }

    @Test
    void deve_retornar_vazio_quando_policy_nao_for_encontrada() {
        when(modelPolicyRepository.findByTenantIdAndDepartmentId(tenantId, departmentId))
            .thenReturn(Optional.empty());

        Optional<ModelPolicyInfo> result = adapter.findByDepartmentId(tenantId, departmentId);

        assertThat(result).isEmpty();
        verify(modelPolicyRepository).findByTenantIdAndDepartmentId(tenantId, departmentId);
    }
}
