package br.com.qsoft.governance.agentpolicies.application;

import java.util.UUID;

/** Trecho recuperado com os metadados da fonte (para citação [n]). */
public record KnowledgeHit(
    UUID chunkId,
    String text,
    UUID documentId,
    String title,
    int versionNumber,
    double rank
) {
}
