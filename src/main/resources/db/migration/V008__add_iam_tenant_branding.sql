-- V008 - Módulo IAM: identidade visual (white-label) do tenant (SPI-53).
-- Logo e cores primária/secundária por tenant, para customização da UI.
-- O armazenamento do binário do logotipo é responsabilidade futura (upload);
-- aqui persistimos a referência (URL) e as cores.

ALTER TABLE iam_tenants ADD COLUMN logo_url VARCHAR(500);
ALTER TABLE iam_tenants ADD COLUMN primary_color VARCHAR(9);
ALTER TABLE iam_tenants ADD COLUMN secondary_color VARCHAR(9);

COMMENT ON COLUMN iam_tenants.logo_url IS 'URL/referência do logotipo do tenant (white-label).';
COMMENT ON COLUMN iam_tenants.primary_color IS 'Cor primária da UI do tenant no formato #RRGGBB.';
COMMENT ON COLUMN iam_tenants.secondary_color IS 'Cor secundária da UI do tenant no formato #RRGGBB.';
