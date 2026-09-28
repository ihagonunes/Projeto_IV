-- V012 - Módulo aigateway: preço do modelo (para o cálculo de custo — SPI-72).
-- O Gateway calcula o estimatedCost UMA vez, a partir dos tokens e do preço do
-- modelo (ADR-13). Preços por 1.000 tokens; nulos = modelo sem preço definido.

ALTER TABLE aigateway_models ADD COLUMN input_price_per_1k  NUMERIC(12,6);
ALTER TABLE aigateway_models ADD COLUMN output_price_per_1k NUMERIC(12,6);

COMMENT ON COLUMN aigateway_models.input_price_per_1k IS
    'Preço por 1.000 tokens de entrada (para o cálculo de custo no Gateway — ADR-13).';
COMMENT ON COLUMN aigateway_models.output_price_per_1k IS
    'Preço por 1.000 tokens de saída (para o cálculo de custo no Gateway — ADR-13).';
