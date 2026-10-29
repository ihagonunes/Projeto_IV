package br.com.qsoft.governance.engagement.domain;

import br.com.qsoft.governance.sharedkernel.id.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Cálculo puro de AI Credits (ADR-19/ADR-40 §7). */
public final class CreditsCalculator {

    public static final String CALCULATION_VERSION = "credits-v1";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private CreditsCalculator() {
    }

    public static List<CreditsItem> calculate(EngagementFacts facts, YearMonth month) {
        LocalDate endOfMonth = month.atEndOfMonth();
        return facts.eligible().stream()
            .sorted((left, right) -> left.value().toString().compareTo(right.value().toString()))
            .map(userId -> item(facts, userId, month, endOfMonth))
            .toList();
    }

    private static CreditsItem item(EngagementFacts facts, UserId userId, YearMonth month,
                                    LocalDate endOfMonth) {
        long activeDays = facts.activeDays().getOrDefault(userId, java.util.Set.of()).stream()
            .filter(day -> YearMonth.from(day).equals(month))
            .count();
        BigDecimal usage = HUNDRED.multiply(BigDecimal.valueOf(Math.min(activeDays, 10)))
            .divide(TEN, 2, RoundingMode.HALF_UP);

        BigDecimal training = null;
        boolean trainingAvailable = facts.trainingSourceAvailable();
        if (trainingAvailable) {
            boolean completed = facts.trainings().getOrDefault(userId, List.of()).stream()
                .anyMatch(item -> item.completedAt() != null
                    && !item.completedAt().isAfter(endOfMonth));
            training = completed ? HUNDRED.setScale(2) : ZERO;
        }

        boolean licenseAvailable = facts.licenseSourceAvailable();
        List<LicenseFact> applicable = licenseAvailable
            ? AdoptionCalculator.applicableLicenses(
                facts.licenses().getOrDefault(userId, List.of()), endOfMonth)
            : List.of();
        LicenseApplicability applicability = applicable.isEmpty()
            ? LicenseApplicability.NOT_APPLICABLE : LicenseApplicability.APPLICABLE;
        BigDecimal license = null;
        if (applicability == LicenseApplicability.APPLICABLE) {
            license = activeDays > 0 ? HUNDRED.setScale(2) : ZERO;
        }

        BigDecimal score = score(usage, training, license, applicability,
            trainingAvailable, licenseAvailable);
        DataStatus dataStatus = trainingAvailable && licenseAvailable
            ? DataStatus.COMPLETE : DataStatus.PARTIAL;
        return new CreditsItem(userId, usage, training, license, applicability, score, dataStatus);
    }

    private static BigDecimal score(BigDecimal usage, BigDecimal training, BigDecimal license,
                                    LicenseApplicability applicability,
                                    boolean trainingAvailable, boolean licenseAvailable) {
        BigDecimal numerator = usage.multiply(new BigDecimal("0.50"));
        BigDecimal weight = new BigDecimal("0.50");
        if (trainingAvailable && training != null) {
            numerator = numerator.add(training.multiply(new BigDecimal("0.30")));
            weight = weight.add(new BigDecimal("0.30"));
        }
        if (licenseAvailable && applicability == LicenseApplicability.APPLICABLE
            && license != null) {
            numerator = numerator.add(license.multiply(new BigDecimal("0.20")));
            weight = weight.add(new BigDecimal("0.20"));
        }
        if (weight.signum() == 0) {
            return null;
        }
        return numerator.divide(weight, 2, RoundingMode.HALF_UP);
    }
}
