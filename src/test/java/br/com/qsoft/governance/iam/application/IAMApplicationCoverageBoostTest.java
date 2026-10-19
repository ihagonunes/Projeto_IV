package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantDomainRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.application.port.UserSearchRepository;
import br.com.qsoft.governance.iam.application.rbac.ProvisionDefaultRoles;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.exception.TenantDomainAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.TenantNotFoundException;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class IAMApplicationCoverageBoostTest {

    private final Map<TenantId, Tenant> tenantsById = new LinkedHashMap<>();
    private final Map<String, Tenant> tenantsBySlug = new LinkedHashMap<>();
    private final Map<UserId, User> usersById = new LinkedHashMap<>();
    private final Map<String, User> usersByTenantAndEmail = new LinkedHashMap<>();
    private final Map<InviteId, Invite> invitesById = new LinkedHashMap<>();
    private final Map<String, Tenant> domainsMap = new LinkedHashMap<>();

    private final TenantRepository tenantRepo = new TenantRepository() {
        @Override public Tenant save(Tenant t) {
            tenantsById.put(t.id(), t);
            tenantsBySlug.put(t.slug(), t);
            return t;
        }
        @Override public Optional<Tenant> findById(TenantId id) { return Optional.ofNullable(tenantsById.get(id)); }
        @Override public Optional<Tenant> findBySlug(String slug) { return Optional.ofNullable(tenantsBySlug.get(slug)); }
        @Override public boolean existsBySlug(String slug) { return tenantsBySlug.containsKey(slug); }
    };

    private final TenantDomainRepository domainRepo = new TenantDomainRepository() {
        @Override public Optional<Tenant> findByEmailDomain(String domain) { return Optional.ofNullable(domainsMap.get(domain)); }
        @Override public boolean existsByEmailDomain(String domain) { return domainsMap.containsKey(domain); }
    };

    private final UserRepository userRepo = new UserRepository() {
        @Override public User save(User u) {
            usersById.put(u.id(), u);
            usersByTenantAndEmail.put(u.tenantId() + "|" + u.email(), u);
            return u;
        }
        @Override public Optional<User> findById(UserId id) { return Optional.ofNullable(usersById.get(id)); }
        @Override public Optional<User> findByTenantIdAndEmail(TenantId tenantId, String email) {
            return Optional.ofNullable(usersByTenantAndEmail.get(tenantId + "|" + email));
        }
        @Override public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return usersByTenantAndEmail.containsKey(tenantId + "|" + email);
        }
    };

    private final Map<RoleId, Role> rolesById = new LinkedHashMap<>();

    private final RoleRepository roleRepo = new RoleRepository() {
        @Override public Role save(Role role) { rolesById.put(role.id(), role); return role; }
        @Override public Optional<Role> findById(RoleId id) { return Optional.ofNullable(rolesById.get(id)); }
        @Override public Optional<Role> findByTenantIdAndName(TenantId tenantId, String name) {
            return rolesById.values().stream()
                .filter(r -> r.tenantId().equals(tenantId) && r.name().equals(name)).findFirst();
        }
        @Override public boolean existsByTenantIdAndName(TenantId tenantId, String name) {
            return findByTenantIdAndName(tenantId, name).isPresent();
        }
    };

    private final InviteRepository inviteRepo = new InviteRepository() {
        @Override public Invite save(Invite invite) { invitesById.put(invite.id(), invite); return invite; }
        @Override public Optional<Invite> findById(InviteId id) { return Optional.ofNullable(invitesById.get(id)); }
        @Override public Optional<Invite> findByTokenHash(String tokenHash) { return Optional.empty(); }
        @Override public boolean existsByTokenHash(String tokenHash) { return false; }
        @Override public List<Invite> findByTenantId(TenantId tenantId) {
            return invitesById.values().stream().filter(i -> i.tenantId().equals(tenantId)).toList();
        }
    };

    private Tenant createActiveTenant() {
        Tenant t = Tenant.restore(TenantId.random(), "tenant1", "Tenant 1", TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenantsById.put(t.id(), t);
        tenantsBySlug.put(t.slug(), t);
        return t;
    }

    private User createActiveUser(TenantId tenantId) {
        User u = User.create(UserId.random(), tenantId, "user@test.com", "Test User", HierarchyLevel.MANAGER, null);
        usersById.put(u.id(), u);
        usersByTenantAndEmail.put(tenantId + "|user@test.com", u);
        return u;
    }

    @Test
    void testUpdateUserMissingBranches() {
        UpdateUser useCase = new UpdateUser(userRepo);

        assertThatThrownBy(() -> useCase.update(null))
            .isInstanceOf(IllegalArgumentException.class);

        UserId actorId = UserId.random();
        UserId targetId = UserId.random();
        assertThatThrownBy(() -> useCase.update(new UpdateUserCommand(actorId, targetId, null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCase.update(new UpdateUserCommand(actorId, targetId, UUID.randomUUID())))
            .isInstanceOf(UserNotFoundException.class);

        Tenant t1 = createActiveTenant();
        User actor = createActiveUser(t1.id());
        assertThatThrownBy(() -> useCase.update(new UpdateUserCommand(actor.id(), targetId, UUID.randomUUID())))
            .isInstanceOf(UserNotFoundException.class);

        Tenant t2 = createActiveTenant();
        User target = createActiveUser(t2.id());
        assertThatThrownBy(() -> useCase.update(new UpdateUserCommand(actor.id(), target.id(), UUID.randomUUID())))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void testUpdateTenantBrandingMissingBranches() {
        UpdateTenantBranding useCase = new UpdateTenantBranding(tenantRepo);

        assertThatThrownBy(() -> useCase.updateBranding(null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCase.updateBranding(new UpdateTenantBrandingCommand(TenantId.random(), "logo", "#fff", "#000")))
            .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void testRevokeInviteMissingBranches() {
        RevokeInvite useCase = new RevokeInvite(inviteRepo);

        assertThatThrownBy(() -> useCase.revoke(null))
            .isInstanceOf(IllegalArgumentException.class);

        Tenant t1 = createActiveTenant();
        Tenant t2 = createActiveTenant();
        Invite invite = Invite.create(InviteId.random(), t1.id(), "a@b.com", HierarchyLevel.COLLABORATOR, null, UUID.randomUUID(), null, "token", Instant.now().plusSeconds(3600));
        invitesById.put(invite.id(), invite);

        // Cross tenant revoke
        assertThatThrownBy(() -> useCase.revoke(new RevokeInviteCommand(t2.id(), invite.id())))
            .isInstanceOf(InvalidInviteException.class);

        // Invite accepted
        Invite acceptedInvite = Invite.restore(InviteId.random(), t1.id(), "b@b.com", HierarchyLevel.COLLABORATOR, null, UUID.randomUUID(), null, "token2", InviteStatus.ACCEPTED, Instant.now(), Instant.now().plusSeconds(3600), Instant.now());
        invitesById.put(acceptedInvite.id(), acceptedInvite);
        assertThatThrownBy(() -> useCase.revoke(new RevokeInviteCommand(t1.id(), acceptedInvite.id())))
            .isInstanceOf(InvalidInviteException.class);
    }

    @Test
    void testProvisionUserMissingBranches() {
        ProvisionUser useCase = new ProvisionUser(tenantRepo, userRepo, new BCryptPasswordEncoder());

        assertThatThrownBy(() -> useCase.provision(null))
            .isInstanceOf(IllegalArgumentException.class);

        Tenant t = createActiveTenant();
        TenantId tenantId = t.id();

        // Null vs Blank for email, name, password
        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(tenantId, null, "Name", HierarchyLevel.MANAGER, "pass", null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(tenantId, "  ", "Name", HierarchyLevel.MANAGER, "pass", null, null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(tenantId, "a@b.com", null, HierarchyLevel.MANAGER, "pass", null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(tenantId, "a@b.com", "  ", HierarchyLevel.MANAGER, "pass", null, null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(tenantId, "a@b.com", "Name", HierarchyLevel.MANAGER, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(tenantId, "a@b.com", "Name", HierarchyLevel.MANAGER, "  ", null, null)))
            .isInstanceOf(IllegalArgumentException.class);

        // Inactive tenant
        Tenant inactiveTenant = Tenant.restore(TenantId.random(), "inact", "Inact", TenantStatus.INACTIVE, Instant.now(), Instant.now());
        tenantsById.put(inactiveTenant.id(), inactiveTenant);
        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(inactiveTenant.id(), "a@b.com", "Name", HierarchyLevel.MANAGER, "pass", null, null)))
            .isInstanceOf(TenantNotFoundException.class);

        // Existing email
        createActiveUser(tenantId);
        assertThatThrownBy(() -> useCase.provision(new ProvisionUserCommand(tenantId, "user@test.com", "Name", HierarchyLevel.MANAGER, "pass", null, null)))
            .isInstanceOf(UserEmailAlreadyExistsException.class);

        // Success with null permissions vs non-null permissions
        User prov1 = useCase.provision(new ProvisionUserCommand(tenantId, "new1@test.com", "Name", HierarchyLevel.MANAGER, "pass", null, null));
        assertThat(prov1).isNotNull();

        User prov2 = useCase.provision(new ProvisionUserCommand(tenantId, "new2@test.com", "Name", HierarchyLevel.MANAGER, "pass", null, Set.of(Permission.MANAGE_USERS)));
        assertThat(prov2.permissions()).contains(Permission.MANAGE_USERS);
    }

    @Test
    void testChangeUserHierarchyLevelMissingBranches() {
        ChangeUserHierarchyLevel useCase = new ChangeUserHierarchyLevel(userRepo);

        assertThatThrownBy(() -> useCase.change(null))
            .isInstanceOf(IllegalArgumentException.class);

        UserId actorId = UserId.random();
        UserId targetId = UserId.random();
        assertThatThrownBy(() -> useCase.change(new ChangeUserHierarchyLevelCommand(actorId, targetId, null)))
            .isInstanceOf(IllegalArgumentException.class);

        Tenant t1 = createActiveTenant();
        User actor = createActiveUser(t1.id());
        assertThatThrownBy(() -> useCase.change(new ChangeUserHierarchyLevelCommand(actor.id(), targetId, HierarchyLevel.EXECUTIVE)))
            .isInstanceOf(UserNotFoundException.class);

        Tenant t2 = createActiveTenant();
        User target = createActiveUser(t2.id());
        assertThatThrownBy(() -> useCase.change(new ChangeUserHierarchyLevelCommand(actor.id(), target.id(), HierarchyLevel.EXECUTIVE)))
            .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void testRegisterTenantMissingBranches() {
        ProvisionDefaultRoles rolesMock = mock(ProvisionDefaultRoles.class);
        RegisterTenant useCase = new RegisterTenant(tenantRepo, domainRepo, rolesMock,
            mock(br.com.qsoft.governance.iam.application.BootstrapTenantAdmin.class));

        assertThatThrownBy(() -> useCase.register(null))
            .isInstanceOf(IllegalArgumentException.class);

        // Null vs Blank for slug, name, emailDomain
        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand(
            null, "Name", "domain.com", "Admin", "admin@domain.com", "secret123")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand(
            "  ", "Name", "domain.com", "Admin", "admin@domain.com", "secret123")))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand(
            "slug", null, "domain.com", "Admin", "admin@domain.com", "secret123")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand(
            "slug", "  ", "domain.com", "Admin", "admin@domain.com", "secret123")))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand(
            "slug", "Name", null, "Admin", "admin@domain.com", "secret123")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand(
            "slug", "Name", "  ", "Admin", "admin@domain.com", "secret123")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand(
            "slug", "Name", "@", "Admin", "admin@domain.com", "secret123")))
            .isInstanceOf(IllegalArgumentException.class);

        createActiveTenant();
        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand(
            "tenant1", "Name", "domain.com", "Admin", "admin@domain.com", "secret123")))
            .isInstanceOf(TenantSlugAlreadyExistsException.class);

        domainsMap.put("domain.com", Tenant.create(TenantId.random(), "t2", "T2", "domain.com"));
        assertThatThrownBy(() -> useCase.register(new RegisterTenantCommand(
            "new-slug", "Name", "domain.com", "Admin", "admin@domain.com", "secret123")))
            .isInstanceOf(TenantDomainAlreadyExistsException.class);
    }

    @Test
    void testCreateInviteMissingBranchesAndTokenCollision() {
        AtomicInteger tokenCheckCounter = new AtomicInteger(0);
        InviteRepository collidingRepo = new InviteRepository() {
            @Override public Invite save(Invite invite) { invitesById.put(invite.id(), invite); return invite; }
            @Override public Optional<Invite> findById(InviteId id) { return Optional.ofNullable(invitesById.get(id)); }
            @Override public Optional<Invite> findByTokenHash(String tokenHash) { return Optional.empty(); }
            @Override public boolean existsByTokenHash(String tokenHash) {
                return tokenCheckCounter.getAndIncrement() == 0; // Collides on 1st check, succeeds on 2nd!
            }
            @Override public List<Invite> findByTenantId(TenantId tenantId) { return List.of(); }
        };

        CreateInvite useCase = new CreateInvite(tenantRepo, userRepo, roleRepo, collidingRepo, 48);

        assertThatThrownBy(() -> useCase.create(null))
            .isInstanceOf(IllegalArgumentException.class);

        Tenant t = createActiveTenant();
        TenantId tenantId = t.id();
        roleRepo.save(Role.create(RoleId.random(), tenantId, "MANAGER"));

        // Null vs Blank email
        assertThatThrownBy(() -> useCase.create(new CreateInviteCommand(tenantId, null, HierarchyLevel.MANAGER, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.create(new CreateInviteCommand(tenantId, "  ", HierarchyLevel.MANAGER, null, null)))
            .isInstanceOf(IllegalArgumentException.class);

        // Token collision loop coverage
        IssuedInvite issued = useCase.create(new CreateInviteCommand(tenantId, "invite@test.com", HierarchyLevel.MANAGER, null, null));
        assertThat(issued).isNotNull();
        assertThat(tokenCheckCounter.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testChangeUserStatusMissingBranches() {
        ChangeUserStatus useCase = new ChangeUserStatus(userRepo);

        assertThatThrownBy(() -> useCase.change(null))
            .isInstanceOf(IllegalArgumentException.class);

        Tenant t1 = createActiveTenant();
        User actor = createActiveUser(t1.id());

        // Deactivating self
        assertThatThrownBy(() -> useCase.change(new ChangeUserStatusCommand(actor.id(), actor.id(), UserStatus.INACTIVE)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("próprio usuário");

        // Activating target user
        User target = User.create(UserId.random(), t1.id(), "other@test.com", "Other", HierarchyLevel.COLLABORATOR, null);
        target.deactivate();
        userRepo.save(target);

        User updated = useCase.change(new ChangeUserStatusCommand(actor.id(), target.id(), UserStatus.ACTIVE));
        assertThat(updated.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void testCreateUserMissingBranches() {
        CreateUser useCase = new CreateUser(userRepo, tenantRepo);

        assertThatThrownBy(() -> useCase.create(null))
            .isInstanceOf(IllegalArgumentException.class);

        Tenant t = createActiveTenant();
        TenantId tenantId = t.id();

        // Null vs Blank for email and name
        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(tenantId, null, "Name", HierarchyLevel.MANAGER, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(tenantId, " ", "Name", HierarchyLevel.MANAGER, null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(tenantId, "a@b.com", null, HierarchyLevel.MANAGER, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(tenantId, "a@b.com", " ", HierarchyLevel.MANAGER, null)))
            .isInstanceOf(IllegalArgumentException.class);

        createActiveUser(tenantId);
        assertThatThrownBy(() -> useCase.create(new CreateUserCommand(tenantId, "user@test.com", "Name", HierarchyLevel.MANAGER, null)))
            .isInstanceOf(UserEmailAlreadyExistsException.class);
    }

    @Test
    void testResendInviteMissingBranchesAndTokenCollision() {
        AtomicInteger tokenCheckCounter = new AtomicInteger(0);
        InviteRepository collidingRepo = new InviteRepository() {
            @Override public Invite save(Invite invite) { invitesById.put(invite.id(), invite); return invite; }
            @Override public Optional<Invite> findById(InviteId id) { return Optional.ofNullable(invitesById.get(id)); }
            @Override public Optional<Invite> findByTokenHash(String tokenHash) { return Optional.empty(); }
            @Override public boolean existsByTokenHash(String tokenHash) {
                return tokenCheckCounter.getAndIncrement() == 0; // Collides on 1st check!
            }
            @Override public List<Invite> findByTenantId(TenantId tenantId) { return List.of(); }
        };

        ResendInvite useCase = new ResendInvite(userRepo, collidingRepo, 48);

        assertThatThrownBy(() -> useCase.resend(null))
            .isInstanceOf(IllegalArgumentException.class);

        Tenant t = createActiveTenant();
        Invite acceptedInvite = Invite.restore(InviteId.random(), t.id(), "b@b.com", HierarchyLevel.COLLABORATOR, null, UUID.randomUUID(), null, "token2", InviteStatus.ACCEPTED, Instant.now(), Instant.now().plusSeconds(3600), Instant.now());
        invitesById.put(acceptedInvite.id(), acceptedInvite);

        assertThatThrownBy(() -> useCase.resend(new ResendInviteCommand(t.id(), acceptedInvite.id())))
            .isInstanceOf(InvalidInviteException.class);

        Invite pendingInvite = Invite.create(InviteId.random(), t.id(), "unique-invite@test.com", HierarchyLevel.COLLABORATOR, null, UUID.randomUUID(), null, "token3", Instant.now().plusSeconds(3600));
        invitesById.put(pendingInvite.id(), pendingInvite);

        IssuedInvite renewed = useCase.resend(new ResendInviteCommand(t.id(), pendingInvite.id()));
        assertThat(renewed).isNotNull();
        assertThat(tokenCheckCounter.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void testListUsersMissingBranches() {
        UserSearchRepository mockSearchRepo = (tenantId, filter, page, size) -> new PageResult<>(List.of(), 0L, page, size);
        ListUsers useCase = new ListUsers(mockSearchRepo);

        assertThatThrownBy(() -> useCase.list(null))
            .isInstanceOf(IllegalArgumentException.class);

        TenantId t = TenantId.random();
        PageResult<User> p1 = useCase.list(new ListUsersCommand(t, null, null, null, null, -5, 0));
        assertThat(p1.page()).isZero();
        assertThat(p1.size()).isEqualTo(ListUsers.DEFAULT_SIZE);

        PageResult<User> p2 = useCase.list(new ListUsersCommand(t, null, null, null, null, 1, 500));
        assertThat(p2.size()).isEqualTo(ListUsers.MAX_SIZE);
    }

    @Test
    void testRegisterUserMissingBranches() {
        CreateUser createUserUseCase = new CreateUser(userRepo, tenantRepo);

        AuthorizationQuery authQueryWithReason = mock(AuthorizationQuery.class);
        when(authQueryWithReason.checkPermission(any(), any())).thenReturn(new AuthorizationResult(false, "Permissão negada por política"));

        RegisterUser useCaseWithReason = new RegisterUser(userRepo, authQueryWithReason, createUserUseCase);

        assertThatThrownBy(() -> useCaseWithReason.register(null))
            .isInstanceOf(IllegalArgumentException.class);

        Tenant t1 = createActiveTenant();
        User actor = createActiveUser(t1.id());

        // Null vs Blank for email and name
        assertThatThrownBy(() -> useCaseWithReason.register(new RegisterUserCommand(actor.id(), t1.id(), null, "New", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCaseWithReason.register(new RegisterUserCommand(actor.id(), t1.id(), "  ", "New", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCaseWithReason.register(new RegisterUserCommand(actor.id(), t1.id(), "new@test.com", null, HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCaseWithReason.register(new RegisterUserCommand(actor.id(), t1.id(), "new@test.com", "  ", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> useCaseWithReason.register(new RegisterUserCommand(actor.id(), t1.id(), "new@test.com", "New", null, null)))
            .isInstanceOf(IllegalArgumentException.class);

        // Denial with custom denialReason string
        assertThatThrownBy(() -> useCaseWithReason.register(new RegisterUserCommand(actor.id(), t1.id(), "new@test.com", "New", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(AuthorizationDeniedException.class)
            .hasMessageContaining("Permissão negada por política");

        // Denial without denialReason string (null denialReason)
        AuthorizationQuery authQueryNoReason = mock(AuthorizationQuery.class);
        when(authQueryNoReason.checkPermission(any(), any())).thenReturn(new AuthorizationResult(false, null));
        RegisterUser useCaseNoReason = new RegisterUser(userRepo, authQueryNoReason, createUserUseCase);

        assertThatThrownBy(() -> useCaseNoReason.register(new RegisterUserCommand(actor.id(), t1.id(), "new2@test.com", "New", HierarchyLevel.COLLABORATOR, null)))
            .isInstanceOf(AuthorizationDeniedException.class)
            .hasMessageContaining("Usuário não autorizado a cadastrar usuários.");
    }
}
