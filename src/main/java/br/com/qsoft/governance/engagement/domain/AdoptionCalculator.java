package br.com.qsoft.governance.engagement.domain;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Cálculo puro de adoção (ADR-18/ADR-40 §4). */
public final class AdoptionCalculator {

    public static final int WINDOW_DAYS = 30;

    private AdoptionCalculator() {
    }

    public static AdoptionResult of(EngagementFacts facts, LocalDate ate) {
        int eligibleCount = facts.eligible().size();
        if (eligibleCount == 0) {
            return new AdoptionResult(0, 0, 0, null, 0, DataStatus.INSUFFICIENT_DATA);
        }

        LocalDate from = ate.minusDays(WINDOW_DAYS - 1L);
        Set<UserId> active = new HashSet<>();
        int idleLicenses = 0;
        for (UserId userId : facts.eligible()) {
            boolean userActive = hasActiveDay(facts, userId, from, ate);
            if (userActive) {
                active.add(userId);
            } else {
                idleLicenses += applicableLicenses(
                    facts.licenses().getOrDefault(userId, List.of()), ate).size();
            }
        }

        BigDecimal rate = BigDecimal.valueOf(active.size())
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(eligibleCount), 2, RoundingMode.HALF_UP);
        DataStatus status = facts.licenseSourceAvailable()
            ? DataStatus.COMPLETE : DataStatus.PARTIAL;
        return new AdoptionResult(eligibleCount, active.size(),
            eligibleCount - active.size(), rate, idleLicenses, status);
    }

    static boolean hasActiveDay(EngagementFacts facts, UserId userId,
                                LocalDate from, LocalDate to) {
        return facts.activeDays().getOrDefault(userId, Set.of()).stream()
            .anyMatch(day -> !day.isBefore(from) && !day.isAfter(to));
    }

    static List<LicenseFact> applicableLicenses(List<LicenseFact> licenses, LocalDate ate) {
        return licenses.stream()
            .filter(license -> license.active()
                && license.assignedAt() != null
                && !license.assignedAt().isAfter(ate)
                && (license.expiresAt() == null || !license.expiresAt().isBefore(ate)))
            .toList();
    }
}
