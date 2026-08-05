package com.crediblex.fineract.portfolio.loanaccount.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForeclosureWaivedPeriodCalculatorTest {

    @Test
    void computeWaivedPeriods_matchesLoan11665Breakdown() {
        final LocalDate foreclosureDate = LocalDate.of(2026, 1, 21);
        final List<ForeclosureWaivedPeriodCalculator.OriginalInstallmentRow> original = List.of(
                new ForeclosureWaivedPeriodCalculator.OriginalInstallmentRow(1, LocalDate.of(2025, 12, 16), LocalDate.of(2026, 1, 20),
                        new BigDecimal("1609.45")),
                new ForeclosureWaivedPeriodCalculator.OriginalInstallmentRow(2, LocalDate.of(2026, 1, 20), LocalDate.of(2026, 2, 20),
                        new BigDecimal("1352.30")),
                new ForeclosureWaivedPeriodCalculator.OriginalInstallmentRow(3, LocalDate.of(2026, 2, 20), LocalDate.of(2026, 3, 20),
                        new BigDecimal("985.43")),
                new ForeclosureWaivedPeriodCalculator.OriginalInstallmentRow(4, LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 20),
                        new BigDecimal("823.82")),
                new ForeclosureWaivedPeriodCalculator.OriginalInstallmentRow(5, LocalDate.of(2026, 4, 20), LocalDate.of(2026, 5, 20),
                        new BigDecimal("536.15")),
                new ForeclosureWaivedPeriodCalculator.OriginalInstallmentRow(6, LocalDate.of(2026, 5, 20), LocalDate.of(2026, 6, 22),
                        new BigDecimal("297.64")));

        final List<ForeclosureWaivedPeriodCalculator.CurrentInstallmentRow> current = List.of(
                new ForeclosureWaivedPeriodCalculator.CurrentInstallmentRow(LocalDate.of(2025, 12, 16), LocalDate.of(2026, 1, 20),
                        new BigDecimal("1609.45")),
                new ForeclosureWaivedPeriodCalculator.CurrentInstallmentRow(LocalDate.of(2026, 1, 20), LocalDate.of(2026, 1, 21),
                        new BigDecimal("43.62")));

        final var waivedPeriods = ForeclosureWaivedPeriodCalculator.computeWaivedPeriods(foreclosureDate, original, current);

        assertThat(waivedPeriods).hasSize(5);
        assertThat(waivedPeriods.get(0).getWaivedInterest()).isEqualByComparingTo("1308.68");
        assertThat(waivedPeriods.stream().map(p -> p.getWaivedInterest()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("3951.72");
    }
}
