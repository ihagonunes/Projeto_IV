package br.com.qsoft.governance.aigateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.aigateway.application.port.ModelRepository;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNameAlreadyExistsException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelNotFoundException;
import br.com.qsoft.governance.aigateway.domain.exception.ModelPriceRequiredException;
import br.com.qsoft.governance.aigateway.domain.model.Model;
import br.com.qsoft.governance.aigateway.domain.model.ModelStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Edição do catálogo (SPI-87/PR06–PR08): nome, nome externo e preços. */
class UpdateModelTest {

    private static final TenantId TENANT = TenantId.random();
    private static final UUID MODEL_ID = UUID.randomUUID();
    private static final UUID PROVIDER_ID = UUID.randomUUID();

    private final ModelRepository repository = mock(ModelRepository.class);
    private final UpdateModel useCase = new UpdateModel(repository);

    private Model model(ModelStatus status, BigDecimal input, BigDecimal output) {
        return Model.restore(MODEL_ID, TENANT, PROVIDER_ID, "gpt-4o", "gpt-4o-old", status,
            input, output, Instant.now(), Instant.now());
    }

    private UpdateModelCommand command(String name, String external, BigDecimal input,
                                       BigDecimal output) {
        return new UpdateModelCommand(TENANT, MODEL_ID, name, external, input, output);
    }

    @Test
    void rejectsNullCommandAndMissingName() {
        assertThatThrownBy(() -> useCase.update(null)).isInstanceOf(IllegalArgumentException.class);
        when(repository.findByTenantIdAndId(TENANT, MODEL_ID))
            .thenReturn(Optional.of(model(ModelStatus.AVAILABLE, BigDecimal.ONE, BigDecimal.ONE)));
        assertThatThrownBy(() -> useCase.update(command(null, null, BigDecimal.ONE, BigDecimal.ONE)))
            .hasMessageContaining("name");
        assertThatThrownBy(() -> useCase.update(command("  ", null, BigDecimal.ONE, BigDecimal.ONE)))
            .hasMessageContaining("name");
    }

    @Test
    void editaNomeExternoEPrecosMantendoStatusEProvider() {
        Model original = model(ModelStatus.AVAILABLE, new BigDecimal("0.005"),
            new BigDecimal("0.015"));
        when(repository.findByTenantIdAndId(TENANT, MODEL_ID)).thenReturn(Optional.of(original));
        when(repository.existsByTenantIdAndName(TENANT, "gpt-4o-novo")).thenReturn(false);
        when(repository.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Model updated = useCase.update(command("gpt-4o-novo", "gpt-4o-2024",
            new BigDecimal("0.010"), new BigDecimal("0.020")));

        assertThat(updated.name()).isEqualTo("gpt-4o-novo");
        assertThat(updated.externalName()).isEqualTo("gpt-4o-2024");
        assertThat(updated.inputPricePer1k()).isEqualByComparingTo("0.010");
        assertThat(updated.outputPricePer1k()).isEqualByComparingTo("0.020");
        assertThat(updated.status()).isEqualTo(ModelStatus.AVAILABLE);
        assertThat(updated.providerId()).isEqualTo(PROVIDER_ID);
        verify(repository).save(updated);
    }

    @Test
    void nomeDuplicadoRetornaConflitoSemSalvar() {
        Model original = model(ModelStatus.AVAILABLE, new BigDecimal("0.005"),
            new BigDecimal("0.015"));
        when(repository.findByTenantIdAndId(TENANT, MODEL_ID)).thenReturn(Optional.of(original));
        when(repository.existsByTenantIdAndName(TENANT, "duplicado")).thenReturn(true);

        assertThatThrownBy(() -> useCase.update(command("duplicado", null,
            new BigDecimal("0.005"), new BigDecimal("0.015"))))
            .isInstanceOf(ModelNameAlreadyExistsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void precoNegativoEhRejeitado() {
        Model original = model(ModelStatus.AVAILABLE, new BigDecimal("0.005"),
            new BigDecimal("0.015"));
        when(repository.findByTenantIdAndId(TENANT, MODEL_ID)).thenReturn(Optional.of(original));
        when(repository.existsByTenantIdAndName(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.update(command("gpt-4o", null,
            new BigDecimal("-0.001"), new BigDecimal("0.015"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negativo");
        verify(repository, never()).save(any());
    }

    @Test
    void modeloAvailableContinuaExigindoDoisPrecos() {
        Model original = model(ModelStatus.AVAILABLE, new BigDecimal("0.005"),
            new BigDecimal("0.015"));
        when(repository.findByTenantIdAndId(TENANT, MODEL_ID)).thenReturn(Optional.of(original));
        when(repository.existsByTenantIdAndName(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.update(command("gpt-4o", null, null,
            new BigDecimal("0.015"))))
            .isInstanceOf(ModelPriceRequiredException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void modeloDisablePermiteLimparPrecos() {
        Model original = model(ModelStatus.DISABLED, new BigDecimal("0.005"),
            new BigDecimal("0.015"));
        when(repository.findByTenantIdAndId(TENANT, MODEL_ID)).thenReturn(Optional.of(original));
        when(repository.existsByTenantIdAndName(any(), any())).thenReturn(false);
        when(repository.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Model updated = useCase.update(command("gpt-4o", null, null, null));

        assertThat(updated.inputPricePer1k()).isNull();
        assertThat(updated.outputPricePer1k()).isNull();
        assertThat(updated.status()).isEqualTo(ModelStatus.DISABLED);
    }

    @Test
    void modeloInexistenteRetornaNaoEncontrado() {
        when(repository.findByTenantIdAndId(TENANT, MODEL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(command("gpt-4o", null,
            BigDecimal.ONE, BigDecimal.ONE)))
            .isInstanceOf(ModelNotFoundException.class);
    }
}
