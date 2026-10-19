package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/**
 * Dados de entrada da listagem/busca paginada de usuários (SPI-62).
 * {@code actor} é o usuário autenticado (de onde vem o recorte de visibilidade).
 */
public record ListUsersCommand(
    TenantId tenantId,
    UserId actor,
    HierarchyLevel hierarchyLevel,
    UUID departmentId,
    String name,
    String email,
    int page,
    int size
) {
}
