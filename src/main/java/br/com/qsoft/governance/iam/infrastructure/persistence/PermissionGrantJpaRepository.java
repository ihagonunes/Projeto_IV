package br.com.qsoft.governance.iam.infrastructure.persistence;

import br.com.qsoft.governance.iam.domain.permission.Action;
import br.com.qsoft.governance.iam.domain.permission.Resource;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso à matriz global de permissões (POPROJ-52).
 * A matriz não é tenant-scoped: a consulta é direta por (roleLevel, resource,
 * action), apoiada pela constraint UNIQUE da tabela.
 */
public interface PermissionGrantJpaRepository extends JpaRepository<PermissionGrantJpaEntity, UUID> {

    Optional<PermissionGrantJpaEntity> findByRoleLevelAndResourceAndAction(
        HierarchyLevel roleLevel, Resource resource, Action action);
}