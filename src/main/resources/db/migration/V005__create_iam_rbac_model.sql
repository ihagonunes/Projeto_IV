-- V005 - Módulo IAM: modelo de permissões (RBAC) — SPI-39.
-- Papéis (roles) por tenant, suas permissões granulares (resource × action)
-- e as atribuições (policy) de papel a usuário, com escopo opcional de Department.
-- FKs apenas intramodule (tenant/user/role). O Department é referenciado apenas
-- por department_id (UUID), sem FK cross-module e sem duplicar o aggregate
-- Department no IAM (ADR-05/ADR-29).

CREATE TABLE iam_roles (
    id         UUID         NOT NULL,
    tenant_id  UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_iam_roles PRIMARY KEY (id),
    CONSTRAINT uq_iam_roles_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_iam_roles_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenants (id)
);

CREATE TABLE iam_role_permissions (
    role_id  UUID        NOT NULL,
    resource VARCHAR(40) NOT NULL,
    action   VARCHAR(20) NOT NULL,

    CONSTRAINT pk_iam_role_permissions PRIMARY KEY (role_id, resource, action),
    CONSTRAINT fk_iam_role_permissions_role FOREIGN KEY (role_id) REFERENCES iam_roles (id)
);

CREATE TABLE iam_role_assignments (
    id            UUID NOT NULL,
    tenant_id     UUID NOT NULL,
    user_id       UUID NOT NULL,
    role_id       UUID NOT NULL,
    department_id UUID,

    CONSTRAINT pk_iam_role_assignments PRIMARY KEY (id),
    CONSTRAINT fk_iam_role_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES iam_tenants (id),
    CONSTRAINT fk_iam_role_assignments_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_iam_role_assignments_role FOREIGN KEY (role_id) REFERENCES iam_roles (id)
);

CREATE INDEX idx_iam_role_assignments_user ON iam_role_assignments (tenant_id, user_id);
