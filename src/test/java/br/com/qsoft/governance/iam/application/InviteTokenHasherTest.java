package br.com.qsoft.governance.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class InviteTokenHasherTest {
    @Test
    void hashesKnownInputAsLowerCaseSha256Hex() {
        assertThat(InviteTokenHasher.sha256Hex("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void failsExplicitlyWhenJvmCannotProvideSha256() {
        var missing = new NoSuchAlgorithmException("SHA-256");
        try (var digest = mockStatic(MessageDigest.class)) {
            digest.when(() -> MessageDigest.getInstance("SHA-256")).thenThrow(missing);

            assertThatThrownBy(() -> InviteTokenHasher.sha256Hex("test-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SHA-256 indisponível na JVM.")
                .hasCause(missing);
        }
    }
}
