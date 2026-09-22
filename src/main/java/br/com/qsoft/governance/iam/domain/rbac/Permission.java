package br.com.qsoft.governance.iam.domain.rbac;

import java.util.Objects;

/**
 * Permissão granular do modelo RBAC: um par (recurso, ação).
 * É a unidade atômica concedida por papéis e avaliada pelo policy evaluator.
 */
public record Permission(Resource resource, Action action) {

    public Permission {
        Objects.requireNonNull(resource, "resource não pode ser nulo.");
        Objects.requireNonNull(action, "action não pode ser nula.");
    }

    public static Permission of(Resource resource, Action action) {
        return new Permission(resource, action);
    }
}
