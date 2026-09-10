package br.com.qsoft.governance.iam.api.model;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/**
 * Representação pública do contexto de um usuário, consumível por outros
 * módulos via <code>iam.api</code>. Não expõe tipos internos do domínio,
 * apenas o nível hierárquico e o status como atributos primitivos,
 * para não vazar a fronteira <code>iam.domain</code> (ADR-31).
 */
public record UserContext(
    UserId userId,
    TenantId tenantId,
    String hierarchyLevel,
    UUID departmentId,
    String status
) {
}
