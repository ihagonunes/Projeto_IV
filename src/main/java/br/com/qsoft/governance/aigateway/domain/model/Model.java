package br.com.qsoft.governance.aigateway.domain.model;

import br.com.qsoft.governance.aigateway.domain.exception.ModelPriceRequiredException;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Modelo do catálogo do tenant. Referencia o Provider <b>por ID</b> (FK
 * intramódulo — ADR-09/ADR-29) e não conhece o agregado Provider.
 * <p>
 * Carrega o preço por 1.000 tokens (entrada/saída) e calcula o custo estimado
 * de uma requisição (ADR-13/SPI-72): o Gateway calcula o custo <b>uma vez</b> e
 * o envia em {@code AIRequestCompleted}; o observability não recalcula.
 */
public final class Model {

    private static final int NAME_MAX = 150;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final int COST_SCALE = 6;

    private final UUID id;
    private final TenantId tenantId;
    private final UUID providerId;
    private String name;
    private String externalName;
    private ModelStatus status;
    private BigDecimal inputPricePer1k;
    private BigDecimal outputPricePer1k;
    private final Instant createdAt;
    private Instant updatedAt;

    private Model(UUID id, TenantId tenantId, UUID providerId, String name, String externalName,
                  ModelStatus status, BigDecimal inputPricePer1k, BigDecimal outputPricePer1k,
                  Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.name = Objects.requireNonNull(name, "name");
        this.externalName = normalizeExternalName(externalName);
        this.status = Objects.requireNonNull(status, "status");
        this.inputPricePer1k = inputPricePer1k;
        this.outputPricePer1k = outputPricePer1k;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Model create(UUID id, TenantId tenantId, UUID providerId, String name,
                               String externalName, BigDecimal inputPricePer1k,
                               BigDecimal outputPricePer1k) {
        BigDecimal input = requirePrice(inputPricePer1k);
        BigDecimal output = requirePrice(outputPricePer1k);
        if (input == null || output == null) {
            throw new ModelPriceRequiredException();
        }
        Instant now = Instant.now();
        return new Model(id, tenantId, providerId, requireName(name), externalName,
            ModelStatus.AVAILABLE, input, output, now, now);
    }

    public static Model restore(UUID id, TenantId tenantId, UUID providerId, String name,
                                String externalName, ModelStatus status, BigDecimal inputPricePer1k,
                                BigDecimal outputPricePer1k, Instant createdAt, Instant updatedAt) {
        return new Model(id, tenantId, providerId, name, externalName, status, inputPricePer1k,
            outputPricePer1k, createdAt, updatedAt);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID providerId() {
        return providerId;
    }

    public String name() {
        return name;
    }

    /** Nome usado junto ao provider; {@code null} significa usar {@link #name()}. */
    public String externalName() {
        return externalName;
    }

    public ModelStatus status() {
        return status;
    }

    public BigDecimal inputPricePer1k() {
        return inputPricePer1k;
    }

    public BigDecimal outputPricePer1k() {
        return outputPricePer1k;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public boolean isAvailable() {
        return status == ModelStatus.AVAILABLE;
    }

    public Model rename(String newName) {
        this.name = requireName(newName);
        return touch();
    }

    public Model changePrices(BigDecimal newInputPrice, BigDecimal newOutputPrice) {
        this.inputPricePer1k = requirePrice(newInputPrice);
        this.outputPricePer1k = requirePrice(newOutputPrice);
        return touch();
    }

    /**
     * Atualiza nome, nome externo e preços em uma única operação (SPI-87).
     * Não altera status nem provider. Modelo AVAILABLE continua exigindo os dois
     * preços (S4-D12); preço nulo em DISABLED é permitido.
     */
    public Model updateDetails(String newName, String newExternalName,
                               BigDecimal newInputPrice, BigDecimal newOutputPrice) {
        String resolvedName = requireName(newName);
        String resolvedExternalName = normalizeExternalName(newExternalName);
        BigDecimal resolvedInput = requirePrice(newInputPrice);
        BigDecimal resolvedOutput = requirePrice(newOutputPrice);
        if (status == ModelStatus.AVAILABLE
            && (resolvedInput == null || resolvedOutput == null)) {
            throw new ModelPriceRequiredException();
        }
        this.name = resolvedName;
        this.externalName = resolvedExternalName;
        this.inputPricePer1k = resolvedInput;
        this.outputPricePer1k = resolvedOutput;
        return touch();
    }

    public Model makeAvailable() {
        if (inputPricePer1k == null || outputPricePer1k == null) {
            throw new ModelPriceRequiredException();
        }
        if (status != ModelStatus.AVAILABLE) {
            this.status = ModelStatus.AVAILABLE;
            touch();
        }
        return this;
    }

    public Model disable() {
        if (status != ModelStatus.DISABLED) {
            this.status = ModelStatus.DISABLED;
            touch();
        }
        return this;
    }

    /**
     * Custo estimado (ADR-13/SPI-72):
     * {@code (inputTokens/1000 * inputPrice) + (outputTokens/1000 * outputPrice)}.
     * Preço nulo é rejeitado; zero explícito é um preço válido (S4-D12).
     */
    public BigDecimal estimatedCost(long inputTokens, long outputTokens) {
        return estimateCost(inputPricePer1k, outputPricePer1k, inputTokens, outputTokens);
    }

    /** Cálculo do custo com preços fotografados (ADR-38); exige os dois preços. */
    public static BigDecimal estimateCost(BigDecimal inputPricePer1k, BigDecimal outputPricePer1k,
                                          long inputTokens, long outputTokens) {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("tokens não podem ser negativos.");
        }
        if (inputPricePer1k == null || outputPricePer1k == null) {
            throw new ModelPriceRequiredException();
        }
        return inputPricePer1k.multiply(BigDecimal.valueOf(inputTokens))
            .add(outputPricePer1k.multiply(BigDecimal.valueOf(outputTokens)))
            .divide(THOUSAND, COST_SCALE, RoundingMode.HALF_UP);
    }

    private Model touch() {
        this.updatedAt = Instant.now();
        return this;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException("name deve ter no máximo " + NAME_MAX + " caracteres.");
        }
        return trimmed;
    }

    private static String normalizeExternalName(String externalName) {
        if (externalName == null || externalName.isBlank()) {
            return null;
        }
        String trimmed = externalName.trim();
        if (trimmed.length() > NAME_MAX) {
            throw new IllegalArgumentException(
                "externalName deve ter no máximo " + NAME_MAX + " caracteres.");
        }
        return trimmed;
    }

    private static BigDecimal requirePrice(BigDecimal price) {
        if (price == null) {
            return null;
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("preço não pode ser negativo.");
        }
        return price;
    }
}
