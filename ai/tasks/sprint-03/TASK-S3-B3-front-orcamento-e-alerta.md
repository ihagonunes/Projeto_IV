# TASK-S3-B3 — Front: administração de orçamento e alerta no dashboard

## Metadados
- **Onda:** B · **Depende de:** B1, B2 e A9 (uso de `/me/permissoes`)
- **Jira:** SPI-121, SPI-124
- **Área:** front Express/EJS
- **Spec:** `SPEC-02` §5

## Critérios de aceitação
- [ ] Tela de orçamento no módulo `departamentos`:
  - [ ] seletor de mês;
  - [ ] tabela de deptos com o orçamento e o limiar;
  - [ ] edição inline;
  - [ ] histórico de 12 meses.
- [ ] A edição só aparece para quem tem `DEPARTMENT:MANAGE` (via `/me/permissoes`).
- [ ] Card "Orçamento do mês" no dashboard, com status por depto:
  - [ ] OK, ALERTA e EXCEDIDO com cor e texto (acessível; não depender só da cor);
  - [ ] "Sem orçamento" quando não houver valor (nunca 0).
- [ ] Os estados vazio e de erro tratam 403 e 5xx.
- [ ] O relatório traz a verificação manual com prints ou a descrição dos passos.

## Restrição
Não tocar no componente de chat (SPI-136).

## Checks
- `npm start`, com navegação manual
