package br.com.qsoft.governance.observability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.observability.application.DashboardPeriod.Window;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários de {@link DashboardPeriod} (package-private).
 * Deve estar no mesmo pacote para acessar a classe.
 */
class DashboardPeriodTest {

    // ── resolve: ambos nulos → janela padrão de 30 dias ──────────────────────

    @Test
    void deve_retornarJanelaDe30Dias_quando_fromEToNulos() {
        Instant antes = Instant.now();

        Window w = DashboardPeriod.resolve(null, null);

        Instant depois = Instant.now();
        assertThat(w.to()).isBetween(antes, depois);
        assertThat(Duration.between(w.from(), w.to()))
            .isGreaterThanOrEqualTo(DashboardPeriod.DEFAULT_RANGE.minusSeconds(2))
            .isLessThanOrEqualTo(DashboardPeriod.DEFAULT_RANGE.plusSeconds(2));
    }

    // ── resolve: from nulo → resolvedFrom = to - 30d ─────────────────────────

    @Test
    void deve_calcularFromComoTo30Dias_quando_apenasFromNulo() {
        Instant to = Instant.parse("2026-06-01T00:00:00Z");

        Window w = DashboardPeriod.resolve(null, to);

        assertThat(w.to()).isEqualTo(to);
        assertThat(w.from()).isEqualTo(to.minus(DashboardPeriod.DEFAULT_RANGE));
    }

    // ── resolve: to nulo → resolvedTo = now ──────────────────────────────────

    @Test
    void deve_usarNowComoTo_quando_apenasToNulo() {
        Instant from = Instant.now().minus(Duration.ofDays(7));
        Instant antes = Instant.now();

        Window w = DashboardPeriod.resolve(from, null);

        assertThat(w.from()).isEqualTo(from);
        assertThat(w.to()).isAfterOrEqualTo(antes);
    }

    // ── resolve: ambos fornecidos e válidos ────────────────────────────────────

    @Test
    void deve_retornarJanelaExata_quando_fromEToFornecidosValidos() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-03-31T23:59:59Z");

        Window w = DashboardPeriod.resolve(from, to);

        assertThat(w.from()).isEqualTo(from);
        assertThat(w.to()).isEqualTo(to);
    }

    // ── resolve: from == to (ponto no tempo — válido) ─────────────────────────

    @Test
    void deve_retornarJanelaValida_quando_fromIgualTo() {
        Instant instant = Instant.parse("2026-06-15T12:00:00Z");

        Window w = DashboardPeriod.resolve(instant, instant);

        assertThat(w.from()).isEqualTo(instant);
        assertThat(w.to()).isEqualTo(instant);
    }

    // ── resolve: from > to → IllegalArgumentException ─────────────────────────

    @Test
    void deve_lancarExcecao_quando_fromPosteriorATo() {
        Instant from = Instant.parse("2026-06-15T00:00:00Z");
        Instant to   = Instant.parse("2026-06-01T00:00:00Z");

        assertThatThrownBy(() -> DashboardPeriod.resolve(from, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("'from'")
            .hasMessageContaining("'to'");
    }

    // ── Window: acessores e validação de not-null ─────────────────────────────

    @Test
    void deve_retornarFromETo_quando_windowCriada() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-01-31T00:00:00Z");

        Window w = new Window(from, to);

        assertThat(w.from()).isEqualTo(from);
        assertThat(w.to()).isEqualTo(to);
    }
}
