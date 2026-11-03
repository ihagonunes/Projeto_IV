package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.department.api.model.DepartmentInfo;
import br.com.qsoft.governance.department.api.model.ModelPolicyInfo;
import br.com.qsoft.governance.department.api.query.DepartmentModelPolicyQuery;
import br.com.qsoft.governance.department.api.query.OrganizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Leitura da política por departamento (SPI-87/PR06). */
class ReadDepartmentModelPolicyTest {

    private static final TenantId TENANT = TenantId.random();
    private static final UUID DEPARTMENT = UUID.randomUUID();

    private final OrganizationQuery organizationQuery = mock(OrganizationQuery.class);
    private final DepartmentModelPolicyQuery policyQuery = mock(DepartmentModelPolicyQuery.class);
    private final ReadDepartmentModelPolicy useCase =
        new ReadDepartmentModelPolicy(organizationQuery, policyQuery);

    private void givenDepartment() {
        when(organizationQuery.findById(TENANT, DEPARTMENT)).thenReturn(Optional.of(
            new DepartmentInfo(DEPARTMENT, TENANT.value(), "Marketing", null)));
    }

    @Test
    void semPoliticaDevolveFormatoVazio() {
        givenDepartment();
        when(policyQuery.findByDepartmentId(TENANT, DEPARTMENT)).thenReturn(Optional.empty());

        ModelPolicyInfo policy = useCase.read(TENANT, DEPARTMENT);

        assertThat(policy.departmentId()).isEqualTo(DEPARTMENT);
        assertThat(policy.defaultModelId()).isNull();
        assertThat(policy.allowedModelIds()).isEmpty();
    }

    @Test
    void lePoliticaPersistida() {
        UUID modelId = UUID.randomUUID();
        givenDepartment();
        when(policyQuery.findByDepartmentId(TENANT, DEPARTMENT)).thenReturn(Optional.of(
            new ModelPolicyInfo(DEPARTMENT, Set.of(modelId), modelId)));

        ModelPolicyInfo policy = useCase.read(TENANT, DEPARTMENT);

        assertThat(policy.allowedModelIds()).containsExactly(modelId);
        assertThat(policy.defaultModelId()).isEqualTo(modelId);
    }

    @Test
    void departamentoInexistenteNoTenantRetorna404() {
        when(organizationQuery.findById(TENANT, DEPARTMENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.read(TENANT, DEPARTMENT))
            .isInstanceOf(DepartmentNotFoundException.class);
    }
}
