package br.com.qsoft.governance.iam.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash SHA-256 (hex) do token de convite. Só o hash é persistido; o token puro
 * é devolvido uma única vez e nunca vai para log (ADR-37).
 */
final class InviteTokenHasher {

    private static final HexFormat HEX = HexFormat.of();

    private InviteTokenHasher() {
    }

    static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }
}
