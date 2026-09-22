-- V007 - Módulo IAM: domínio de e-mail corporativo do tenant (SPI-54).
-- O domínio é ÚNICO por tenant: impede que duas organizações registrem o mesmo
-- domínio corporativo, o que tornaria ambígua a resolução do tenant no login
-- (ADR-32, emendado por esta task).
-- A coluna é anulável para acomodar tenants legados sem domínio cadastrado;
-- o índice UNIQUE (que no PostgreSQL admite múltiplos NULL) garante a
-- unicidade dos domínios efetivamente cadastrados.

ALTER TABLE iam_tenants ADD COLUMN email_domain VARCHAR(253);

CREATE UNIQUE INDEX uq_iam_tenants_email_domain ON iam_tenants (email_domain);

COMMENT ON COLUMN iam_tenants.email_domain IS
    'Domínio de e-mail corporativo do tenant (único globalmente). Base da resolução do tenant no login (ADR-32).';
