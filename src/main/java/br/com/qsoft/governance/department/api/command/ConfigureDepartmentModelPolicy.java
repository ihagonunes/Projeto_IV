package br.com.qsoft.governance.department.api.command;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.Collection;
import java.util.UUID;

/**
 * Contrato público (comando) do módulo {@code department} para configurar a
 * ModelPolicy de um departamento (ADR-07).
 * <p>
 * Quem <b>orquestra</b> é o {@code aigateway}: ele valida os {@code ModelIds} no
 * seu catálogo e então chama este comando — assim a dependência é
 * {@code aigateway → department} e não há ciclo (ADR-07).
 * <p>
 * O {@code department} continua dono do dado e revalida o invariante do ADR-08
 * (default obrigatório e pertencente ao conjunto quando há permitidos).
 */
public interface ConfigureDepartmentModelPolicy {

    void configure(TenantId tenantId, UUID departmentId,
                   Collection<UUID> allowedModelIds, UUID defaultModelId);
}
