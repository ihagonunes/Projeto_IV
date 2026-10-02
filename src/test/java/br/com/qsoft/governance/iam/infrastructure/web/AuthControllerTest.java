package br.com.qsoft.governance.iam.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.qsoft.governance.iam.application.AuthenticateUser;
import br.com.qsoft.governance.iam.application.AuthenticatedUser;
import br.com.qsoft.governance.iam.application.LoginCommand;
import br.com.qsoft.governance.iam.application.port.TokenIssuer;
import br.com.qsoft.governance.iam.domain.exception.InvalidCredentialsException;
import br.com.qsoft.governance.iam.domain.exception.TooManyLoginAttemptsException;
import br.com.qsoft.governance.iam.infrastructure.security.LoginAttemptLimiter;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Testes unitários do {@link AuthController}.
 * Usa {@code MockMvcBuilders.standaloneSetup} — sem contexto Spring, sem banco.
 */
class AuthControllerTest {

    private AuthenticateUser authenticateUser;
    private TokenIssuer tokenIssuer;
    private LoginAttemptLimiter loginAttemptLimiter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authenticateUser = mock(AuthenticateUser.class);
        tokenIssuer = mock(TokenIssuer.class);
        loginAttemptLimiter = mock(LoginAttemptLimiter.class);

        AuthController controller = new AuthController(authenticateUser, tokenIssuer, loginAttemptLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new AuthExceptionHandler())
            .build();
    }

    // ── login — sucesso ───────────────────────────────────────────────────────

    @Test
    void deve_retornarToken_quando_credenciaisValidas() throws Exception {
        when(loginAttemptLimiter.key(anyString(), anyString())).thenReturn("key");
        when(loginAttemptLimiter.isBlocked("key")).thenReturn(false);

        AuthenticatedUser auth = new AuthenticatedUser(
            UserId.random(), TenantId.random(), "admin@qsoft.com", "COLLABORATOR");
        when(authenticateUser.login(any(LoginCommand.class))).thenReturn(auth);
        when(tokenIssuer.issue(auth)).thenReturn("jwt-token");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@qsoft.com\",\"password\":\"secret123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(loginAttemptLimiter).reset("key");
    }

    // ── login — rate limit ────────────────────────────────────────────────────

    @Test
    void deve_retornar429_quando_ipBloqueadoPorRateLimit() throws Exception {
        when(loginAttemptLimiter.key(anyString(), anyString())).thenReturn("key");
        when(loginAttemptLimiter.isBlocked("key")).thenReturn(true);
        when(loginAttemptLimiter.retryAfterSeconds("key")).thenReturn(60L);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@qsoft.com\",\"password\":\"secret123\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.message").exists());

        verify(authenticateUser, never()).login(any());
    }

    // ── login — credenciais inválidas ─────────────────────────────────────────

    @Test
    void deve_retornar401_quando_credenciaisInvalidas() throws Exception {
        when(loginAttemptLimiter.key(anyString(), anyString())).thenReturn("key");
        when(loginAttemptLimiter.isBlocked("key")).thenReturn(false);
        when(authenticateUser.login(any(LoginCommand.class)))
            .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@qsoft.com\",\"password\":\"errada\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Credenciais inválidas."));

        verify(loginAttemptLimiter).recordFailure("key");
    }

    // ── login — validação de request ──────────────────────────────────────────

    @Test
    void deve_retornar400_quando_emailEmBranco() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"secret123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deve_retornar400_quando_passwordEmBranco() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@qsoft.com\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }
}
