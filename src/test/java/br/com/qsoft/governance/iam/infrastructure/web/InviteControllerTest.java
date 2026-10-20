package br.com.qsoft.governance.iam.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.iam.application.AcceptInvite;
import br.com.qsoft.governance.iam.application.CreateInvite;
import br.com.qsoft.governance.iam.application.IssuedInvite;
import br.com.qsoft.governance.iam.application.ListInvites;
import br.com.qsoft.governance.iam.application.ResendInvite;
import br.com.qsoft.governance.iam.application.RevokeInvite;
import br.com.qsoft.governance.iam.domain.exception.InvalidInviteException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.iam.domain.invite.InviteStatus;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.iam.domain.user.UserStatus;
import br.com.qsoft.governance.sharedkernel.id.InviteId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
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
 * Testes unitários do {@link InviteController} e {@link InviteApiExceptionHandler}.
 * Usa {@code MockMvcBuilders.standaloneSetup} — sem contexto Spring, sem banco.
 */
class InviteControllerTest {

    private CreateInvite createInvite;
    private AcceptInvite acceptInvite;
    private ResendInvite resendInvite;
    private RevokeInvite revokeInvite;
    private ListInvites listInvites;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createInvite = mock(CreateInvite.class);
        acceptInvite = mock(AcceptInvite.class);
        resendInvite = mock(ResendInvite.class);
        revokeInvite = mock(RevokeInvite.class);
        listInvites = mock(ListInvites.class);

        InviteController controller = new InviteController(
            createInvite, acceptInvite, resendInvite, revokeInvite, listInvites);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new InviteApiExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setContext() {
        TenantContextHolder.set(new TenantContext(TenantId.random(), UserId.random()));
    }

    private Invite sampleInvite(TenantId tenantId) {
        return Invite.restore(
            InviteId.random(), tenantId, "dev@qsoft.com",
            HierarchyLevel.COLLABORATOR, null,
            UUID.randomUUID(), null,
            "hash-abc", InviteStatus.PENDING,
            Instant.now(), Instant.now().plusSeconds(86400), null
        );
    }

    private IssuedInvite sampleIssuedInvite(TenantId tenantId) {
        return new IssuedInvite(sampleInvite(tenantId), "tok-abc");
    }

    private User sampleUser() {
        return User.restore(
            UserId.random(), TenantId.random(), "dev@qsoft.com", "Dev",
            HierarchyLevel.COLLABORATOR, null, "hash",
            UserStatus.ACTIVE, Instant.now(), Instant.now()
        );
    }

    // ── GET /api/v1/convites ──────────────────────────────────────────────────

    @Test
    void deve_retornar200_quando_listarConvitesComContexto() throws Exception {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));
        when(listInvites.list(any(), any())).thenReturn(List.of(sampleInvite(tenantId)));

        mockMvc.perform(get("/api/v1/convites"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("dev@qsoft.com"));
    }

    // ── POST /api/v1/convites ─────────────────────────────────────────────────

    @Test
    void deve_retornar201_quando_criarConviteComSucesso() throws Exception {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));
        when(createInvite.create(any())).thenReturn(sampleIssuedInvite(tenantId));

        mockMvc.perform(post("/api/v1/convites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@qsoft.com\",\"roleKey\":\"COLLABORATOR\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").value("tok-abc"));
    }

    @Test
    void deve_retornar400_quando_emailInvalidoAoCriarConvite() throws Exception {
        setContext();

        mockMvc.perform(post("/api/v1/convites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalido\",\"roleKey\":\"COLLABORATOR\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deve_retornar409_quando_emailJaExiste() throws Exception {
        setContext();
        when(createInvite.create(any()))
            .thenThrow(new UserEmailAlreadyExistsException("dev@qsoft.com"));

        mockMvc.perform(post("/api/v1/convites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@qsoft.com\",\"roleKey\":\"COLLABORATOR\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
    }

    // ── POST /api/v1/convites/aceitar ─────────────────────────────────────────

    @Test
    void deve_retornar200_quando_aceitarConviteValido() throws Exception {
        when(acceptInvite.accept(any())).thenReturn(sampleUser());

        mockMvc.perform(post("/api/v1/convites/aceitar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok-abc\",\"name\":\"Dev\",\"password\":\"secret123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("dev@qsoft.com"));
    }

    @Test
    void deve_retornar400_quando_tokenInvalido() throws Exception {
        when(acceptInvite.accept(any()))
            .thenThrow(new InvalidInviteException());

        mockMvc.perform(post("/api/v1/convites/aceitar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok-invalido\",\"name\":\"Dev\",\"password\":\"secret123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    // ── POST /api/v1/convites/{id}/reenviar ───────────────────────────────────

    @Test
    void deve_retornar200_quando_reenviarConvite() throws Exception {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));
        when(resendInvite.resend(any())).thenReturn(sampleIssuedInvite(tenantId));

        mockMvc.perform(post("/api/v1/convites/" + UUID.randomUUID() + "/reenviar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists());
    }

    // ── DELETE /api/v1/convites/{id} ──────────────────────────────────────────

    @Test
    void deve_retornar204_quando_revogarConvite() throws Exception {
        setContext();
        doNothing().when(revokeInvite).revoke(any());

        mockMvc.perform(delete("/api/v1/convites/" + UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
