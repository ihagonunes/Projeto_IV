package br.com.qsoft.governance.agentpolicies.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de diretório usada apenas pelo seeder de demonstração (tenant demo e
 * departamentos por nome). Não é um contrato de domínio.
 */
public interface DemoDirectoryLookup {

    Optional<UUID> findTenantIdBySlug(String slug);

    Optional<UUID> findDepartmentIdByName(UUID tenantId, String name);
}
