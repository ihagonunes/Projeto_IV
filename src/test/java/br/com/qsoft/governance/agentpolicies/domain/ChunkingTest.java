package br.com.qsoft.governance.agentpolicies.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Divisão em trechos por parágrafo com limite aproximado de 800 (SPEC-05 §5). */
class ChunkingTest {

    @Test
    void cutsLongWordsWithoutLosingTextAndIgnoresTabOnlySlices() {
        String word = "a".repeat(801);
        assertThat(Chunking.split(word)).containsExactly("a".repeat(800), "a");
        assertThat(Chunking.split("a" + "\t".repeat(1600) + "b")).containsExactly("a", "b");
    }

    @Test
    void separaParagrafosEmTrechos() {
        List<String> chunks = Chunking.split("Primeiro parágrafo.\n\nSegundo parágrafo.\n\n  ");

        assertThat(chunks).containsExactly("Primeiro parágrafo.", "Segundo parágrafo.");
    }

    @Test
    void paragrafoLongoEhPartidoEmTrechosDeAte800() {
        String paragraph = "palavra ".repeat(300).trim();

        List<String> chunks = Chunking.split(paragraph);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(800));
        assertThat(String.join(" ", chunks).replaceAll("\\s+", " "))
            .isEqualTo(paragraph.replaceAll("\\s+", " "));
    }

    @Test
    void textoVazioNaoGeraTrechos() {
        assertThat(Chunking.split(null)).isEmpty();
        assertThat(Chunking.split("   \n\n  ")).isEmpty();
    }

    @Test
    void paragrafoCurtoViraUmUnicoTrecho() {
        assertThat(Chunking.split("Texto curto.")).containsExactly("Texto curto.");
    }
}
