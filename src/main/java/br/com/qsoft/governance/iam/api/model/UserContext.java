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
    String status,
    String name
) {

    /**
     * Construtor legado sem {@code name}, mantido para não quebrar callers
     * existentes. O adapter de produção usa o construtor completo.
     */
    public UserContext(UserId userId, TenantId tenantId, String hierarchyLevel,
                       UUID departmentId, String status) {
        this(userId, tenantId, hierarchyLevel, departmentId, status, null);
    }
}
