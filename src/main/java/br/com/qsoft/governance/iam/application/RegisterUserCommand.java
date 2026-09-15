package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.UUID;

/**
 * Dados de entrada para registrar (cadastrar) um usuário com papel hierárquico
 * dentro de um tenant.
 *
 * <p>{@code actorUserId} identifica QUEM está realizando a ação (vem do
 * contexto autenticado, nunca do frontend — ADR-03). O {@code tenantId} é o
 * tenant de DESTINO do novo usuário e deve ser obrigatoriamente o mesmo tenant
 * do ator.
 *
 * @param actorUserId   id do usuário autenticado que está cadastrando
 * @param tenantId      tenant onde o novo usuário será criado
 * @param email         e-mail do novo usuário (único dentro do tenant)
 * @param name          nome de exibição do novo usuário
 * @param hierarchyLevel papel hierárquico (COLLABORATOR, LEADER, MANAGER,
 *                       EXECUTIVE) — obrigatório (cenário BDD do Ihan)
 * @param departmentId  departamento ao qual o usuário pertence (opcional;
 *                      vinculação funcional com o módulo department)
 */
public record RegisterUserCommand(
    UserId actorUserId,
    TenantId tenantId,
    String email,
    String name,
    HierarchyLevel hierarchyLevel,
    UUID departmentId
) {
}