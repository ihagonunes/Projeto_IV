package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentTenantTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private GetCurrentTenant getCurrentTenant;

    private final TenantId tenantId = TenantId.random();
    private final Tenant tenant = Tenant.create(tenantId, "qsoft", "QSOFT Demo", "qsoft.com");

    @Test
    void deve_retornar_tenant_atual_quando_existir() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        Tenant result = getCurrentTenant.current(tenantId);

        assertThat(result).isEqualTo(tenant);
        verify(tenantRepository).findById(tenantId);
    }

    @Test
    void deve_lancar_excecao_quando_tenant_nao_for_encontrado() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getCurrentTenant.current(tenantId))
            .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void deve_validar_tenant_id_obrigatorio() {
        assertThatThrownBy(() -> getCurrentTenant.current(null))
            .isInstanceOf(NullPointerException.class);
    }
}
