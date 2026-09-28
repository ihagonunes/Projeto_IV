package br.com.qsoft.governance.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    private static final String IP = "203.0.113.10";

    private LoginAttemptLimiter limiter(int maxAttempts, long windowMinutes) {
        return new LoginAttemptLimiter(maxAttempts, windowMinutes);
    }

    @Test
    void shouldNotBlockBeforeMaxAttempts() {
        LoginAttemptLimiter limiter = limiter(3, 15);
        String key = limiter.key("ana@x.com", IP);

        limiter.recordFailure(key);
        limiter.recordFailure(key);

        assertThat(limiter.isBlocked(key)).isFalse();
        assertThat(limiter.retryAfterSeconds(key)).isZero();
    }

    @Test
    void shouldBlockAtMaxAttempts() {
        LoginAttemptLimiter limiter = limiter(3, 15);
        String key = limiter.key("ana@x.com", IP);

        limiter.recordFailure(key);
        limiter.recordFailure(key);
        limiter.recordFailure(key);

        assertThat(limiter.isBlocked(key)).isTrue();
        assertThat(limiter.retryAfterSeconds(key)).isPositive();
    }

    @Test
    void shouldResetAfterSuccessfulLogin() {
        LoginAttemptLimiter limiter = limiter(3, 15);
        String key = limiter.key("ana@x.com", IP);
        limiter.recordFailure(key);
        limiter.recordFailure(key);

        limiter.reset(key);

        assertThat(limiter.isBlocked(key)).isFalse();
        limiter.recordFailure(key);
        limiter.recordFailure(key);
        assertThat(limiter.isBlocked(key)).isFalse();
    }

    @Test
    void shouldUnblockAfterWindowExpires() {
        // Janela de 0 minuto: cada registro já nasce expirado (bloqueio temporário).
        LoginAttemptLimiter limiter = limiter(2, 0);
        String key = limiter.key("ana@x.com", IP);

        limiter.recordFailure(key);
        limiter.recordFailure(key);
        limiter.recordFailure(key);

        assertThat(limiter.isBlocked(key)).isFalse();
    }

    @Test
    void shouldKeepSeparateCountersPerEmailAndIp() {
        LoginAttemptLimiter limiter = limiter(2, 15);
        String blocked = limiter.key("ana@x.com", IP);
        String otherEmail = limiter.key("bruno@x.com", IP);
        String otherIp = limiter.key("ana@x.com", "198.51.100.7");

        limiter.recordFailure(blocked);
        limiter.recordFailure(blocked);

        assertThat(limiter.isBlocked(blocked)).isTrue();
        assertThat(limiter.isBlocked(otherEmail)).isFalse();
        assertThat(limiter.isBlocked(otherIp)).isFalse();
    }

    @Test
    void shouldNormalizeEmailInKey() {
        LoginAttemptLimiter limiter = limiter(3, 15);

        assertThat(limiter.key("  Ana@X.COM ", IP)).isEqualTo(limiter.key("ana@x.com", IP));
    }

    /**
     * A limpeza do mapa é amortizada (não uma varredura O(n) por tentativa).
     * Mesmo após estourar o teto de chaves, o bloqueio continua funcionando.
     */
    @Test
    void shouldKeepBlockingAfterManyDistinctKeys() {
        LoginAttemptLimiter limiter = limiter(2, 15);
        for (int i = 0; i < 10_001; i++) {
            limiter.recordFailure(limiter.key("user" + i + "@x.com", IP));
        }

        String key = limiter.key("ana@x.com", IP);
        limiter.recordFailure(key);
        limiter.recordFailure(key);

        assertThat(limiter.isBlocked(key)).isTrue();
    }
}
