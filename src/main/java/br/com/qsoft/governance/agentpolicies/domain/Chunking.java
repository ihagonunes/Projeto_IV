package br.com.qsoft.governance.agentpolicies.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Divisão do texto em trechos por parágrafo, com limite aproximado de 800
 * caracteres (ADR-35 opção A). Parágrafos maiores são partidos no último
 * espaço antes do limite; sem espaço, o corte é duro.
 */
public final class Chunking {

    public static final int MAX_CHARS = 800;

    private Chunking() {
    }

    public static List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        for (String paragraph : text.split("\\R\\s*\\R")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            addParagraph(chunks, trimmed);
        }
        return chunks;
    }

    private static void addParagraph(List<String> chunks, String paragraph) {
        if (paragraph.length() <= MAX_CHARS) {
            chunks.add(paragraph);
            return;
        }
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_CHARS, paragraph.length());
            if (end < paragraph.length()) {
                int lastSpace = paragraph.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            String piece = paragraph.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            start = end;
            while (start < paragraph.length() && paragraph.charAt(start) == ' ') {
                start++;
            }
        }
    }
}
