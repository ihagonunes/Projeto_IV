package br.com.qsoft.governance.iam.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.iam.application.ChangeUserHierarchyLevel;
import br.com.qsoft.governance.iam.application.ChangeUserStatus;
import br.com.qsoft.governance.iam.application.CreateUser;
import br.com.qsoft.governance.iam.application.ListUsers;
import br.com.qsoft.governance.iam.application.UpdateUser;
import br.com.qsoft.governance.iam.application.rbac.PolicyEvaluator;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.rbac.Permission;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.query.PageResult;
import br.com.qsoft.governance.sharedkernel.rbac.Action;
import br.com.qsoft.governance.sharedkernel.rbac.Resource;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Testes unitários do {@link UserController} e {@link UserApiExceptionHandler}.
 * Usa {@code MockMvcBuilders.standaloneSetup} — sem contexto Spring, sem banco.
 */
class UserControllerTest {

    private CreateUser createUser;
    private ChangeUserHierarchyLevel changeUserHierarchyLevel;
    private ChangeUserStatus changeUserStatus;
    private ListUsers listUsers;
    private UpdateUser updateUser;
    private PolicyEvaluator policyEvaluator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createUser = mock(CreateUser.class);
        changeUserHierarchyLevel = mock(ChangeUserHierarchyLevel.class);
        changeUserStatus = mock(ChangeUserStatus.class);
        listUsers = mock(ListUsers.class);
        updateUser = mock(UpdateUser.class);
        policyEvaluator = mock(PolicyEvaluator.class);

        UserController controller = new UserController(
            createUser, changeUserHierarchyLevel, changeUserStatus,
            listUsers, updateUser, policyEvaluator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new UserApiExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setContext(TenantId tenantId) {
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));
    }

    private User sampleUser(TenantId tenantId) {
        return User.restore(
            UserId.random(), tenantId, "dev@qsoft.com", "Dev",
            HierarchyLevel.COLLABORATOR, null, "hash", Set.of(),
            UserStatus.ACTIVE, Instant.now(), Instant.now()
        );
    }

    // ── POST /api/v1/usuarios ─────────────────────────────────────────────────

    @Test
    void deve_retornar201_quando_criarUsuarioComSucesso() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        when(createUser.create(any())).thenReturn(sampleUser(tenantId));

        mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@qsoft.com\",\"name\":\"Dev\",\"hierarchyLevel\":\"COLLABORATOR\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("dev@qsoft.com"));
    }

    @Test
    void deve_retornar409_quando_emailJaExiste() throws Exception {
        setContext(TenantId.random());
        when(createUser.create(any()))
            .thenThrow(new UserEmailAlreadyExistsException("dev@qsoft.com"));

        mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@qsoft.com\",\"name\":\"Dev\",\"hierarchyLevel\":\"COLLABORATOR\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deve_retornar400_quando_emailInvalidoAoCriarUsuario() throws Exception {
        setContext(TenantId.random());

        mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalido\",\"name\":\"Dev\",\"hierarchyLevel\":\"COLLABORATOR\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deve_retornar401_quando_criarUsuarioSemContexto() throws Exception {
        // TenantContextHolder vazio → require() lança IllegalStateException → handler → 401
        mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@qsoft.com\",\"name\":\"Dev\",\"hierarchyLevel\":\"COLLABORATOR\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/usuarios ──────────────────────────────────────────────────

    @Test
    void deve_retornar200_quando_listarUsuarios() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        User user = sampleUser(tenantId);
        when(listUsers.list(any())).thenReturn(
            new PageResult<>(List.of(user), 1L, 0, 20));

        mockMvc.perform(get("/api/v1/usuarios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].email").value("dev@qsoft.com"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── PATCH /api/v1/usuarios/{id} ───────────────────────────────────────────

    @Test
    void deve_retornar200_quando_atualizarUsuario() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        when(updateUser.update(any())).thenReturn(sampleUser(tenantId));

        UUID deptId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/usuarios/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":\"" + deptId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("dev@qsoft.com"));
    }

    @Test
    void deve_retornar404_quando_usuarioNaoEncontrado() throws Exception {
        setContext(TenantId.random());
        when(updateUser.update(any()))
            .thenThrow(new UserNotFoundException(UserId.random()));

        UUID deptId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/usuarios/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":\"" + deptId + "\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").exists());
    }

    // ── PATCH /api/v1/usuarios/{id}/status ────────────────────────────────────

    @Test
    void deve_retornar200_quando_alterarStatusDoUsuario() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        when(changeUserStatus.change(any())).thenReturn(sampleUser(tenantId));

        mockMvc.perform(patch("/api/v1/usuarios/" + UUID.randomUUID() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}"))
            .andExpect(status().isOk());
    }

    // ── POST /api/v1/usuarios/{id}/nivel ──────────────────────────────────────

    @Test
    void deve_retornar200_quando_alterarNivelHierarquico() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        when(changeUserHierarchyLevel.change(any())).thenReturn(sampleUser(tenantId));

        mockMvc.perform(post("/api/v1/usuarios/" + UUID.randomUUID() + "/nivel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hierarchyLevel\":\"MANAGER\"}"))
            .andExpect(status().isOk());
    }

    // ── GET /api/v1/usuarios/me/permissoes ────────────────────────────────────

    @Test
    void deve_retornar200_quando_buscarPermissoesDoProprioUsuario() throws Exception {
        TenantId tenantId = TenantId.random();
        UserId userId = UserId.random();
        TenantContextHolder.set(new TenantContext(tenantId, userId));
        when(policyEvaluator.effectivePermissions(any()))
            .thenReturn(Set.of(Permission.of(Resource.USER, Action.READ)));

        mockMvc.perform(get("/api/v1/usuarios/me/permissoes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId.value().toString()))
            .andExpect(jsonPath("$.permissions").isArray());
    }

    // ── GET /api/v1/usuarios/{id}/permissoes ──────────────────────────────────

    @Test
    void deve_retornar200_quando_buscarPermissoesDeOutroUsuario() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        UUID targetId = UUID.randomUUID();
        when(policyEvaluator.effectivePermissions(any())).thenReturn(Set.of());

        mockMvc.perform(get("/api/v1/usuarios/" + targetId + "/permissoes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(targetId.toString()));
    }
}
