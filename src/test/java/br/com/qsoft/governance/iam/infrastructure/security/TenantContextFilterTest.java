package br.com.qsoft.governance.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantContextFilterTest {

    private final Map<TenantId, Tenant> tenantsById = new LinkedHashMap<>();
    private final Map<UserId, User> usersById = new LinkedHashMap<>();

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
            return Optional.empty();
        }

        @Override
        public boolean existsBySlug(String slug) {
            return false;
        }
    };

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
            return Optional.empty();
        }

        @Override
        public boolean existsByTenantIdAndEmail(TenantId tenantId, String email) {
            return false;
        }
    };

    private final TenantContextFilter filter = new TenantContextFilter(tenantRepository, userRepository);

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private Tenant activeTenant(TenantId id) {
        Tenant tenant = Tenant.restore(id, "qsoft", "QSOFT", TenantStatus.ACTIVE, Instant.now(), Instant.now());
        tenantsById.put(id, tenant);
        return tenant;
    }

    private JwtAuthenticationToken jwtAuthentication(UserId userId, TenantId tenantId) {
        Jwt jwt = Jwt.withTokenValue("token.a.b")
            .header("alg", "HS256")
            .header("typ", "JWT")
            .subject(userId.value().toString())
            .claim("tenant_id", tenantId.value().toString())
            .claim("iat", Instant.now())
            .claim("exp", Instant.now().plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void shouldSetTenantContextWhenTokenIsValid() throws Exception {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        activeTenant(tenantId);
        usersById.put(userId, User.create(userId, tenantId, "ana@qsoft.com", "Ana",
            HierarchyLevel.MANAGER, null));

        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication(userId, tenantId));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // O contexto é propagado durante o processamento e limpo no finally.
        java.util.concurrent.atomic.AtomicReference<TenantContext> captured = new java.util.concurrent.atomic.AtomicReference<>();
        filter.doFilter(new MockHttpServletRequest(), response,
            (request, res) -> captured.set(TenantContextHolder.get()));

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().tenantId()).isEqualTo(tenantId);
        assertThat(captured.get().userId()).isEqualTo(userId);
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void shouldRejectInactiveUser() throws Exception {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        activeTenant(tenantId);
        User inactive = User.create(userId, tenantId, "ana@qsoft.com", "Ana", HierarchyLevel.MANAGER, null);
        inactive.deactivate();
        usersById.put(userId, inactive);

        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication(userId, tenantId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(TenantContextHolder.get()).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldRejectInactiveTenant() throws Exception {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        tenantsById.put(tenantId, Tenant.restore(tenantId, "x", "X", TenantStatus.INACTIVE,
            Instant.now(), Instant.now()));

        SecurityContextHolder.getContext().setAuthentication(jwtAuthentication(userId, tenantId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(TenantContextHolder.get()).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void shouldSkipWhenRequestNotAuthenticated() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(TenantContextHolder.get()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
