package br.com.qsoft.governance.iam.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.qsoft.governance.iam.application.RegisterTenant;
import br.com.qsoft.governance.iam.application.RegisterTenantCommand;
import br.com.qsoft.governance.iam.domain.exception.TenantSlugAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    private RegisterTenant registerTenant;

    @Test
    void deve_retornar_sem_executar_quando_demo_password_estiver_nula() {
        DemoDataSeeder seeder = new DemoDataSeeder(registerTenant, null);

        seeder.run(new DefaultApplicationArguments());

        verifyNoInteractions(registerTenant);
    }

    @Test
    void deve_retornar_sem_executar_quando_demo_password_estiver_em_branco() {
        DemoDataSeeder seeder = new DemoDataSeeder(registerTenant, "   ");

        seeder.run(new DefaultApplicationArguments());

        verifyNoInteractions(registerTenant);
    }

    @Test
    void deve_registrar_tenant_e_admin_pelo_caso_de_uso_de_producao() {
        DemoDataSeeder seeder = new DemoDataSeeder(registerTenant, "secret123");

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<RegisterTenantCommand> captor =
            ArgumentCaptor.forClass(RegisterTenantCommand.class);
        verify(registerTenant).register(captor.capture());
        RegisterTenantCommand command = captor.getValue();
        assertThat(command.slug()).isEqualTo(DemoDataSeeder.TENANT_SLUG);
        assertThat(command.adminEmail()).isEqualTo(DemoDataSeeder.ADMIN_EMAIL);
        assertThat(command.adminPassword()).isEqualTo("secret123");
    }

    @Test
    void deve_ser_idempotente_quando_tenant_ja_existe() {
        DemoDataSeeder seeder = new DemoDataSeeder(registerTenant, "secret123");
        when(registerTenant.register(any(RegisterTenantCommand.class)))
            .thenThrow(new TenantSlugAlreadyExistsException(DemoDataSeeder.TENANT_SLUG));

        seeder.run(new DefaultApplicationArguments());

        // Não propaga a exceção: a aplicação sobe normalmente.
        verify(registerTenant).register(any(RegisterTenantCommand.class));
    }
}
