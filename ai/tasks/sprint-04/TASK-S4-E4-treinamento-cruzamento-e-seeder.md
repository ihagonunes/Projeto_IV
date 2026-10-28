# TASK-S4-E4 — Cruzamento treinamento × uso (4 quadrantes) e seeder de demonstração

## Metadados
- **Onda:** E · **Ordem:** 4 · **Depende de:** E3
- **Jira:** **SPI-146**; seeder da **SPI-145**
- **Módulo:** `engagement`
- **Spec:** `SPEC-06` §5.2, §6

## Critérios de aceitação
- [ ] `GET /api/v1/engajamento/treinamentos/cruzamento?fonte=&ate=&departmentId=`:
  - [ ] USAGE:READ, BY_TARGET;
  - [ ] lista nominal só para quem tem `USER:READ` cobrindo cada usuário (decisão S4-D5);
  - [ ] no máximo 200 por quadrante, com `truncado`.
- [ ] Nunca expor nota, salário ou conteúdo.
- [ ] `EngagementDemoSeeder`:
  - [ ] idempotente;
  - [ ] só sob `app.bootstrap.demo-data=true`;
  - [ ] popula os 4 quadrantes e deixa usuários sem licença.
- [ ] Testes:
  - [ ] unit da lógica dos quadrantes (critério da SPI-146);
  - [ ] IT EN08;
  - [ ] IT do seeder (rodar duas vezes não duplica).

## Checks
- `./scripts/check`
- `./scripts/check-integration`
