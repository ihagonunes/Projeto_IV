package br.com.qsoft.governance.sharedkernel.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.sharedkernel.id.TenantId;
import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários de {@link TenantContextHolder}.
 * <p>
 * O {@code @AfterEach} garante que o ThreadLocal seja limpo entre testes
 * para evitar vazamento de estado.
 */
class TenantContextHolderTest {

    @AfterEach
    void limpar() {
        TenantContextHolder.clear();
    }

    // ── set e get ─────────────────────────────────────────────────────────────

    @Test
    void deve_retornarContexto_quando_setDefinido() {
        TenantContext ctx = context();

        TenantContextHolder.set(ctx);

        assertThat(TenantContextHolder.get()).isSameAs(ctx);
    }

    @Test
    void deve_retornarNulo_quando_semContextoDefinido() {
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void deve_sobrescreverContexto_quando_setDuasVezes() {
        TenantContext ctx1 = context();
        TenantContext ctx2 = context();

        TenantContextHolder.set(ctx1);
        TenantContextHolder.set(ctx2);

        assertThat(TenantContextHolder.get()).isSameAs(ctx2);
    }

    // ── require ───────────────────────────────────────────────────────────────

    @Test
    void deve_retornarContexto_quando_requireComContextoPresente() {
        TenantContext ctx = context();
        TenantContextHolder.set(ctx);

        assertThat(TenantContextHolder.require()).isSameAs(ctx);
    }

    @Test
    void deve_lancarExcecao_quando_requireSemContexto() {
        assertThatThrownBy(TenantContextHolder::require)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TenantContext");
    }

    // ── requireTenantId ────────────────────────────────────────────────────────

    @Test
    void deve_retornarTenantId_quando_requireTenantIdComContexto() {
        TenantId tenantId = TenantId.random();
        TenantContextHolder.set(new TenantContext(tenantId, UserId.random()));

        assertThat(TenantContextHolder.requireTenantId()).isEqualTo(tenantId);
    }

    // ── clear ─────────────────────────────────────────────────────────────────

    @Test
    void deve_removerContexto_quando_clearChamado() {
        TenantContextHolder.set(context());
        TenantContextHolder.clear();

        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void deve_naoLancarExcecao_quando_clearSemContexto() {
        // clear sem set não deve lançar exceção
        TenantContextHolder.clear();
        assertThat(TenantContextHolder.get()).isNull();
    }

    // ── isolamento entre threads ───────────────────────────────────────────────

    @Test
    void deve_isolarContexto_entre_threads() throws InterruptedException {
        TenantContext ctxThread1 = context();
        TenantContextHolder.set(ctxThread1);

        AtomicReference<TenantContext> ctxVisto = new AtomicReference<>();
        Thread outra = new Thread(() -> ctxVisto.set(TenantContextHolder.get()));
        outra.start();
        outra.join();

        // A outra thread não tem contexto definido — deve ver null
        assertThat(ctxVisto.get()).isNull();
        // Thread principal ainda tem o contexto
        assertThat(TenantContextHolder.get()).isSameAs(ctxThread1);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static TenantContext context() {
        return new TenantContext(TenantId.random(), UserId.random());
    }
}
