# TASK-SPI-65 — Fluxo de recuperação de senha

## Metadados
- **Projeto:** SPI
- **Sprint:** SPI Sprint 2
- **Chave:** SPI-65
- **História PO:** POPROJ-15
- **Status:** Tarefas pendentes
- **Responsável:** Não atribuído
- **Prioridade:** Medium
- **Categorias:** `sprint-2`
- **Grupo:** Autenticação, Sessões e Segurança

## Fonte original do consolidado
Link de recuperação expira após tempo definido; Nova senha segue política de segurança; Link de uso único.

## Versão reconciliada com a arquitetura
A task está compatível com a baseline atual e não exige alteração estrutural.

## Área proprietária
`iam + platform/security`

## Contexto para agente
- `AGENTS.md`
- `ai/architecture.md`
- `ai/testing.md`
- `ADR-30`

## Regras transversais
- Cross-module somente via `module.api.*`.
- Sem associação JPA/FK SQL cross-module.
- TenantId vem do contexto autenticado.
- Persistência muda somente com Flyway.
- PostgreSQL é o único banco suportado.
- Frontend nunca substitui autorização backend.
- Mudança estrutural exige ADR.

## Nota de implementação (bloqueado por decisão)

A task **não pode ser implementada sem duas decisões**, porque faltam pré-requisitos na baseline:

1. **Canal de entrega do link** — não existe infraestrutura de e-mail/notificação (nenhuma dependência de mail no `pom.xml`). Retornar o token na resposta HTTP permitiria sequestro de conta; registrá-lo em log viola o AGENTS §9.
2. **Política de senha** — não existe nenhuma validação de força/complexidade no projeto, e a task exige que a nova senha "siga política de segurança".

Opções em `ai/decisions.md` → *Decisões abertas*: (a) porta `PasswordResetNotifier` com no-op no MVP; (b) `spring-boot-starter-mail` (nova dependência, exige `ai/dependencies.md`/ADR); (c) fora do MVP.

**Enquanto não decidido:** nenhum endpoint de recuperação de senha é exposto.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration` quando aplicável.
