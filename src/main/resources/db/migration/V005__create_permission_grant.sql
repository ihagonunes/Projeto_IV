-- V005 - Módulo IAM: matriz de permissões por nível hierárquico (POPROJ-52).
-- A matriz é GLOBAL: sem tenant_id e sem department_id (a mesma para todos os
-- tenants). O escopo de department é aplicado na avaliação (SPI-40),
-- comparando o departmentId do usuário com o do recurso — não fica na tabela.
-- UNIQUE (role_level, resource, action) garante a unicidade da tupla e serve
-- de índice para consulta eficiente por (roleLevel, resource, action).

CREATE TABLE permission_grant (
    id         UUID        NOT NULL,
    role_level VARCHAR(20) NOT NULL,
    resource   VARCHAR(40) NOT NULL,
    action     VARCHAR(20) NOT NULL,

    CONSTRAINT pk_permission_grant PRIMARY KEY (id),
    CONSTRAINT uq_permission_grant_role_resource_action UNIQUE (role_level, resource, action)
);

COMMENT ON TABLE permission_grant IS
    'Matriz global de permissões (role_level, resource, action) por nível hierárquico.';