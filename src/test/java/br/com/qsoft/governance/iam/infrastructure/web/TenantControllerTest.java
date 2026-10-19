package br.com.qsoft.governance.iam.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.iam.application.GetCurrentTenant;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.UpdateTenant;
import br.com.qsoft.governance.iam.application.UpdateTenantBranding;
import br.com.qsoft.governance.iam.domain.exception.TenantDomainAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.tenant.TenantStatus;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContext;
import br.com.qsoft.governance.sharedkernel.tenant.TenantContextHolder;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Testes unitários do {@link TenantController} e {@link TenantApiExceptionHandler}.
 * Usa {@code MockMvcBuilders.standaloneSetup} — sem contexto Spring, sem banco.
 */
class TenantControllerTest {

    private RegisterTenant registerTenant;
    private GetCurrentTenant getCurrentTenant;
    private UpdateTenant updateTenant;
    private UpdateTenantBranding updateTenantBranding;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registerTenant = mock(RegisterTenant.class);
        getCurrentTenant = mock(GetCurrentTenant.class);
        updateTenant = mock(UpdateTenant.class);
        updateTenantBranding = mock(UpdateTenantBranding.class);

        TenantController controller = new TenantController(
            registerTenant, getCurrentTenant, updateTenant, updateTenantBranding);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TenantApiExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Tenant sampleTenant(TenantId id) {
        return Tenant.restore(id, "qsoft", "QSOFT", "qsoft.com",
            TenantStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private void setContext(TenantId tenantId) {
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));
    }

    // ── POST /api/v1/tenants ──────────────────────────────────────────────────

    @Test
    void deve_retornar201_quando_tenantCriadoComSucesso() throws Exception {
        TenantId id = TenantId.random();
        when(registerTenant.register(any(RegisterTenantCommand.class))).thenReturn(sampleTenant(id));

        mockMvc.perform(post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"qsoft\",\"name\":\"QSOFT\",\"emailDomain\":\"qsoft.com\","
                    + "\"adminName\":\"Admin\",\"adminEmail\":\"admin@qsoft.com\","
                    + "\"adminPassword\":\"secret123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("qsoft"));
    }

    @Test
    void deve_retornar409_quando_slugJaExiste() throws Exception {
        when(registerTenant.register(any(RegisterTenantCommand.class)))
            .thenThrow(new TenantSlugAlreadyExistsException("qsoft"));

        mockMvc.perform(post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"qsoft\",\"name\":\"QSOFT\",\"emailDomain\":\"qsoft.com\","
                    + "\"adminName\":\"Admin\",\"adminEmail\":\"admin@qsoft.com\","
                    + "\"adminPassword\":\"secret123\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deve_retornar409_quando_dominioJaExiste() throws Exception {
        when(registerTenant.register(any(RegisterTenantCommand.class)))
            .thenThrow(new TenantDomainAlreadyExistsException("qsoft.com"));

        mockMvc.perform(post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"qsoft\",\"name\":\"QSOFT\",\"emailDomain\":\"qsoft.com\","
                    + "\"adminName\":\"Admin\",\"adminEmail\":\"admin@qsoft.com\","
                    + "\"adminPassword\":\"secret123\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deve_retornar400_quando_slugEmBranco() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"\",\"name\":\"QSOFT\",\"emailDomain\":\"qsoft.com\","
                    + "\"adminName\":\"Admin\",\"adminEmail\":\"admin@qsoft.com\","
                    + "\"adminPassword\":\"secret123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deve_retornar400_quando_emailDomainInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"qsoft\",\"name\":\"QSOFT\",\"emailDomain\":\"nao_e_dominio\","
                    + "\"adminName\":\"Admin\",\"adminEmail\":\"admin@qsoft.com\","
                    + "\"adminPassword\":\"secret123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    // ── GET /api/v1/tenants/me ────────────────────────────────────────────────

    @Test
    void deve_retornar200_quando_buscarTenantDoContexto() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        when(getCurrentTenant.current(tenantId)).thenReturn(sampleTenant(tenantId));

        mockMvc.perform(get("/api/v1/tenants/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("qsoft"));
    }

    @Test
    void deve_retornar401_quando_buscarTenantSemContexto() throws Exception {
        // TenantContextHolder vazio → require() lança IllegalStateException → handler → 401
        mockMvc.perform(get("/api/v1/tenants/me"))
            .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/v1/tenants/me ────────────────────────────────────────────────

    @Test
    void deve_retornar200_quando_atualizarTenantComContexto() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        when(updateTenant.update(any())).thenReturn(sampleTenant(tenantId));

        mockMvc.perform(put("/api/v1/tenants/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"QSOFT Atualizado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("qsoft"));
    }

    // ── GET /api/v1/tenants/me/identidade-visual ──────────────────────────────

    @Test
    void deve_retornar200_quando_buscarBrandingDoContexto() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        when(getCurrentTenant.current(tenantId)).thenReturn(sampleTenant(tenantId));

        mockMvc.perform(get("/api/v1/tenants/me/identidade-visual"))
            .andExpect(status().isOk());
    }

    // ── PUT /api/v1/tenants/me/identidade-visual ──────────────────────────────

    @Test
    void deve_retornar200_quando_atualizarBrandingComContexto() throws Exception {
        TenantId tenantId = TenantId.random();
        setContext(tenantId);
        when(updateTenantBranding.updateBranding(any())).thenReturn(sampleTenant(tenantId));

        mockMvc.perform(put("/api/v1/tenants/me/identidade-visual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logoUrl\":null,\"primaryColor\":null,\"secondaryColor\":null}"))
            .andExpect(status().isOk());
    }
}
