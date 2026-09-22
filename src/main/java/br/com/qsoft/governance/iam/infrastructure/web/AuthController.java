package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.application.AuthenticateUser;
import br.com.qsoft.governance.iam.application.AuthenticatedUser;
import br.com.qsoft.governance.iam.application.LoginCommand;
import br.com.qsoft.governance.iam.application.port.TokenIssuer;
import br.com.qsoft.governance.iam.domain.exception.InvalidCredentialsException;
import br.com.qsoft.governance.iam.domain.exception.TooManyLoginAttemptsException;
import br.com.qsoft.governance.iam.infrastructure.security.LoginAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de login. Autentica as credenciais e emite um JWT Bearer (ADR-30).
 * <p>
 * SPI-67: aplica rate limiting por IP + e-mail <b>antes</b> de autenticar.
 * Cada tentativa inválida conta para a janela; um login bem-sucedido zera o
 * contador. O bloqueio é temporário e a mensagem é genérica (não revela se a
 * conta existe).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateUser authenticateUser;
    private final TokenIssuer tokenIssuer;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthController(AuthenticateUser authenticateUser,
                          TokenIssuer tokenIssuer,
                          LoginAttemptLimiter loginAttemptLimiter) {
        this.authenticateUser = authenticateUser;
        this.tokenIssuer = tokenIssuer;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                              HttpServletRequest httpRequest) {
        String key = loginAttemptLimiter.key(request.email(), httpRequest.getRemoteAddr());
        if (loginAttemptLimiter.isBlocked(key)) {
            throw new TooManyLoginAttemptsException();
        }

        AuthenticatedUser user;
        try {
            user = authenticateUser.login(
                new LoginCommand(request.tenantSlug(), request.email(), request.password()));
        } catch (InvalidCredentialsException ex) {
            loginAttemptLimiter.recordFailure(key);
            throw ex;
        }

        loginAttemptLimiter.reset(key);
        String token = tokenIssuer.issue(user);
        return ResponseEntity.ok(new AuthResponse(token, "Bearer"));
    }
}
