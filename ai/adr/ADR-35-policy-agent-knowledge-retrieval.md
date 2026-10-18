# ADR-35 — Agente de políticas: recuperação de conhecimento e ferramentas

## Status

ACCEPTED (2026-10-18) — **opção A** (busca nativa do PostgreSQL, sem dependência nova).

## Contexto

As tarefas SPI-127 a 135 pedem um agente que responda dúvidas sobre as políticas internas da
empresa, com isolamento por tenant e departamento e com chamada de ferramentas. Já existem regras
que limitam o desenho:

- `ai/project.md` declara RAG e pgvector **não obrigatórios**;
- `ai/dependencies.md` lista Spring AI e pgvector em "não adicionar agora";
- ADR-14 diz que a implementação do `KnowledgeAccessService` é livre, desde que respeite tenant e
  escopo;
- ADR-15 exige autorização antes do retrieval e do LLM;
- a imagem do PostgreSQL do compose (`postgres:18.6-alpine`) não traz pgvector.

## Decisão (opção A — recomendada)

1. **Recuperação textual nativa do PostgreSQL**, sem extensão e sem dependência nova:
   - `tsvector` gerado (configuração `portuguese`) com índice GIN;
   - ranqueamento por `ts_rank`.
   Isso cumpre o ADR-14 e funciona com a imagem atual.
2. **Filtro rígido antes da busca (ADR-15).** A query SQL filtra explicitamente:
   - `tenant_id` do contexto autenticado;
   - versão `ACTIVE`;
   - documento do tenant inteiro **ou** com escopo em algum departamento do usuário.
   O LLM nunca vê trecho fora desse filtro e nunca decide autorização.
3. **Geração.** Usa `AICompletionService` (ADR-38). O prompt leva só os trechos autorizados e a
   resposta cita as fontes (documento e versão).
4. **Ferramentas.** São determinísticas e ficam numa *allowlist* no `agentpolicies`:
   - `buscar_politicas(consulta)`;
   - `modelo_permitido()`.
   Quando o provider suporta *tools* da API compatível com OpenAI, o agente as expõe. Cada chamada
   é executada pelo backend com o contexto do usuário, no máximo 2 rodadas. Sem suporte a *tools*,
   o fluxo cai no "recuperar antes de gerar".
5. **Execução registrada só com metadados** (`agentpolicies_executions`): `request_id`, status,
   documentos usados e ferramentas chamadas. Nenhum texto de pergunta ou resposta é persistido fora
   do chat do próprio usuário.
6. **Documentos e ciclo de vida (ADR-25).** Na Sprint 3, os documentos entram por *seeder* de
   demonstração (SPI-129), já `ACTIVE`. A administração completa de documentos fica fora.
   `TENANT_ADMIN` ganha `KNOWLEDGE_DOCUMENT:MANAGE`.

## Alternativas consideradas

- **Opção B — Spring AI + pgvector + embeddings.** Exige duas dependências novas, a troca da imagem
  do PostgreSQL (`pgvector/pgvector`) e um provider de embeddings. Melhor recuperação semântica, mas
  aumenta o custo e o risco num prazo de 5 dias. Só com aprovação explícita.
- **Sem recuperação, só prompt.** Responde sem fonte e não cumpre o isolamento departamental.

## Consequências

- Uma migration (V019: documentos, versões, escopos e execuções).
- Um endpoint de pergunta: `POST /api/v1/agente-politicas/perguntas`.
- As tarefas SPI-127 (Spring AI) e SPI-128 (pgvector) são **redefinidas** como "cliente de provider
  sem SDK" (coberto pelo ADR-38) e "schema de conhecimento com busca nativa".
- Recuperação por palavra, não por semântica: perguntas com sinônimos distantes podem não achar a
  fonte.
