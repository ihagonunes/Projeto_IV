package br.com.qsoft.governance.iam.infrastructure.bootstrap;

import br.com.qsoft.governance.iam.application.ProvisionUser;
import br.com.qsoft.governance.iam.application.ProvisionUserCommand;
import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.application.port.RoleAssignmentRepository;
import br.com.qsoft.governance.iam.application.port.RoleRepository;
import br.com.qsoft.governance.iam.application.port.TenantRepository;
import br.com.qsoft.governance.iam.application.port.UserRepository;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.exception.UserEmailAlreadyExistsException;
import br.com.qsoft.governance.iam.domain.rbac.Role;
import br.com.qsoft.governance.iam.domain.rbac.RoleAssignment;
import br.com.qsoft.governance.iam.domain.tenant.Tenant;
import br.com.qsoft.governance.iam.domain.user.HierarchyLevel;
import br.com.qsoft.governance.iam.domain.user.User;
import br.com.qsoft.governance.sharedkernel.id.RoleId;
import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    private RegisterTenant registerTenant;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ProvisionUser provisionUser;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    private final TenantId tenantId = TenantId.random();
    private final Tenant demoTenant = Tenant.create(tenantId, "qsoft", "QSOFT Demo", "qsoft.com");
    private final UserId adminId = UserId.random();
    private final User adminUser = User.create(adminId, tenantId, "admin@qsoft.com", "Administrador", HierarchyLevel.EXECUTIVE, null);
    private final RoleId roleId = RoleId.random();
    private final Role executiveRole = Role.create(roleId, tenantId, "EXECUTIVE");

    @Test
    void deve_retornar_sem_executar_quando_demo_password_estiver_nula() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            registerTenant, tenantRepository, provisionUser, userRepository,
            roleRepository, roleAssignmentRepository, null);

        seeder.run(new DefaultApplicationArguments());

        verifyNoInteractions(registerTenant, tenantRepository, provisionUser, userRepository, roleRepository, roleAssignmentRepository);
    }

    @Test
    void deve_retornar_sem_executar_quando_demo_password_estiver_em_branco() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            registerTenant, tenantRepository, provisionUser, userRepository,
            roleRepository, roleAssignmentRepository, "   ");

        seeder.run(new DefaultApplicationArguments());

        verifyNoInteractions(registerTenant, tenantRepository, provisionUser, userRepository, roleRepository, roleAssignmentRepository);
    }

    @Test
    void deve_executar_seeding_com_sucesso_quando_tudo_estiver_correto() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            registerTenant, tenantRepository, provisionUser, userRepository,
            roleRepository, roleAssignmentRepository, "secret123");

        when(registerTenant.register(any(RegisterTenantCommand.class))).thenReturn(demoTenant);
        when(tenantRepository.findBySlug("qsoft")).thenReturn(Optional.of(demoTenant));
        when(provisionUser.provision(any(ProvisionUserCommand.class))).thenReturn(adminUser);
        when(roleRepository.findByTenantIdAndName(tenantId, "EXECUTIVE")).thenReturn(Optional.of(executiveRole));
        when(roleAssignmentRepository.findByTenantIdAndUserId(tenantId, adminId)).thenReturn(List.of());

        seeder.run(new DefaultApplicationArguments());

        verify(registerTenant).register(any(RegisterTenantCommand.class));
        verify(tenantRepository).findBySlug("qsoft");
        verify(provisionUser).provision(any(ProvisionUserCommand.class));
        verify(roleRepository).findByTenantIdAndName(tenantId, "EXECUTIVE");
        verify(roleAssignmentRepository).save(any(RoleAssignment.class));
    }

    @Test
    void deve_tratar_excecao_de_tenant_ja_existente_e_continuar_seeding() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            registerTenant, tenantRepository, provisionUser, userRepository,
            roleRepository, roleAssignmentRepository, "secret123");

        when(registerTenant.register(any(RegisterTenantCommand.class)))
            .thenThrow(new TenantSlugAlreadyExistsException("qsoft"));
        when(tenantRepository.findBySlug("qsoft")).thenReturn(Optional.of(demoTenant));
        when(provisionUser.provision(any(ProvisionUserCommand.class))).thenReturn(adminUser);
        when(roleRepository.findByTenantIdAndName(tenantId, "EXECUTIVE")).thenReturn(Optional.of(executiveRole));
        when(roleAssignmentRepository.findByTenantIdAndUserId(tenantId, adminId)).thenReturn(List.of());

        seeder.run(new DefaultApplicationArguments());

        verify(tenantRepository).findBySlug("qsoft");
        verify(provisionUser).provision(any(ProvisionUserCommand.class));
        verify(roleAssignmentRepository).save(any(RoleAssignment.class));
    }

    @Test
    void deve_interromper_seeding_quando_tenant_nao_for_encontrado() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            registerTenant, tenantRepository, provisionUser, userRepository,
            roleRepository, roleAssignmentRepository, "secret123");

        when(registerTenant.register(any(RegisterTenantCommand.class))).thenReturn(demoTenant);
        when(tenantRepository.findBySlug("qsoft")).thenReturn(Optional.empty());

        seeder.run(new DefaultApplicationArguments());

        verify(tenantRepository).findBySlug("qsoft");
        verify(provisionUser, never()).provision(any());
        verify(roleAssignmentRepository, never()).save(any());
    }

    @Test
    void deve_tratar_usuario_admin_ja_existente_e_buscar_usuario_existente() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            registerTenant, tenantRepository, provisionUser, userRepository,
            roleRepository, roleAssignmentRepository, "secret123");

        when(registerTenant.register(any(RegisterTenantCommand.class))).thenReturn(demoTenant);
        when(tenantRepository.findBySlug("qsoft")).thenReturn(Optional.of(demoTenant));
        when(provisionUser.provision(any(ProvisionUserCommand.class)))
            .thenThrow(new UserEmailAlreadyExistsException("admin@qsoft.com"));
        when(userRepository.findByTenantIdAndEmail(tenantId, "admin@qsoft.com")).thenReturn(Optional.of(adminUser));
        when(roleRepository.findByTenantIdAndName(tenantId, "EXECUTIVE")).thenReturn(Optional.of(executiveRole));
        when(roleAssignmentRepository.findByTenantIdAndUserId(tenantId, adminId)).thenReturn(List.of());

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository).findByTenantIdAndEmail(tenantId, "admin@qsoft.com");
        verify(roleAssignmentRepository).save(any(RoleAssignment.class));
    }

    @Test
    void deve_interromper_se_usuario_admin_ja_existente_nao_for_encontrado() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            registerTenant, tenantRepository, provisionUser, userRepository,
            roleRepository, roleAssignmentRepository, "secret123");

        when(registerTenant.register(any(RegisterTenantCommand.class))).thenReturn(demoTenant);
        when(tenantRepository.findBySlug("qsoft")).thenReturn(Optional.of(demoTenant));
        when(provisionUser.provision(any(ProvisionUserCommand.class)))
            .thenThrow(new UserEmailAlreadyExistsException("admin@qsoft.com"));
        when(userRepository.findByTenantIdAndEmail(tenantId, "admin@qsoft.com")).thenReturn(Optional.empty());

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository).findByTenantIdAndEmail(tenantId, "admin@qsoft.com");
        verify(roleRepository, never()).findByTenantIdAndName(any(), any());
        verify(roleAssignmentRepository, never()).save(any());
    }

    @Test
    void deve_nao_atribuir_papel_se_role_executive_nao_for_encontrado() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            registerTenant, tenantRepository, provisionUser, userRepository,
            roleRepository, roleAssignmentRepository, "secret123");

        when(registerTenant.register(any(RegisterTenantCommand.class))).thenReturn(demoTenant);
        when(tenantRepository.findBySlug("qsoft")).thenReturn(Optional.of(demoTenant));
        when(provisionUser.provision(any(ProvisionUserCommand.class))).thenReturn(adminUser);
        when(roleRepository.findByTenantIdAndName(tenantId, "EXECUTIVE")).thenReturn(Optional.empty());

        seeder.run(new DefaultApplicationArguments());

        verify(roleRepository).findByTenantIdAndName(tenantId, "EXECUTIVE");
        verify(roleAssignmentRepository, never()).save(any());
    }

    @Test
    void deve_nao_salvar_atribuicao_se_papel_ja_estiver_atribuido() {
        DemoDataSeeder seeder = new DemoDataSeeder(
            registerTenant, tenantRepository, provisionUser, userRepository,
            roleRepository, roleAssignmentRepository, "secret123");

        RoleAssignment existingAssignment = RoleAssignment.grant(
            adminId, executiveRole, null,
            br.com.qsoft.governance.iam.domain.rbac.GrantOrigin.MIGRATION, null, java.time.Instant.now());

        when(registerTenant.register(any(RegisterTenantCommand.class))).thenReturn(demoTenant);
        when(tenantRepository.findBySlug("qsoft")).thenReturn(Optional.of(demoTenant));
        when(provisionUser.provision(any(ProvisionUserCommand.class))).thenReturn(adminUser);
        when(roleRepository.findByTenantIdAndName(tenantId, "EXECUTIVE")).thenReturn(Optional.of(executiveRole));
        when(roleAssignmentRepository.findByTenantIdAndUserId(tenantId, adminId)).thenReturn(List.of(existingAssignment));

        seeder.run(new DefaultApplicationArguments());

        verify(roleAssignmentRepository).findByTenantIdAndUserId(tenantId, adminId);
        verify(roleAssignmentRepository, never()).save(any());
    }
}
