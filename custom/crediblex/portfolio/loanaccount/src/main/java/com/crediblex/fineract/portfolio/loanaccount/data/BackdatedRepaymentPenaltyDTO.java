package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class BackdatedRepaymentPenaltyDTO {

    private final BigDecimal penaltyAmountDue;
    private final BigDecimal principalOutstanding;
    private final BigDecimal interestOutstanding;
    /**
     * The earliest date this loan may currently be backdated to for a repayment/transfer - see
     * {@code BackdatedRepaymentValidator#computeEarliestAllowedTransactionDate}. Surfaced here so the UI's
     * transaction-date calendar can set its minDate directly from the backend instead of hardcoding a limit that could
     * drift out of sync with the server-side rule.
     */
    private final LocalDate earliestAllowedTransactionDate;
}
