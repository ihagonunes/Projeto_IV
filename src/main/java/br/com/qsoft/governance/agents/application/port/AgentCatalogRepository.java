package br.com.qsoft.governance.agents.application.port;

import br.com.qsoft.governance.agents.domain.AgentDefinition;
import java.util.List;
import java.util.Optional;

/**
 * Leitura do catálogo global de agentes (ADR-44). O catálogo é semeado por
 * Flyway; a aplicação não o altera.
 */
public interface AgentCatalogRepository {

    List<AgentDefinition> findAll();

    Optional<AgentDefinition> findByAgentKey(String agentKey, int version);

    /** Versão mais recente da definição (maior `version`). */
    Optional<AgentDefinition> findLatestByAgentKey(String agentKey);

    boolean existsByAgentKey(String agentKey);
}
