package br.com.qsoft.governance.iam.infrastructure.web;

import br.com.qsoft.governance.iam.application.GrantView;
import br.com.qsoft.governance.iam.application.UserAccessView;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resposta de {@code GET /usuarios/me/permissoes} e {@code /{id}/permissoes}
 * (SPEC-01 §7.3): concessões com escopo + união das permissões ("RESOURCE:ACTION").
 */
public record UserAccessResponse(
    UUID userId,
    List<ConcessionResponse> concessoes,
    Set<String> permissoes
) {

    public record ConcessionResponse(
        UUID id,
        String papel,
        String escopo,
        UUID departmentId,
        String origem
    ) {

        static ConcessionResponse from(GrantView grant) {
            return new ConcessionResponse(
                grant.id(), grant.role(), grant.scope(), grant.departmentId(), grant.origin());
        }
    }

    public static UserAccessResponse from(UserAccessView view) {
        List<ConcessionResponse> concessions = view.concessions().stream()
            .map(ConcessionResponse::from)
            .toList();
        Set<String> permissions = view.permissions().stream()
            .map(permission -> permission.resource().name() + ":" + permission.action().name())
            .collect(Collectors.toUnmodifiableSet());
        return new UserAccessResponse(view.userId(), concessions, permissions);
    }
}
