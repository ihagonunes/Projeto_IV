# TASK-S3-D2 — Recibo de execução na UI do chat (coordenação com a Lyn)

## Metadados
- **Onda:** D · **Depende de:** D1; o componente SPI-136 (Lyn)
- **Jira:** SPI-134, SPI-136
- **Responsável pela UI:** Lyn (SPI-136). **Esta tarefa não deve ser executada por outro agente sem
  autorização explícita do responsável.**

## Objetivo
Ligar o componente de chat aos endpoints da SPEC-04 e exibir o recibo de cada resposta do
assistente:
- modelo, provider, tokens, custo e latência;
- "custo indisponível" quando `statusCusto` ≠ KNOWN, nunca 0.

Quando E2 existir, o mesmo componente pode alternar para o agente de políticas
(`POST /api/v1/agente-politicas/perguntas`) e exibir as fontes.

## Critérios de aceitação
- [ ] Contrato da SPEC-04 §3 consumido sem adaptação no backend.
- [ ] O recibo aparece em todas as respostas.
- [ ] O modo "Agente de políticas" mostra as fontes (título e versão) e as ferramentas usadas.
- [ ] Os erros 403, 404, 409, 502, 503 e 504 têm mensagens próprias.

## Checks
- Verificação manual com o provider `STUB` e, se aprovado, com o provider real
