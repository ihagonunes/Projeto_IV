# TASK-S3-A4 — Bootstrap do primeiro admin e fixtures de IT pelo fluxo de produção

## Metadados
- **Onda:** A · **Ordem:** 4 · **Depende de:** A1 (A2 recomendado)
- **Jira:** **SPI-169** (bootstrap do primeiro admin, N01); complementa SPI-50
- **Módulo proprietário:** `iam`
- **Spec:** `SPEC-01` §7.1, §9, §11 (AZ01); ADR-37 itens 1 e 8

## Objetivo
- `POST /api/v1/tenants` passa a criar, na mesma transação, o tenant **e** o primeiro usuário, com
  concessão `TENANT_ADMIN` de origem BOOTSTRAP.
- Todos os ITs passam a preparar cenário só pelos casos de uso de produção.

## Critérios de aceitação
- [ ] `TenantRequest` e `RegisterTenantCommand` ganham `adminName`, `adminEmail` e `adminPassword`,
      com as validações da §7.1.
- [ ] `RegisterTenant` é `@Transactional`.
- [ ] Criar o caso de uso interno `BootstrapTenantAdmin` (ou um passo explícito equivalente). Falha
      em qualquer etapa reverte tudo.
- [ ] A senha nunca aparece em log nem em resposta.
- [ ] `DemoDataSeeder` chama `RegisterTenant` com o admin:
  - [ ] sem `EnumSet.allOf(Permission)`;
  - [ ] sem criar `RoleAssignment` direto;
  - [ ] continua idempotente.
- [ ] Criar `src/test/java/br/com/qsoft/governance/support/TestTenants.java` (SPEC-01 §9).
- [ ] Migrar os 9 ITs listados em V19. Nenhum `*IT.java` usa `roleAssignmentRepository.save` ou
      `RoleAssignment.*`, exceto `RolePersistenceIT`.
  - Enquanto a A5 não existir, usuários não-admin podem ser criados pelo `TestTenants` com o fluxo
    de convite atual mais `AssignRole` interno. Nunca inserir concessão à mão.
- [ ] IT AZ01.
- [ ] IT de transação: falha simulada depois de criar o tenant → nenhum tenant nem usuário
      persistido.
- [ ] `OnboardingFlowIT` (`HANDOFF.md` §5) entra **só** quando a A5 estiver no mesmo branch. Até
      lá, registrar como pendente.

## Contexto para o agente
- ADR-37
- `SPEC-01` §7.1, §9
- `VERIFICACAO_FASE0.md` (V01, V19, D1)
- Código: `RegisterTenant`, `TenantController`, `TenantRequest`, `DemoDataSeeder` e os 9 ITs

## Notas
O contrato de `POST /tenants` muda. O front do onboarding é a A9; avisar no relatório.

## Checks
- `./scripts/check --changed`
- `./scripts/check`
- `./scripts/check-integration`
