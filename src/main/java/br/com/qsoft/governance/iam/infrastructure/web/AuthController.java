package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.application.AuthenticateUser;
import br.com.qsoft.governance.iam.application.AuthenticatedUser;
import br.com.qsoft.governance.iam.application.LoginCommand;
import br.com.qsoft.governance.iam.application.port.TokenIssuer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de login. Autentica as credenciais e emite um JWT Bearer (ADR-30).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateUser authenticateUser;
    private final TokenIssuer tokenIssuer;

    public AuthController(AuthenticateUser authenticateUser, TokenIssuer tokenIssuer) {
        this.authenticateUser = authenticateUser;
        this.tokenIssuer = tokenIssuer;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthenticatedUser user = authenticateUser.login(
            new LoginCommand(request.tenantSlug(), request.email(), request.password()));
        String token = tokenIssuer.issue(user);
        return ResponseEntity.ok(new AuthResponse(token, "Bearer"));
    }
}
