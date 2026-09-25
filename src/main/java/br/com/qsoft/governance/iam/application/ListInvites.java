package br.com.qsoft.governance.iam.application;

import br.com.qsoft.governance.iam.application.port.InviteRepository;
import br.com.qsoft.governance.iam.domain.invite.Invite;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de listagem dos convites do tenant (SPI-59/POPROJ-14).
 * A AUTORIZAÇÃO (INVITE:MANAGE) é aplicada no endpoint; o tenant vem SEMPRE do
 * contexto autenticado (ADR-03).
 */
@Service
public class ListInvites {

    private final InviteRepository inviteRepository;

    public ListInvites(InviteRepository inviteRepository) {
        this.inviteRepository = inviteRepository;
    }

    public List<Invite> list(TenantId tenantId) {
        TenantId id = Objects.requireNonNull(tenantId, "tenantId");
        return inviteRepository.findByTenantId(id);
    }
}
