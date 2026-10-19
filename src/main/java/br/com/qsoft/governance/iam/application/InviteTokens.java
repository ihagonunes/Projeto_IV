package br.com.qsoft.governance.iam.application;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Geração do token puro de convite: 128 bits (16 bytes) de {@link SecureRandom},
 * em hexadecimal. O token é devolvido uma única vez e só o seu hash é persistido.
 */
final class InviteTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 16;

    private InviteTokens() {
    }

    static String random() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
