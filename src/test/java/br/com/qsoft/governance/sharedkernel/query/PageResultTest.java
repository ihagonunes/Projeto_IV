package br.com.qsoft.governance.sharedkernel.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void deveCriarPageResultComSucesso() {
        List<String> itens = List.of("A", "B");
        PageResult<String> page = new PageResult<>(itens, 10, 0, 20);

        assertThat(page.content()).containsExactly("A", "B");
        assertThat(page.totalElements()).isEqualTo(10);
        assertThat(page.page()).isEqualTo(0);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void deveInicializarListaVaziaQuandoContentForNulo() {
        PageResult<String> page = new PageResult<>(null, 0, 0, 10);

        assertThat(page.content()).isEmpty();
    }

    @Test
    void deveLancarExcecaoQuandoPageForNegativo() {
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, -1, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("page não pode ser negativo.");
    }

    @Test
    void deveLancarExcecaoQuandoSizeForZeroOuNegativo() {
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("size deve ser maior que zero.");

        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 0, -5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("size deve ser maior que zero.");
    }

    @Test
    void deveCalcularTotalPagesCorretamente() {
        // Divisão com resto (15 elementos / 10 por página = 2 páginas)
        PageResult<String> page1 = new PageResult<>(List.of(), 15, 0, 10);
        assertThat(page1.totalPages()).isEqualTo(2);

        // Divisão exata (20 elementos / 10 por página = 2 páginas)
        PageResult<String> page2 = new PageResult<>(List.of(), 20, 0, 10);
        assertThat(page2.totalPages()).isEqualTo(2);

        // Nenhum elemento (0 elementos / 10 por página = 0 páginas)
        PageResult<String> page3 = new PageResult<>(List.of(), 0, 0, 10);
        assertThat(page3.totalPages()).isEqualTo(0);
    }
}
