package br.com.qsoft.governance.iam.domain.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;


/**
 * Testes unitários do agregado {@link Tenant}.
 * Domínio puro — sem Spring, sem banco, sem mocks.
 * Cobre os branches faltantes: branch 69.2% → ≥ 70%.
 */
class TenantTest {

    // -------------------------------------------------------------------------
    // create — validação de emailDomain
    // -------------------------------------------------------------------------

    @Test
    void deve_criarTenantAtivo_quando_dadosValidos() {
        TenantId id = TenantId.random();
        Tenant tenant = Tenant.create(id, "qsoft", "QSOFT", "qsoft.com.br");

        assertThat(tenant.id()).isEqualTo(id);
        assertThat(tenant.slug()).isEqualTo("qsoft");
        assertThat(tenant.name()).isEqualTo("QSOFT");
        assertThat(tenant.emailDomain()).isEqualTo("qsoft.com.br");
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.createdAt()).isNotNull();
        assertThat(tenant.updatedAt()).isNotNull();
    }

    @Test
    void deve_lancarExcecao_quando_emailDomainVazio() {
        assertThatThrownBy(() -> Tenant.create(TenantId.random(), "q", "Q", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("emailDomain");
    }

    @Test
    void deve_lancarExcecao_quando_emailDomainApenasEspacos() {
        assertThatThrownBy(() -> Tenant.create(TenantId.random(), "q", "Q", "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("emailDomain");
    }

    @Test
    void deve_lancarExcecao_quando_emailDomainNulo() {
        assertThatThrownBy(() -> Tenant.create(TenantId.random(), "q", "Q", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("emailDomain");
    }

    /** Domínio com @ no início → normalizado, sem lançar exceção. */
    @Test
    void deve_normalizarDominio_quando_comArroba() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "@qsoft.com.br");

        // normalizeDomain remove o @ inicial
        assertThat(tenant.emailDomain()).isEqualTo("qsoft.com.br");
    }

    /** Domínio com letras maiúsculas → normalizado em minúsculas. */
    @Test
    void deve_normalizarDominio_quando_comMaiusculas() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "QSOFT.COM.BR");

        assertThat(tenant.emailDomain()).isEqualTo("qsoft.com.br");
    }

    /** Domínio com espaços → normalizado. */
    @Test
    void deve_normalizarDominio_quando_comEspacos() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "  qsoft.com.br  ");

        assertThat(tenant.emailDomain()).isEqualTo("qsoft.com.br");
    }

    // -------------------------------------------------------------------------
    // normalizeDomain — método público estático
    // -------------------------------------------------------------------------

    @Test
    void deve_retornarNull_quando_normalizeDomainRecebeNull() {
        assertThat(Tenant.normalizeDomain(null)).isNull();
    }

    @Test
    void deve_removerArroba_quando_normalizeDomainComArroba() {
        assertThat(Tenant.normalizeDomain("@empresa.com")).isEqualTo("empresa.com");
    }

    // -------------------------------------------------------------------------
    // activate / deactivate — idempotência
    // -------------------------------------------------------------------------

    @Test
    void deve_desativar_quando_estaAtivo() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        Instant before = tenant.updatedAt();
        tenant.deactivate();

        assertThat(tenant.status()).isEqualTo(TenantStatus.INACTIVE);
        assertThat(tenant.updatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void deve_manterStatus_quando_deactivateJaInativo() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");
        tenant.deactivate();
        Instant before = tenant.updatedAt();

        tenant.deactivate(); // idempotente

        assertThat(tenant.status()).isEqualTo(TenantStatus.INACTIVE);
        assertThat(tenant.updatedAt()).isEqualTo(before);
    }

    @Test
    void deve_reativar_quando_estaInativo() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");
        tenant.deactivate();

        tenant.activate();

        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void deve_manterStatus_quando_activateJaAtivo() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");
        Instant before = tenant.updatedAt();

        tenant.activate(); // idempotente

        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.updatedAt()).isEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // changeName
    // -------------------------------------------------------------------------

    @Test
    void deve_alterarNome_quando_nomeValido() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Old", "q.com");

        tenant.changeName("New Name");

        assertThat(tenant.name()).isEqualTo("New Name");
    }

    @Test
    void deve_lancarExcecao_quando_changeNameComNomeVazio() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        assertThatThrownBy(() -> tenant.changeName(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void deve_lancarExcecao_quando_changeNameComNomeApenasEspacos() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        assertThatThrownBy(() -> tenant.changeName("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    @Test
    void deve_lancarExcecao_quando_changeNameComNomeNulo() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        assertThatThrownBy(() -> tenant.changeName(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name");
    }

    // -------------------------------------------------------------------------
    // changeBranding — todos os branches de requireHexColor e normalizeLogoUrl
    // -------------------------------------------------------------------------

    @Test
    void deve_atualizarBranding_quando_dadosValidos() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        tenant.changeBranding("https://img.com/logo.png", "#1A2B3C", "#FFFFFF");

        assertThat(tenant.logoUrl()).isEqualTo("https://img.com/logo.png");
        assertThat(tenant.primaryColor()).isEqualTo("#1A2B3C");
        assertThat(tenant.secondaryColor()).isEqualTo("#FFFFFF");
    }

    @Test
    void deve_limparBranding_quando_argumentosNulos() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");
        tenant.changeBranding("https://img.com/logo.png", "#1A2B3C", "#FFFFFF");

        tenant.changeBranding(null, null, null);

        assertThat(tenant.logoUrl()).isNull();
        assertThat(tenant.primaryColor()).isNull();
        assertThat(tenant.secondaryColor()).isNull();
    }

    /** logoUrl em branco (só espaços) → normalizado para null. */
    @Test
    void deve_normalizarLogoUrl_quando_emBranco() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        tenant.changeBranding("   ", "#1A2B3C", null);

        assertThat(tenant.logoUrl()).isNull();
    }

    /** Cor em branco (só espaços) → normalizada para null sem exceção. */
    @Test
    void deve_normalizarCor_quando_emBranco() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        tenant.changeBranding(null, "   ", null);

        assertThat(tenant.primaryColor()).isNull();
    }

    /** Cor vazia (string "  ") → aceita como null (sem lançar exceção). */
    @Test
    void deve_aceitarCorVazia_comoNull() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        // Não deve lançar exceção: string em branco → null
        tenant.changeBranding(null, "", null);

        assertThat(tenant.primaryColor()).isNull();
    }

    /** Formato de cor inválido → IllegalArgumentException. */
    @Test
    void deve_lancarExcecao_quando_primaryColorFormatoInvalido() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        assertThatThrownBy(() -> tenant.changeBranding(null, "ZZZZZZ", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("#RRGGBB");
    }

    @Test
    void deve_lancarExcecao_quando_secondaryColorFormatoInvalido() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        assertThatThrownBy(() -> tenant.changeBranding(null, null, "blue"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("#RRGGBB");
    }

    /** Cor sem # → inválida. */
    @Test
    void deve_lancarExcecao_quando_corSemHashtag() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        assertThatThrownBy(() -> tenant.changeBranding(null, "1A2B3C", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("#RRGGBB");
    }

    /** Cor com # e letras válidas → normalizada para maiúsculas. */
    @Test
    void deve_normalizarCorParaMaiusculas_quando_corMinuscula() {
        Tenant tenant = Tenant.create(TenantId.random(), "q", "Q", "q.com");

        tenant.changeBranding(null, "#1a2b3c", null);

        assertThat(tenant.primaryColor()).isEqualTo("#1A2B3C");
    }

    // -------------------------------------------------------------------------
    // restore — sobrecarga sem identidade visual
    // -------------------------------------------------------------------------

    @Test
    void deve_restaurarTenantComDominio_quando_persistidoComDominio() {
        TenantId id = TenantId.random();
        Instant now = Instant.now();
        Tenant tenant = Tenant.restore(id, "qsoft", "QSOFT", "qsoft.com",
            TenantStatus.ACTIVE, now, now);

        assertThat(tenant.emailDomain()).isEqualTo("qsoft.com");
        assertThat(tenant.logoUrl()).isNull();
        assertThat(tenant.primaryColor()).isNull();
    }

    @Test
    void deve_restaurarTenantSemDominio_quando_tenantLegado() {
        TenantId id = TenantId.random();
        Instant now = Instant.now();
        Tenant tenant = Tenant.restore(id, "legado", "Legado",
            TenantStatus.INACTIVE, now, now);

        assertThat(tenant.emailDomain()).isNull();
        assertThat(tenant.status()).isEqualTo(TenantStatus.INACTIVE);
    }

    @Test
    void deve_restaurarComBranding_quando_completo() {
        TenantId id = TenantId.random();
        Instant now = Instant.now();
        Tenant tenant = Tenant.restore(id, "q", "Q", "q.com",
            "https://logo.com/img.png", "#111111", "#222222",
            TenantStatus.ACTIVE, now, now);

        assertThat(tenant.logoUrl()).isEqualTo("https://logo.com/img.png");
        assertThat(tenant.primaryColor()).isEqualTo("#111111");
        assertThat(tenant.secondaryColor()).isEqualTo("#222222");
    }
}
