# ADR-37 — Provisionamento, delegação e revogação de concessões

## Status

ACCEPTED (2026-10-18).

## Contexto

Hoje nenhum fluxo de produção cria concessões:

- `POST /api/v1/tenants` não cria usuário;
- o aceite de convite cria o usuário sem papel;
- `POST /api/v1/usuarios` cria um usuário que nunca consegue logar.

Fora o seeder de demonstração, nenhum usuário real tem autorização (N01).

Além disso, quem tem `USER:MANAGE` pode alterar o próprio rótulo e o próprio departamento, e convidar
qualquer nível (N03). Não há canal de e-mail (SPI-65).

## Decisão

1. **Bootstrap.** `POST /api/v1/tenants` recebe também nome, e-mail e senha do primeiro
   administrador. Na mesma transação, cria o tenant, os papéis de sistema, o usuário e a concessão
   `TENANT_ADMIN`/TENANT (`origin = BOOTSTRAP`, `granted_by = NULL`).
2. **O convite carrega a concessão.** O convite guarda `role_id` e `invited_by`. O `department_id`
   que já existe vira a lotação do convidado e o departamento da concessão, quando o papel é
   departamental. O aceite revalida a autoridade de quem convidou: se ela não domina mais o papel e
   o escopo, o convite cai (409) e nenhum usuário é criado. `AcceptInvite` cria o usuário e a
   concessão na mesma transação.
3. **Entrega.** O link copiável é devolvido só a quem convida. O token é guardado apenas como hash
   (SHA-256), com expiração e uso único, e nunca vai para log. A V015 revoga os convites pendentes
   do formato antigo.
4. **Dominância.** Um ator só concede (papel R, escopo S) se tiver uma concessão de posto maior que R
   cujo escopo contenha S. Só `TENANT_ADMIN` concede MANAGER, EXECUTIVE e `TENANT_ADMIN`. Na prática,
   o MANAGER(A) só convida COLLABORATOR ou LEADER no departamento A. Um ator só age sobre um usuário
   (editar, desativar, revogar) se dominar todas as concessões ativas desse usuário.
5. **Guardas fixas.**
   - O ator nunca altera o próprio rótulo, departamento, status ou concessões (403).
   - Não se revoga nem se desativa o último `TENANT_ADMIN` ativo (409).
6. **Revogação.** É lógica (`revoked_at`, `revoked_by`) e vale na requisição seguinte.
6a. **Atribuição direta.** Atribuir papel a um usuário existente
   (`POST /api/v1/usuarios/{id}/concessoes`) exige `ROLE:MANAGE`, que só o `TENANT_ADMIN` tem.
   Gestores delegam apenas por convite.
7. **Remoção do legado.** `POST /api/v1/usuarios` é removido; o caminho de entrada é o convite.
   `RegisterUser`, `User.permissions`, `isAdmin()` e `hasPermission()` deixam de existir no código.
   A tabela `user_permission` é removida no release seguinte (V021+).
8. **Seeder alinhado.** O seeder de demonstração usa os mesmos casos de uso de produção.
   `RoleAssignment` só é instanciado em `iam.application` (regra ArchUnit).

## Alternativas consideradas

- Comando de operador para o primeiro admin: dispensa o auto-cadastro público de admin, mas complica
  a demo e exige ferramenta de operação.
- Manter EXECUTIVE como admin: poupa uma mudança de matriz, mas confunde diretor com administrador
  de TI.
- Gestores atribuindo papéis a usuários já existentes: dá mais flexibilidade, mas aumenta a
  superfície. Rejeitado no MVP; só o `TENANT_ADMIN` atribui diretamente (item 6a).
- E-mail real: bloqueado por falta de infraestrutura.

## Consequências

- Fecha N01 e N03 juntos. O contrato de `POST /tenants` muda, e o onboarding do front precisa enviar
  os dados do admin.
- Agrava o risco residual de ocupação de domínio (`email_domain` sem verificação), que já está
  registrado.
- O link de convite é um segredo de curta duração.
