-- V004 - Módulo IAM: permissões administrativas do usuário (RBAC, ADR-04).
-- ADMIN é o conjunto completo de permissões; não é nível hierárquico.

CREATE TABLE user_permission (
    user_id    UUID        NOT NULL,
    permission VARCHAR(40) NOT NULL,

    CONSTRAINT pk_user_permission PRIMARY KEY (user_id, permission),
    CONSTRAINT fk_user_permission_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
