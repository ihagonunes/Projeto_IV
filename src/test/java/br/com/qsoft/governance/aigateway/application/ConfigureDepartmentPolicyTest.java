package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.exception.DepartmentNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
import br.com.qsoft.governance.department.api.command.ConfigureDepartmentModelPolicy;
import br.com.qsoft.governance.department.api.model.DepartmentInfo;
import br.com.qsoft.governance.department.api.query.OrganizationQuery;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Testes unitários de {@link ConfigureDepartmentPolicy}.
 * <p>
 * Usa Mockito pois {@link ConfigureDepartmentModelPolicy} é um contrato
 * cross-module — não pode ser instanciado diretamente sem Spring.
 */
@ExtendWith(MockitoExtension.class)
class ConfigureDepartmentPolicyTest {

    @Mock ModelRepository               modelRepository;
    @Mock OrganizationQuery             organizationQuery;
    @Mock ConfigureDepartmentModelPolicy configurePolicy;

    @InjectMocks ConfigureDepartmentPolicy useCase;

    private final TenantId tenantId    = TenantId.random();
    private final UUID     deptId      = UUID.randomUUID();
    private final UUID     modelId1    = UUID.randomUUID();
    private final UUID     modelId2    = UUID.randomUUID();

    // ── helpers ───────────────────────────────────────────────────────────────

    private void deptExists() {
        when(organizationQuery.findById(tenantId, deptId))
            .thenReturn(Optional.of(new DepartmentInfo(deptId, tenantId.value(), "TI", null)));
    }

    private Model fakeModel(UUID id) {
        return Model.restore(id, tenantId, UUID.randomUUID(), "m-" + id,
            ModelStatus.AVAILABLE, null, null, Instant.now(), Instant.now());
    }

    // ── testes ────────────────────────────────────────────────────────────────

    @Test
    void deve_configurarPolitica_quando_listaDeModelosValida() {
        deptExists();
        Set<UUID> allowed = Set.of(modelId1, modelId2);
        when(modelRepository.findByTenantIdAndIdIn(tenantId, allowed))
            .thenReturn(List.of(fakeModel(modelId1), fakeModel(modelId2)));

        assertThatCode(() ->
            useCase.configure(tenantId, deptId, allowed, modelId1))
            .doesNotThrowAnyException();

        verify(configurePolicy).configure(tenantId, deptId, allowed, modelId1);
    }

    @Test
    void deve_configurarPolitica_quando_listaDeModelosNula() {
        deptExists();

        assertThatCode(() ->
            useCase.configure(tenantId, deptId, null, null))
            .doesNotThrowAnyException();

        // Nenhum model foi validado (lista vazia)
        verifyNoInteractions(modelRepository);
        verify(configurePolicy).configure(tenantId, deptId, Set.of(), null);
    }

    @Test
    void deve_configurarPolitica_quando_listaDeModelosVazia() {
        deptExists();

        assertThatCode(() ->
            useCase.configure(tenantId, deptId, List.of(), null))
            .doesNotThrowAnyException();

        verifyNoInteractions(modelRepository);
        verify(configurePolicy).configure(tenantId, deptId, Set.of(), null);
    }

    @Test
    void deve_lancarExcecao_quando_departamentoNaoEncontrado() {
        when(organizationQuery.findById(tenantId, deptId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            useCase.configure(tenantId, deptId, List.of(modelId1), null))
            .isInstanceOf(DepartmentNotFoundException.class)
            .hasMessageContaining(deptId.toString());

        verifyNoInteractions(modelRepository, configurePolicy);
    }

    @Test
    void deve_lancarExcecao_quando_modelNaoEncontradoNaLista() {
        deptExists();
        Set<UUID> requested = Set.of(modelId1, modelId2);
        // Só um dos dois foi encontrado
        when(modelRepository.findByTenantIdAndIdIn(tenantId, requested))
            .thenReturn(List.of(fakeModel(modelId1)));

        assertThatThrownBy(() ->
            useCase.configure(tenantId, deptId, requested, null))
            .isInstanceOf(ModelNotFoundException.class);

        verifyNoInteractions(configurePolicy);
    }

    @Test
    void deve_configurarDefaultModel_quando_fornecido() {
        deptExists();
        Set<UUID> allowed = Set.of(modelId1);
        when(modelRepository.findByTenantIdAndIdIn(tenantId, allowed))
            .thenReturn(List.of(fakeModel(modelId1)));

        useCase.configure(tenantId, deptId, allowed, modelId1);

        verify(configurePolicy).configure(tenantId, deptId, allowed, modelId1);
    }
}
