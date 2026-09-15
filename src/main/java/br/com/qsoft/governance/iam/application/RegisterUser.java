package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.api.model.AuthorizationResult;
import br.com.qsoft.governance.iam.api.model.Permission;
import br.com.qsoft.governance.iam.api.query.AuthorizationQuery;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.AuthorizationDeniedException;
import br.com.qsoft.governance.iam.domain.exception.UserNotFoundException;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de cadastro (registro) de usuário com papel hierárquico dentro
 * de um tenant, executado pelo administrador do tenant.
 *
 * <p>Responsabilidades (nesta ordem):
 * <ol>
 *   <li>validar o comando (campos obrigatórios);</li>
 *   <li>localizar o ator (usuário autenticado que está cadastrando);</li>
 *   <li>autorizar determinística e unicamente via {@link AuthorizationQuery}:
 *       exige a permissão {@code MANAGE_USERS} (ADR-04);</li>
 *   <li>garantir o escopo de tenant: o ator só cadastra dentro do PRÓPRIO
 *       tenant (ADR-03) — TenantId vem do contexto autenticado, nunca do
 *       cliente;</li>
 *   <li>delegar a criação efetiva ao {@link CreateUser}, que mantém as regras
 *       de tenant ACTIVE, e-mail único por tenant e papel obrigatório.</li>
 * </ol>
 *
 * <p>Este caso de uso espelha o padrão de {@link ChangeUserHierarchyLevel}
 * (actor + permissão + mesmo tenant) e não duplica a lógica de criação que
 * já existe em {@link CreateUser}.
 */
@Service
public class RegisterUser {

    private final UserRepository userRepository;
    private final AuthorizationQuery authorizationQuery;
    private final CreateUser createUser;

    public RegisterUser(UserRepository userRepository, AuthorizationQuery authorizationQuery,
                        CreateUser createUser) {
        this.userRepository = userRepository;
        this.authorizationQuery = authorizationQuery;
        this.createUser = createUser;
    }

    /**
     * Registra um novo usuário com papel hierárquico.
     *
     * @param command dados do cadastro (ator + destino + dados do usuário)
     * @return o usuário recém-criado e persistido
     * @throws IllegalArgumentException    se o comando for nulo ou houver campo
     *                                     obrigatório ausente (ex.: papel nulo)
     * @throws UserNotFoundException       se o ator não existir
     * @throws AuthorizationDeniedException se o ator não tiver {@code MANAGE_USERS}
     *                                     ou estiver tentando cadastrar em outro tenant
     */
    public User register(RegisterUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command é obrigatório.");
        }

        // 1) Campos obrigatórios do comando (mesmo estilo de validação do projeto).
        UserId actorId = Objects.requireNonNull(command.actorUserId(), "actorUserId");
        TenantId tenantId = Objects.requireNonNull(command.tenantId(), "tenantId");
        String email = requireNonBlank(command.email(), "email");
        String name = requireNonBlank(command.name(), "name");
        // Papel hierárquico obrigatório (cenário BDD do Ihan): validação explícita
        // aqui para dar a mensagem de domínio "obrigatório" antes da delegação.
        // Convenção do projeto (ChangeUserHierarchyLevel): IllegalArgumentException
        // para campo obrigatório — Objects.requireNonNull lançaria NullPointerException.
        HierarchyLevel level = command.hierarchyLevel();
        if (level == null) {
            throw new IllegalArgumentException("hierarchyLevel é obrigatório.");
        }

        // 2) TODO ator existe no sistema? Sem ele, nenhuma autorização é possível.
        User actor = userRepository.findById(actorId)
            .orElseThrow(() -> new UserNotFoundException(actorId));

        // 3) AUTORIZAÇÃO (ADR-04): cadastrar usuário exige a permissão MANAGE_USERS.
        //    A decisão é determinística e feita pelo IAM — nunca por frontend/LLM.
        AuthorizationResult authorization =
            authorizationQuery.checkPermission(actorId, Permission.MANAGE_USERS);
        if (!authorization.allowed()) {
            throw new AuthorizationDeniedException(
                authorization.denialReason() != null
                    ? authorization.denialReason()
                    : "Usuário não autorizado a cadastrar usuários.");
        }

        // 4) ESCOPO DE TENANT (ADR-03): o ator só cria usuários no PRÓPRIO tenant.
        //    Nunca confiamos em tenant enviado pelo frontend quando o contexto
        //    autenticado permite derivar o tenant do ator.
        if (!actor.tenantId().equals(tenantId)) {
            throw new AuthorizationDeniedException(
                "Usuário não autorizado a cadastrar usuário em outro tenant.");
        }

        // 5) Delegação ao CreateUser: reutiliza as regras existentes de
        //    tenant ACTIVE, unicidade de e-mail por tenant e status inicial.
        return createUser.create(new CreateUserCommand(
            tenantId, email, name, level, command.departmentId()));
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório.");
        }
        return value.trim();
    }
}