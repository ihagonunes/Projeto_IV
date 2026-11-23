-- V031 - Decisão 4-B (SPI-180): o conteúdo final da execução não é persistido.
-- O resultado existe apenas na resposta síncrona do POST de execução; GET/replay
-- devolvem status, etapas e recibos, sem prompt, entrada ou saída.

ALTER TABLE agents_flow_executions DROP COLUMN result_text;
