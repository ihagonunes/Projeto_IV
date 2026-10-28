# TASK-S4-E6 — Painel de adoção e treinamento no dashboard do gestor

## Metadados
- **Onda:** E · **Ordem:** 6 · **Depende de:** E3, E4 (E5 para o card de Credits)
- **Jira:** **SPI-144**
- **Área:** front do dashboard (`modules/dashboard`, `public/js/pages/dashboard*.js`)
- **Coordenação:** os dashboards por perfil foram construídos na branch `b-evelyn`. Combinar com a
  responsável antes de alterar os mesmos arquivos.

## Critérios de aceitação
- [ ] Card "Adoção (30 dias)": taxa, ativos/elegíveis, licenças ociosas e a data de corte.
- [ ] Gráfico de rosca ativos × inativos.
- [ ] O seletor de `fonte` (Operacional/Histórico) segue o padrão do dashboard; as fontes nunca são
      somadas.
- [ ] Card dos 4 quadrantes (contagens). A lista nominal só aparece quando a API a devolve
      (`listaNominal=true`).
- [ ] Card de Credits:
  - [ ] colaborador vê o próprio score e os componentes;
  - [ ] gestor vê a distribuição da equipe, **sem ranking** (Sprint 5).
- [ ] Atualiza conforme o filtro de departamento e o período (`ate`).
- [ ] Estados vazio, 403 e `INSUFFICIENT_DATA` explicados. Nunca mostrar 0 no lugar de "sem dado".
- [ ] Cor não é a única pista; responsivo.
- [ ] `node --test` cobrindo o mapeamento da resposta para os cards.

## Restrição
Não tocar no componente de chat.

## Checks
- `node --test src/test/frontend/*.test.js`
- Verificação manual por perfil (colaborador, gestor e executivo)
