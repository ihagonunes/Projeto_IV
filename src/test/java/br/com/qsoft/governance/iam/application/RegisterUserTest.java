package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.AuthorizationScope;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do caso de uso {@link RegisterUser} (cadastro de usuários
 * com papel hierárquico pelo administrador do tenant).
 *
 * <h2>História</h2>
 * Como administrador, quero cadastrar usuários da minha organização com um
 * papel (colaborador, líder, gestor, executivo) para que cada um tenha o
 * acesso adequado ao seu nível.
 *
 * <h2>Padrão de teste (sem Spring, sem banco, sem Mockito)</h2>
 * Testamos a classe REAL {@link RegisterUser} de produção. O que é "fake"
 * é o ambiente ao redor dela: o "banco de dados" (dois {@code Map} em
 * memória) e a "consulta de autorização" (uma implementação manual da
 * interface {@link AuthorizationQuery} com a MESMA regra do adapter real).
 *
 * <p>Fluxo dos dados:
 * <pre>
 *   Teste (Arrange) → usa helpers activeTenant()/userIn() para "gravar"
 *                     dados no banco em memória (os Map's);
 *   useCase.register(...) → RegisterUser REAL chama os fakes;
 *   fakes leem/escrevem NOS MESMOS Map's populados pelo Arrange;
 *   Assert verifica o resultado e o estado final do "banco".
 * </pre>
 *
 * <p>O JUnit 5 cria uma instância NOVA da classe de teste para cada
 * {@code @Test} (lifecycle padrão PER_METHOD), então cada teste começa com
 * os Map's vazios — isolamento automático (não precisa de {@code @BeforeEach}
 * com {@code clear()}).
 */
class RegisterUserTest {

    // =====================================================================
    // "BANCO DE DADOS" EM MEMÓRIA
    // Estes Map's simulam as tabelas do PostgreSQL local:
    //   usersById   = SELECT * FROM app_user WHERE id = ?
    //   tenantsById = SELECT * FROM tenant WHERE id = ?
    // Todos os fakes abaixo compartilham estes Map's, garantindo consistência
    // entre "salvar" (save) e "consultar" (findById/existsBy...).
    // =====================================================================
    private final Map<UserId, User> usersById = new LinkedHashMap<>();
    private final Map<TenantId, Tenant> tenantsById = new LinkedHashMap<>();

    // FAKE do UserRepository (a "porta de persistência" de usuários do IAM)
    // Implementamos a interface real UserRepository como uma "classe anônima"
    // (new UserRepository() { ... }). Em produção, quem implementa esta porta
    // é o UserRepositoryAdapter (camada infrastructure), que usa JPA. Aqui,
    // cada metodo lê/escreve nos Map's em memória.
    private final UserRepository userRepository = new UserRepository() {
        // "INSERT INTO app_user ..." — grava o usuário no banco fake.
        @Override
        public User save(User user) {
            usersById.put(user.id(), user);
            return user;
        }

        // "SELECT * FROM app_user WHERE id = ?" — retorna o usuário pelo PK.
        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(usersById.get(id));
        }

        // "SELECT * FROM app_user WHERE tenant_id = ? AND email = ?"
        // Usado para unicidade de e-mail e para recuperações por e-mail.
        @Override
        public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersById.values().stream()
                .filter(u -> u.tenantId().equals(tenantId) && u.email().equals(email))
                .findFirst();
        }

        // "SELECT EXISTS(...)" — usado pelo CreateUser para validar unicidade
        // de e-mail dentro do tenant (lança UserEmailAlreadyExistsException).
        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersById.values().stream()
                .anyMatch(u -> u.tenantId().equals(tenantId) && u.email().equals(email));
        }
    };

    // FAKE do TenantRepository (a "porta de persistência" de tenants do IAM)
    // Necessário porque o CreateUser REAL valida "tenant existe e está
    // ACTIVE" antes de criar o usuário.
    private final TenantRepository tenantRepository = new TenantRepository() {
        // "INSERT INTO tenant ..."
        @Override
        public Tenant save(Tenant tenant) {
            tenantsById.put(tenant.id(), tenant);
            return tenant;
        }

        // "SELECT * FROM tenant WHERE id = ?"
        @Override
        public Optional<Tenant> findById(TenantId id) {
            return Optional.ofNullable(tenantsById.get(id));
        }

        // "SELECT * FROM tenant WHERE slug = ?"
        @Override
        public Optional<Tenant> findBySlug(String slug) {
            return tenantsById.values().stream()
                .filter(t -> t.slug().equals(slug))
                .findFirst();
        }

        // "SELECT EXISTS(...)" para slug único.
        @Override
        public boolean existsBySlug(String slug) {
            return tenantsById.values().stream().anyMatch(t -> t.slug().equals(slug));
        }
    };

    // FAKE do AuthorizationQuery (contrato público de autorização do IAM)
    // IMPORTANTE: este fake NÃO é um mock "vazio". Ele replica a MESMA regra
    // do AuthorizationQueryAdapter real (ADR-04): Usuário existente + ACTIVE +
    // Tenant ACTIVE + permissão concedida. A diferença é que ele consulta o
    // banco em memória em vez do PostgreSQL.

    private final AuthorizationQuery authorizationQuery = new AuthorizationQuery() {
        @Override
        public AuthorizationResult checkPermission(UserId userId, Permission permission) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return AuthorizationResult.deny("Usuário não encontrado.");
            }
            if (user.status() != UserStatus.ACTIVE) {
                return AuthorizationResult.deny("Usuário inativo.");
            }
            if (!user.hasPermission(permission)) {
                return AuthorizationResult.deny("Permissão negada: " + permission.name() + ".");
            }
            return AuthorizationResult.allow();
        }

        // Não usado pelos testes deste arquivo (usado no AuthorizationQueryAdapterTest).
        @Override
        public AuthorizationScope resolveScope(UserId userId) {
            return AuthorizationScope.SELF;
        }

        // Novos métodos do contrato (ADR-36) — não usados por este teste.
        @Override
        public boolean isAllowed(br.com.qsoft.governance.sharedkernel.id.TenantId tenantId,
                                 UserId actor,
                                 br.com.qsoft.governance.sharedkernel.rbac.Resource resource,
                                 br.com.qsoft.governance.sharedkernel.rbac.Action action,
                                 br.com.qsoft.governance.sharedkernel.rbac.AccessTarget target) {
            return false;
        }

        @Override
        public br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope visibilityFor(
                br.com.qsoft.governance.sharedkernel.id.TenantId tenantId, UserId actor,
                br.com.qsoft.governance.sharedkernel.rbac.Resource resource,
                br.com.qsoft.governance.sharedkernel.rbac.Action action) {
            return new br.com.qsoft.governance.sharedkernel.rbac.VisibilityScope.None();
        }
    };

    // CASO DE USO SOB TESTE (a classe REAL de produção)
    // Em vez de o Spring injetar os adaptadores de banco, injetamos os fakes.
    // O CreateUser passado também é o REAL (com os mesmos fakes) — portanto a
    // validação de tenant ACTIVE, e-mail duplicado e papel obrigatório é a
    // verdadeira regra de produção.
    private final RegisterUser useCase = new RegisterUser(
        userRepository,
        authorizationQuery,
        new CreateUser(userRepository, tenantRepository));

    // HELPERS (fábricas de dados usadas no bloco "Arrange" dos testes)

    /**
     * Cria um tenant com status ACTIVE e já o "grava" no banco em memória.
     * Necessário porque o {@link CreateUser} real recusa tenant inexistente
     * ou inativo (lança {@code TenantNotFoundException}).
     */
    private Tenant activeTenant() {
        TenantId id = TenantId.random();
        Tenant tenant = Tenant.restore(
            id, "qsoft", "QSOFT", TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenantsById.put(id, tenant);
        return tenant;
    }

    /**
     * Cria um usuário já "persistido" no banco em memória.
     *
     * @param permissions permissões (varargs). Passe {@code Permission.MANAGE_USERS}
     *                    para simular um administrador; passe nada para simular
     *                    um usuário comum sem permissão administrativa.
     */
    private User userIn(TenantId tenantId, String email, HierarchyLevel level,
                        Permission... permissions) {
        User user = User.create(UserId.random(), tenantId, email, email, level, null);
        for (Permission permission : permissions) {
            user.grantPermission(permission);
        }
        usersById.put(user.id(), user);
        return user;
    }

    // CENÁRIOS BDD DA USER STORY "Cadastro de usuários com papel"

    /**
     * CENÁRIO BDD — planejado por: <b>Ihago</b>
     *
     * Dado que sou um administrador autenticado
     * Quando cadastro um usuário com o papel "colaborador"
     * Então o usuário deve ser registrado como colaborador
     * E deve estar ativo dentro do meu tenant.
     *
     * Testa o CAMINHO GREEN do caso de uso RegisterUser:
     * ator com permissão MANAGE_USERS cadastra Ana como COLLABORATOR.
     * O CreateUser (real) cuida do restante: tenant ACTIVE, e-mail único,
     * status inicial ACTIVE.
     */
    @Test
    void shouldRegisterUserWithCollaboratorRole() {
        // Arrange: um tenant ativo + o administrador autenticado com a
        // permissão que autoriza cadastrar usuários (MANAGE_USERS).
        Tenant tenant = activeTenant();
        User admin = userIn(tenant.id(), "admin@qsoft.com",
            HierarchyLevel.EXECUTIVE, Permission.MANAGE_USERS);

        // Act: o administrador cadastra Ana como colaboradora.
        User created = useCase.register(new RegisterUserCommand(
            admin.id(), tenant.id(), "ana@qsoft.com", "Ana",
            HierarchyLevel.COLLABORATOR, null));

        // Assert: Ana foi criada com o papel solicitado, ativa, no tenant certo
        // e realmente persistida (o "banco" fake tem registro por tenant+email).
        assertThat(created.hierarchyLevel()).isEqualTo(HierarchyLevel.COLLABORATOR);
        assertThat(created.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.tenantId()).isEqualTo(tenant.id());
        assertThat(userRepository.existsByTenantIdAndEmail(tenant.id(), "ana@qsoft.com")).isTrue();
    }

    /**
     * CENÁRIO BDD — planejado por: <b>Maria Eduarda</b>
     *
     * Dado que sou um usuário autenticado sem permissão de administrar usuários
     * Quando tento acessar a funcionalidade de cadastro de usuários
     * Então o sistema deve negar o meu acesso
     * E não deve criar nenhum usuário.
     *
     * Testa a CAMADA DE AUTORIZAÇÃO (ADR-04): para cadastrar usuários é
     * obrigatório ter a permissão MANAGE_USERS. O ator é um COLLABORATOR
     * SEM permissão — o AuthorizationQuery (fake com regra real) devolve
     * "Permissão negada" e o RegisterUser lança AuthorizationDeniedException.
     * A asserção extra garante que a negação aconteceu ANTES de qualquer
     * persistência (nenhum INSERT foi feito).
     */
    @Test
    void shouldDenyWhenActorLacksPermission() {
        // Arrange: tenant ativo + ator SEM a permissão MANAGE_USERS.
        Tenant tenant = activeTenant();
        User collaborator = userIn(tenant.id(), "user@qsoft.com", HierarchyLevel.COLLABORATOR);

        // Act & Assert: tentar cadastrar deve lançar AuthorizationDeniedException
        // (o ator não tem a permissão administrativa necessária).
        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand(
            collaborator.id(), tenant.id(), "ana@qsoft.com", "Ana",
            HierarchyLevel.MANAGER, null)))
            .isInstanceOf(AuthorizationDeniedException.class);

        // Assert extra (segurança): a negação ocorreu antes de persistir —
        // nenhum usuário "ana@qsoft.com" existe no banco do tenant.
        assertThat(userRepository.existsByTenantIdAndEmail(tenant.id(), "ana@qsoft.com")).isFalse();
    }

    /**
     * CENÁRIO BDD — planejado por: <b>Evelyn</b>
     *
     * Dado que sou um administrador autenticado
     * Quando cadastro um usuário como "gestor" para o departamento Marketing
     * Então o usuário deve ter o perfil de Gestor com acesso às informações
     * de consumo, custos e adoção do seu departamento
     * E não deve possuir permissão para visualizar o conteúdo privado de
     * conversas de outros usuários.
     *
     * PARTE 1 (este teste): o cadastro persiste o papel MANAGER vinculado ao
     * departamento informado (departmentId = Marketing).
     *
     * A PARTE 2 (escopo organizacional e privacidade de chats) é coberta pelo
     * AuthorizationQueryAdapterTest: o escopo do MANAGER é DEPARTMENT (nunca
     * TENANT). A privacidade de conversas é regra do módulo chat (ADR-16) e
     * será testada quando esse módulo existir.
     */
    @Test
    void shouldRegisterManagerForDepartment() {
        // Arrange: tenant ativo + administrador autenticado + o id do
        // departamento de Marketing (a existência do departamento em si é
        // responsabilidade do módulo department; aqui basta o id).
        Tenant tenant = activeTenant();
        User admin = userIn(tenant.id(), "admin@qsoft.com",
            HierarchyLevel.EXECUTIVE, Permission.MANAGE_USERS);
        UUID marketing = UUID.randomUUID();

        // Act: o administrador cadastra o gestor vinculado ao Marketing.
        User created = useCase.register(new RegisterUserCommand(
            admin.id(), tenant.id(), "gestor@qsoft.com", "Gestor",
            HierarchyLevel.MANAGER, marketing));

        // Assert: o papel e o vínculo com o departamento foram persistidos.
        assertThat(created.hierarchyLevel()).isEqualTo(HierarchyLevel.MANAGER);
        assertThat(created.departmentId()).isEqualTo(marketing);
    }

    /**
     * CENÁRIO BDD — planejado por: <b>Ihan</b>
     *
     * Dado que sou um administrador autenticado
     * Quando tento salvar o cadastro de um usuário sem selecionar um papel
     * Então o sistema deve impedir a ação
     * E informar que a seleção de papel é obrigatória
     * E não deve criar o usuário.
     *
     * Testa a VALIDAÇÃO DE NEGÓCIO: o papel (hierarchyLevel) é um campo
     * obrigatório. A asserção usa a MENSAGEM da exceção (e não só o tipo)
     * para garantir a mensagem de domínio "obrigatório" — comportamento que
     * o RegisterUser adiciona explicitamente, além da validação já existente
     * no CreateUser.
     */
    @Test
    void shouldRejectRegistrationWithoutRole() {
        // Arrange: tenant ativo + administrador autenticado
        // (a permissão é checada ANTES da validação de campo).
        Tenant tenant = activeTenant();
        User admin = userIn(tenant.id(), "admin@qsoft.com",
            HierarchyLevel.EXECUTIVE, Permission.MANAGE_USERS);

        // Act & Assert: papel nulo ⇒ IllegalArgumentException com mensagem de
        // domínio informando que o campo é obrigatório.
        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand(
            admin.id(), tenant.id(), "ana@qsoft.com", "Ana", null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");

        // Assert extra: o usuário NÃO foi criado (nenhum INSERT ocorreu).
        assertThat(userRepository.existsByTenantIdAndEmail(tenant.id(), "ana@qsoft.com")).isFalse();
    }

    // =====================================================================
    // Branches faltantes em RegisterUser (branch 64.3% → ≥ 70%)
    // =====================================================================

    /** Command nulo → IllegalArgumentException imediato. */
    @Test
    void deve_lancarExcecao_quando_commandEhNulo() {
        assertThatThrownBy(() -> useCase.register(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("obrigatório");
    }

    /** Email em branco → IllegalArgumentException antes de qualquer persistência. */
    @Test
    void deve_lancarExcecao_quando_emailEmBranco() {
        Tenant tenant = activeTenant();
        User admin = userIn(tenant.id(), "admin@qsoft.com",
            HierarchyLevel.EXECUTIVE, Permission.MANAGE_USERS);

        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand(
            admin.id(), tenant.id(), "  ", "Ana", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("email");
    }

    /** Name em branco → IllegalArgumentException. */
    @Test
    void deve_lancarExcecao_quando_nomeEmBranco() {
        Tenant tenant = activeTenant();
        User admin = userIn(tenant.id(), "admin@qsoft.com",
            HierarchyLevel.EXECUTIVE, Permission.MANAGE_USERS);

        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand(
            admin.id(), tenant.id(), "ana@qsoft.com", "  ", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    /** Ator não encontrado → UserNotFoundException antes de checar permissão. */
    @Test
    void deve_lancarExcecao_quando_atorNaoExiste() {
        Tenant tenant = activeTenant();
        UserId atorInexistente = UserId.random();

        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand(
            atorInexistente, tenant.id(), "ana@qsoft.com", "Ana",
            HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(br.com.qsoft.governance.iam.domain.exception.UserNotFoundException.class);
    }

    /**
     * Isolamento multi-tenant (ADR-03): ator tenta cadastrar usuário em tenant
     * diferente do seu → AuthorizationDeniedException.
     */
    @Test
    void deve_negarCadastro_quando_atorTentaCadastrarEmOutroTenant() {
        Tenant meuTenant = activeTenant();
        // Segundo tenant com ID diferente no mesmo banco fake
        TenantId outroId = TenantId.random();
        Tenant outroTenant = Tenant.restore(
            outroId, "outro", "Outro", TenantStatus.ACTIVE,
            Instant.now(), Instant.now());
        tenantsById.put(outroId, outroTenant);

        User admin = userIn(meuTenant.id(), "admin@qsoft.com",
            HierarchyLevel.EXECUTIVE, Permission.MANAGE_USERS);

        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand(
            admin.id(), outroId, "ana@outro.com", "Ana",
            HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(AuthorizationDeniedException.class)
            .hasMessageContaining("outro tenant");
    }

    /**
     * Testa o branch de denialReason não-nulo:
     * quando o AuthorizationQuery retorna deny COM razão explícita,
     * essa razão é propagada no AuthorizationDeniedException.
     */
    @Test
    void deve_propagarDenialReason_quando_autorizacaoNegadaComMotivo() {
        Tenant tenant = activeTenant();
        // Colaborador sem MANAGE_USERS → deny com motivo "Permissão negada: MANAGE_USERS."
        User collaborator = userIn(tenant.id(), "colab@qsoft.com", HierarchyLevel.COLLABORATOR);

        assertThatThrownBy(() -> useCase.register(new RegisterUserCommand(
            collaborator.id(), tenant.id(), "nova@qsoft.com", "Nova",
            HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(AuthorizationDeniedException.class)
            .hasMessageContaining("MANAGE_USERS");
    }
}

