package com.crediblex.fineract.portfolio.loc.service;

import java.time.LocalDate;

/**
 * Date rules for a Line of Credit expiry change. Kept free of persistence so the checks can be tested directly.
 */
public final class LineOfCreditExpiryDateRules {

    public static final String EXTENSION = "Extension";
    public static final String REDUCTION = "Reduction";
    public static final String PENDING_MESSAGE = "An expiry change is already pending approval for this facility.";
    public static final String INACTIVE_MESSAGE = "Facility is inactive. Activate to allow drawdowns.";
    public static final String REVIEW_WARNING = "Next Review Date is empty or later than the New End Date.";

    private LineOfCreditExpiryDateRules() {}

    public static String changeType(final LocalDate currentEndDate, final LocalDate newEndDate) {
        return newEndDate.isAfter(currentEndDate) ? EXTENSION : REDUCTION;
    }

    public static String nextReviewWarning(final LocalDate nextReviewDate, final LocalDate newEndDate) {
        if (nextReviewDate == null || nextReviewDate.isAfter(newEndDate)) {
            return REVIEW_WARNING;
        }
        return null;
    }

    public static String newEndDateError(final LocalDate today, final LocalDate startDate, final LocalDate currentEndDate,
            final LocalDate newEndDate) {
        if (newEndDate == null) {
            return "New End Date is required.";
        }
        if (!newEndDate.isAfter(today)) {
            return "New End Date must be after today.";
        }
        if (startDate != null && !newEndDate.isAfter(startDate)) {
            return "New End Date must be after the Start Date.";
        }
        if (currentEndDate != null && newEndDate.equals(currentEndDate)) {
            return "New End Date must be different from the current End Date.";
        }
        return null;
    }
}
