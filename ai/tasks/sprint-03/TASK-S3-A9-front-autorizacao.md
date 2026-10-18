# TASK-S3-A9 — Front: onboarding com admin, menus por `/me/permissoes`, convites e concessões

## Metadados
- **Onda:** A · **Ordem:** 9 · **Depende de:** A4–A8 (contratos estáveis)
- **Jira:** SPI-48 (front), SPI-95 (parte do erro 403)
- **Área:** front Express/EJS em `src/main/resources/static`
- **Spec:** `SPEC-01` §6, §7; `docs/review/REVIEW_DEBUG_SPRINT2.md` §2 (rotas desalinhadas)

## Restrição
**Não tocar** em arquivos do componente de chat (SPI-136, da Lyn). Se for preciso, parar e perguntar.

## Critérios de aceitação
- [ ] Onboarding (`public/js/pages/onboarding/step-tenant.js` e view):
  - [ ] envia `adminName`, `adminEmail` e `adminPassword`;
  - [ ] depois de criar o tenant, faz o login do admin.
- [ ] `admin-usuarios.js` e `admin-convites.js`:
  - [ ] removem `localStorage('simulatedUserRole')`;
  - [ ] guiam menus e botões por `GET /api/v1/usuarios/me/permissoes` (só experiência de uso; o
        backend continua decidindo).
- [ ] Formulário de convite:
  - [ ] papel vindo de `GET /api/v1/papeis` (só os `concedivel`);
  - [ ] campo de departamento;
  - [ ] o link do convite aparece uma única vez, com botão de copiar.
- [ ] Tela de concessões do usuário:
  - [ ] lista via `/{id}/permissoes`;
  - [ ] admin atribui e revoga.
- [ ] O fluxo de criação manual de usuário sai (`POST /usuarios` não existe mais) e passa a apontar
      para o convite.
- [ ] Conferir e alinhar as rotas do REVIEW: aceite em `/api/v1/convites/aceitar`, `/tenants/me` e
      o verbo PATCH. Registrar quais já estavam corretas.
- [ ] Dashboard:
  - [ ] esconde o filtro de departamento quando a visibilidade não é TENANT;
  - [ ] trata 403 com mensagem.
- [ ] O relatório descreve a verificação manual do fluxo: registrar → login admin → convidar →
      aceitar → login do colaborador → menus corretos.

## Checks
- `npm start` sobe e o fluxo é navegável manualmente
- `./scripts/check` (backend inalterado)
