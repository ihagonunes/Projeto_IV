package br.com.qsoft.governance.iam.domain.rbac;

import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matriz de permissões PADRÃO (SPI-42): mapeia o nível hierárquico para o
 * conjunto de permissões (recurso × ação) concedidas por padrão.
 *
 * Invariante: COLLABORATOR ⊆ LEADER ⊆ MANAGER ⊆ EXECUTIVE (cada nível herda
 * o anterior). ADMIN não é nível hierárquico; corresponde ao conjunto completo
 * de permissões (ADR-04).
 *
 * Esta é uma especificação de domínio (sem I/O). A persistência/seed é tratada
 * em task própria (SPI-43).
 */
public final class PermissionMatrix {

    private static final Map<HierarchyLevel, Set<Permission>> DEFAULTS = buildDefaults();

    private PermissionMatrix() {
    }

    public static Set<Permission> permissionsFor(HierarchyLevel level) {
        Objects.requireNonNull(level, "level é obrigatório.");
        return DEFAULTS.getOrDefault(level, Set.of());
    }

    public static boolean allows(HierarchyLevel level, Permission permission) {
        return permissionsFor(level).contains(permission);
    }

    /** Ações concedidas por padrão para um nível sobre um recurso (área). */
    public static Set<Action> actionsFor(HierarchyLevel level, Resource resource) {
        Objects.requireNonNull(resource, "resource é obrigatório.");
        return permissionsFor(level).stream()
            .filter(p -> p.resource() == resource)
            .map(Permission::action)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Map<HierarchyLevel, Set<Permission>> buildDefaults() {
        Set<Permission> collaborator = new HashSet<>();
        collaborator.add(p(Resource.CHAT, Action.CREATE));
        collaborator.add(p(Resource.CHAT, Action.READ));
        collaborator.add(p(Resource.KNOWLEDGE_DOCUMENT, Action.READ));
        collaborator.add(p(Resource.USAGE, Action.READ));
        collaborator.add(p(Resource.MODEL, Action.READ));

        Set<Permission> leader = new HashSet<>(collaborator);
        leader.add(p(Resource.USER, Action.READ));
        leader.add(p(Resource.GOVERNANCE_SCORE, Action.READ));

        Set<Permission> manager = new HashSet<>(leader);
        manager.add(p(Resource.USER, Action.MANAGE));
        manager.add(p(Resource.INVITE, Action.MANAGE));
        manager.add(p(Resource.ROLE, Action.READ));
        manager.add(p(Resource.DEPARTMENT, Action.READ));
        manager.add(p(Resource.PROJECT, Action.MANAGE));
        manager.add(p(Resource.AI_TOOL, Action.READ));

        Set<Permission> executive = new HashSet<>(manager);
        executive.add(p(Resource.TENANT, Action.READ));
        executive.add(p(Resource.DEPARTMENT, Action.MANAGE));
        executive.add(p(Resource.ROLE, Action.MANAGE));
        executive.add(p(Resource.MODEL, Action.MANAGE));
        executive.add(p(Resource.LICENSE, Action.MANAGE));
        executive.add(p(Resource.TRAINING, Action.MANAGE));

        Map<HierarchyLevel, Set<Permission>> matrix = new EnumMap<>(HierarchyLevel.class);
        matrix.put(HierarchyLevel.COLLABORATOR, Set.copyOf(collaborator));
        matrix.put(HierarchyLevel.LEADER, Set.copyOf(leader));
        matrix.put(HierarchyLevel.MANAGER, Set.copyOf(manager));
        matrix.put(HierarchyLevel.EXECUTIVE, Set.copyOf(executive));
        return Map.copyOf(matrix);
    }

    private static Permission p(Resource resource, Action action) {
        return Permission.of(resource, action);
    }
}
