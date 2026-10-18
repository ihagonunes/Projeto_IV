# TASK-S3-D1 — Backend do chat privado com recibo

## Metadados
- **Onda:** D (chat) · **Depende de:** ADR-39 ACCEPTED, A2 (teto CHAT), C1 (`AICompletionService`)
- **Jira:** **SPI-172** (backend do chat corporativo, suporte à SPI-136); SPI-134 (parte de dados do recibo)
- **Módulo proprietário:** `chat`
- **Spec:** `SPEC-04-chat.md` (inteira)

## Critérios de aceitação
- [ ] `V018__chat_conversations.sql` exatamente como na SPEC-04 §1.
- [ ] Domínio, aplicação e infraestrutura do módulo `chat`, só com imports permitidos
      (`sharedkernel`, `iam.api`, `aigateway.api`).
- [ ] Os 4 endpoints da SPEC-04 §3, com gate BY_TARGET, dono = ator e 404 para conversa de outra
      pessoa.
- [ ] O fluxo de envio não segura transação durante a chamada ao provider. Em falha, a mensagem
      USER fica gravada e nenhuma ASSISTANT é criada.
- [ ] O recibo bate com a tentativa e com o `UsageRecord`.
- [ ] O conteúdo nunca vai para log nem evento. Há um teste que captura o log.
- [ ] Testes da SPEC-04 §4, incluindo AZ12 em IT.
- [ ] `ai/contracts.md` atualizado (seção chat) e o contrato enviado à Lyn no relatório (JSON de
      exemplo de cada endpoint).

## Restrição
Não alterar arquivos de front do componente de chat (SPI-136).

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
