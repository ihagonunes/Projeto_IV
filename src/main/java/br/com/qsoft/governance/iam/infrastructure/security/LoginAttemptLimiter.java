package br.com.qsoft.governance.iam.infrastructure.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate limiting das tentativas de login (SPI-67 / ADR-30).
 * <p>
 * O estado é mantido <b>em memória</b>, por instância, em uma janela fixa:
 * após {@code max-attempts} falhas para a mesma chave (IP + e-mail) o login
 * fica bloqueado por {@code window-minutes} — um bloqueio <b>temporário</b>,
 * não um lockout permanente de conta (a task veda lockout persistente sem nova
 * decisão funcional).
 * <p>
 * A chave combina IP e e-mail para dificultar tanto o ataque direcionado a uma
 * conta quanto a pulverização por várias contas a partir do mesmo IP. A chave
 * existe independentemente de a conta existir, o que evita enumeração.
 * <p>
 * Limitação conhecida: por ser em memória, o limite é por instância (não
 * compartilhado entre réplicas) e se perde em um restart. Um backend
 * compartilhado (ex.: Redis) exigiria dependência nova e decisão própria.
 */
@Component
public class LoginAttemptLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    /** Amortiza a varredura de limpeza: no máximo uma a cada N registros. */
    private static final int PURGE_INTERVAL = 1_000;

    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();
    private final AtomicInteger writesSincePurge = new AtomicInteger();

    public LoginAttemptLimiter(
            @Value("${app.security.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login-rate-limit.window-minutes:15}") long windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /** Chave de rate limiting: IP do cliente + e-mail normalizado. */
    public String key(String email, String clientIp) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String ip = clientIp == null ? "" : clientIp.trim();
        return ip + "|" + normalizedEmail;
    }

    public boolean isBlocked(String key) {
        Attempts attempts = current(key);
        return attempts != null && attempts.count() >= maxAttempts;
    }

    /** Segundos restantes de bloqueio; {@code 0} se não houver bloqueio ativo. */
    public long retryAfterSeconds(String key) {
        Attempts attempts = current(key);
        if (attempts == null || attempts.count() < maxAttempts) {
            return 0;
        }
        long remaining = Duration.between(Instant.now(), attempts.windowStart().plus(window)).getSeconds();
        return Math.max(remaining, 0);
    }

    public void recordFailure(String key) {
        Instant now = Instant.now();
        purgeIfNeeded(now);
        attemptsByKey.compute(key, (ignored, existing) ->
            existing == null || existing.isExpired(now, window)
                ? new Attempts(1, now)
                : new Attempts(existing.count() + 1, existing.windowStart()));
    }

    /** Zera o contador — chamado após um login bem-sucedido. */
    public void reset(String key) {
        attemptsByKey.remove(key);
    }

    private Attempts current(String key) {
        Attempts attempts = attemptsByKey.get(key);
        if (attempts == null || attempts.isExpired(Instant.now(), window)) {
            return null;
        }
        return attempts;
    }

    /**
     * Evita crescimento ilimitado do mapa em cenários de varredura de e-mails.
     * A varredura é <b>amortizada</b>: quando o mapa passa do teto, ela ocorre no
     * máximo uma vez a cada {@link #PURGE_INTERVAL} registros — sem isso, toda
     * tentativa pagaria uma varredura O(n) do mapa inteiro (amplificador de carga
     * sob ataque de enumeração).
     */
    private void purgeIfNeeded(Instant now) {
        if (attemptsByKey.size() < MAX_TRACKED_KEYS) {
            return;
        }
        if (writesSincePurge.incrementAndGet() < PURGE_INTERVAL) {
            return;
        }
        writesSincePurge.set(0);
        attemptsByKey.entrySet().removeIf(entry -> entry.getValue().isExpired(now, window));
    }

    private record Attempts(int count, Instant windowStart) {

        boolean isExpired(Instant now, Duration window) {
            return !now.isBefore(windowStart.plus(window));
        }
    }
}
