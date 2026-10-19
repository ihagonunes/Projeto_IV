package br.com.qsoft.governance.iam.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.AuthorizationScope;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Testes do adapter REAL {@link AuthorizationQueryAdapter} (contrato público
 * de autorização do IAM — ADR-04).
 *
 * <p>Diferente dos testes de caso de uso, aqui NÃO usamos um fake de
 * {@code AuthorizationQuery}: testamos o adapter de infraestrutura de verdade,
 * injetando nele repositórios fake em memória (mesmo padrão do
 * {@code IdentityQueryTest}).
 *
 * <p>Complementa o cenário BDD da Evelyn: um usuário com papel MANAGER deve
 * ter escopo {@code DEPARTMENT} (acesso a consumo/custos/adoção do seu
 * departamento) e NUNCA escopo {@code TENANT} (que daria acesso a dados
 * além do departamento). A privacidade de conversas de usuários é regra do
 * módulo chat (ADR-16) e será testada por aquele módulo.
 */
class AuthorizationQueryAdapterTest {

    // =====================================================================
    // "BANCO DE DADOS" EM MEMÓRIA (mesma técnica dos testes de caso de uso)
    // =====================================================================
    private final Map<UserId, User> usersById = new LinkedHashMap<>();
    private final Map<TenantId, Tenant> tenantsById = new LinkedHashMap<>();

    private final UserRepository userRepository = new UserRepository() {
        @Override
        public User save(User user) {
            usersById.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(usersById.get(id));
        }

        @Override
        public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersById.values().stream()
                .filter(u -> u.tenantId().equals(tenantId) && u.email().equals(email))
                .findFirst();
        }

        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersById.values().stream()
                .anyMatch(u -> u.tenantId().equals(tenantId) && u.email().equals(email));
        }
    };

    private final TenantRepository tenantRepository = new TenantRepository() {
        @Override
        public Tenant save(Tenant tenant) {
            tenantsById.put(tenant.id(), tenant);
            return tenant;
        }

        @Override
        public Optional<Tenant> findById(TenantId id) {
            return Optional.ofNullable(tenantsById.get(id));
        }

        @Override
        public Optional<Tenant> findBySlug(String slug) {
            return tenantsById.values().stream()
                .filter(t -> t.slug().equals(slug))
                .findFirst();
        }

        @Override
        public boolean existsBySlug(String slug) {
            return tenantsById.values().stream().anyMatch(t -> t.slug().equals(slug));
        }
    };

    // O OBJETO SOB TESTE: o adapter REAL, com os fakes injetados.
    // O PolicyEvaluator é mockado: estes testes cobrem a trilha legada
    // (checkPermission/resolveScope), que não o utiliza.
    private final AuthorizationQueryAdapter authorizationQuery =
        new AuthorizationQueryAdapter(userRepository, tenantRepository, mock(PolicyEvaluator.class));

    // ---------------------------------------------------------------------
    // Helpers (mesma técnica dos testes de caso de uso)
    // ---------------------------------------------------------------------

    private Tenant activeTenant() {
        TenantId id = TenantId.random();
        Tenant tenant = Tenant.restore(
            id, "qsoft", "QSOFT", TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenantsById.put(id, tenant);
        return tenant;
    }

    private User userIn(TenantId tenantId, String email, HierarchyLevel level,
                        Permission... permissions) {
        User user = User.create(UserId.random(), tenantId, email, email, level, null);
        for (Permission permission : permissions) {
            user.grantPermission(permission);
        }
        usersById.put(user.id(), user);
        return user;
    }

    // =====================================================================
    // CENÁRIO BDD — planejado por: <b>Evelyn</b> (parte 2: escopo)
    // =====================================================================

    /**
     * Um gestor (MANAGER) tem escopo organizacional DEPARTMENT — acesso a
     * consumo/custos/adoção do SEU departamento — e NUNCA escopo TENANT
     * (que alcançaria os departamentos de todos os outros usuários).
     *
     * <p>Isto trava o comportamento existente do adapter (teste de
     * caracterização): a premissa de "gestor vê dados do departamento, mas
     * não dados alheios" é a base da autorização por escopo do IAM.
     */
    @Test
    void managerShouldResolveDepartmentScopeNotTenant() {
        // Arrange: um gestor ativo dentro de um tenant ativo.
        Tenant tenant = activeTenant();
        User manager = userIn(tenant.id(), "gestor@qsoft.com", HierarchyLevel.MANAGER);

        // Act: resolve o escopo organizacional do gestor.
        AuthorizationScope scope = authorizationQuery.resolveScope(manager.id());

        // Assert: escopo é o departamento do gestor — nunca o tenant inteiro.
        assertThat(scope).isEqualTo(AuthorizationScope.DEPARTMENT);
        assertThat(scope).isNotEqualTo(AuthorizationScope.TENANT);
    }

    /** Colaborador tem escopo SELF (apenas os próprios dados). */
    @Test
    void collaboratorShouldResolveSelfScope() {
        // Arrange: um colaborador ativo dentro de um tenant ativo.
        Tenant tenant = activeTenant();
        User collaborator = userIn(tenant.id(), "colab@qsoft.com", HierarchyLevel.COLLABORATOR);

        // Act & Assert.
        assertThat(authorizationQuery.resolveScope(collaborator.id()))
            .isEqualTo(AuthorizationScope.SELF);
    }

    /** Executivo tem escopo TENANT (todo o tenant, todos os departamentos). */
    @Test
    void executiveShouldResolveTenantScope() {
        // Arrange: um executivo ativo dentro de um tenant ativo.
        Tenant tenant = activeTenant();
        User executive = userIn(tenant.id(), "ceo@qsoft.com", HierarchyLevel.EXECUTIVE);

        // Act & Assert.
        assertThat(authorizationQuery.resolveScope(executive.id()))
            .isEqualTo(AuthorizationScope.TENANT);
    }

    // =====================================================================
    // CENÁRIO BDD — planejado por: <b>Maria Eduarda</b> (reforço no IAM)
    // =====================================================================

    /**
     * Um usuário sem a permissão MANAGE_USERS não pode executar operações de
     * cadastro de usuários: a checagem de permissão do adapter (regra REAL de
     * produção) devolve {@code deny} para quem não possui a permissão.
     *
     * <p>Este teste reforça a mesma regra coberta pelo RegisterUserTest, mas
     * no nível do ADAPTER (a peça que em produção decide a autorização).
     */
    @Test
    void checkPermissionShouldDenyUserWithoutPermission() {
        // Arrange: tenant ativo + colaborador SEM a permissão administrativa.
        Tenant tenant = activeTenant();
        User collaborator = userIn(tenant.id(), "colab@qsoft.com", HierarchyLevel.COLLABORATOR);

        // Act: checagem determinística da permissão MANAGE_USERS.
        AuthorizationResult result =
            authorizationQuery.checkPermission(collaborator.id(), Permission.MANAGE_USERS);

        // Assert: negado, com motivo para o cliente entender a falha.
        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).isNotBlank();
    }

    // =====================================================================
    // Branches faltantes em checkPermission (branch 58.8% → ≥ 70%)
    // =====================================================================

    /** Usuário não encontrado → negado com motivo. */
    @Test
    void deve_negarPermissao_quando_usuarioNaoExiste() {
        AuthorizationResult result =
            authorizationQuery.checkPermission(UserId.random(), Permission.MANAGE_USERS);

        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).contains("não encontrado");
    }

    /** Usuário inativo → negado mesmo tendo permissão. */
    @Test
    void deve_negarPermissao_quando_usuarioInativo() {
        Tenant tenant = activeTenant();
        User user = userIn(tenant.id(), "inativo@qsoft.com", HierarchyLevel.EXECUTIVE,
            Permission.MANAGE_USERS);
        user.deactivate();
        usersById.put(user.id(), user);

        AuthorizationResult result =
            authorizationQuery.checkPermission(user.id(), Permission.MANAGE_USERS);

        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).contains("inativo");
    }

    /** Tenant não encontrado → negado. */
    @Test
    void deve_negarPermissao_quando_tenantNaoExiste() {
        // Usuário sem tenant registrado no repositório
        User user = User.create(UserId.random(), TenantId.random(),
            "orphan@x.com", "Orphan", HierarchyLevel.EXECUTIVE, null);
        user.grantPermission(Permission.MANAGE_USERS);
        usersById.put(user.id(), user);
        // tenant NÃO é salvo em tenantsById

        AuthorizationResult result =
            authorizationQuery.checkPermission(user.id(), Permission.MANAGE_USERS);

        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).contains("inativo");
    }

    /** Tenant inativo → negado mesmo com user ACTIVE. */
    @Test
    void deve_negarPermissao_quando_tenantInativo() {
        TenantId tenantId = TenantId.random();
        Tenant inactiveTenant = Tenant.restore(tenantId, "off", "Off",
            TenantStatus.INACTIVE, Instant.now(), Instant.now());
        tenantsById.put(tenantId, inactiveTenant);

        User user = User.create(UserId.random(), tenantId,
            "user@off.com", "U", HierarchyLevel.EXECUTIVE, null);
        user.grantPermission(Permission.MANAGE_USERS);
        usersById.put(user.id(), user);

        AuthorizationResult result =
            authorizationQuery.checkPermission(user.id(), Permission.MANAGE_USERS);

        assertThat(result.allowed()).isFalse();
        assertThat(result.denialReason()).contains("inativo");
    }

    /** Usuário ativo com permissão e tenant ativo → autorizado. */
    @Test
    void deve_permitirPermissao_quando_usuarioEtenantAtivos() {
        Tenant tenant = activeTenant();
        User admin = userIn(tenant.id(), "admin@qsoft.com", HierarchyLevel.EXECUTIVE,
            Permission.MANAGE_USERS);

        AuthorizationResult result =
            authorizationQuery.checkPermission(admin.id(), Permission.MANAGE_USERS);

        assertThat(result.allowed()).isTrue();
        assertThat(result.denialReason()).isNull();
    }

    // =====================================================================
    // resolveScope — branches faltantes: LEADER, isAdmin
    // =====================================================================

    /** LEADER tem escopo DEPARTMENT (mesmo nível que MANAGER). */
    @Test
    void leaderShouldResolveDepartmentScope() {
        Tenant tenant = activeTenant();
        User leader = userIn(tenant.id(), "lider@qsoft.com", HierarchyLevel.LEADER);

        AuthorizationScope scope = authorizationQuery.resolveScope(leader.id());

        assertThat(scope).isEqualTo(AuthorizationScope.DEPARTMENT);
    }

    /** Admin (todas as permissões) sempre tem escopo TENANT, independente do nível hierárquico. */
    @Test
    void adminShouldResolveTenantScope() {
        Tenant tenant = activeTenant();
        User admin = userIn(tenant.id(), "admin@qsoft.com", HierarchyLevel.COLLABORATOR);
        // Concede TODAS as permissões → isAdmin() = true
        for (Permission p : Permission.values()) {
            admin.grantPermission(p);
        }
        usersById.put(admin.id(), admin);

        AuthorizationScope scope = authorizationQuery.resolveScope(admin.id());

        assertThat(scope).isEqualTo(AuthorizationScope.TENANT);
    }

    /** Usuário não encontrado → scope padrão SELF (comportamento defensivo). */
    @Test
    void deve_retornarSelfScope_quando_usuarioNaoExiste() {
        AuthorizationScope scope = authorizationQuery.resolveScope(UserId.random());

        assertThat(scope).isEqualTo(AuthorizationScope.SELF);
    }
}