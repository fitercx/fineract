package com.crediblex.fineract.portfolio.loc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LineOfCreditExpiryDateRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate CURRENT_END = LocalDate.of(2027, 10, 19);

    @Test
    void newEndDateMustBeAfterTodayAfterStartAndDifferent() {
        assertThat(LineOfCreditExpiryDateRules.newEndDateError(TODAY, START, CURRENT_END, null)).isEqualTo("New End Date is required.");
        assertThat(LineOfCreditExpiryDateRules.newEndDateError(TODAY, START, CURRENT_END, TODAY))
                .isEqualTo("New End Date must be after today.");
        assertThat(LineOfCreditExpiryDateRules.newEndDateError(TODAY, LocalDate.of(2027, 1, 1), CURRENT_END, LocalDate.of(2026, 12, 1)))
                .isEqualTo("New End Date must be after the Start Date.");
        assertThat(LineOfCreditExpiryDateRules.newEndDateError(TODAY, START, CURRENT_END, CURRENT_END))
                .isEqualTo("New End Date must be different from the current End Date.");
        assertThat(LineOfCreditExpiryDateRules.newEndDateError(TODAY, START, CURRENT_END, LocalDate.of(2028, 1, 19))).isNull();
    }

    @Test
    void changeTypeAndReviewWarning() {
        assertThat(LineOfCreditExpiryDateRules.changeType(CURRENT_END, LocalDate.of(2028, 1, 19)))
                .isEqualTo(LineOfCreditExpiryDateRules.EXTENSION);
        assertThat(LineOfCreditExpiryDateRules.changeType(CURRENT_END, LocalDate.of(2027, 4, 19)))
                .isEqualTo(LineOfCreditExpiryDateRules.REDUCTION);
        assertThat(LineOfCreditExpiryDateRules.nextReviewWarning(null, LocalDate.of(2028, 1, 19)))
                .isEqualTo(LineOfCreditExpiryDateRules.REVIEW_WARNING);
        assertThat(LineOfCreditExpiryDateRules.nextReviewWarning(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 1, 19)))
                .isEqualTo(LineOfCreditExpiryDateRules.REVIEW_WARNING);
        assertThat(LineOfCreditExpiryDateRules.nextReviewWarning(LocalDate.of(2027, 12, 1), LocalDate.of(2028, 1, 19))).isNull();
    }
}
