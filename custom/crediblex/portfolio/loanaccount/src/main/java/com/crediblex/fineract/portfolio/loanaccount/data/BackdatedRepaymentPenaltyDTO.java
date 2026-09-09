package com.crediblex.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class BackdatedRepaymentPenaltyDTO {

    public static final Set<String> RESPONSE_DATA_PARAMETERS = Set.of("penaltyAmountDue", "principalOutstanding", "interestOutstanding",
            "remainingPrincipalOutstanding", "earliestAllowedTransactionDate", "onInstallmentDueDate", "lpiWaivedOnSettlement");

    private final BigDecimal penaltyAmountDue;
    /** Principal still due on the installment that contains the transaction date (current EMI). */
    private final BigDecimal principalOutstanding;
    private final BigDecimal interestOutstanding;
    /**
     * Remaining principal across every unpaid installment as of the transaction date. Full settlement under
     * mifos-standard / pro-rata-mifos-standard applies extra funds to this amount (future EMI interest is not
     * collected).
     */
    private final BigDecimal remainingPrincipalOutstanding;
    /**
     * The earliest date this loan may currently be backdated to for a repayment/transfer - see
     * {@code BackdatedRepaymentValidator#computeEarliestAllowedTransactionDate}. Surfaced here so the UI's
     * transaction-date calendar can set its minDate directly from the backend instead of hardcoding a limit that could
     * drift out of sync with the server-side rule.
     */
    private final LocalDate earliestAllowedTransactionDate;

    /** True when the selected date exactly matches an installment due date (on-time payment). */
    private boolean onInstallmentDueDate;

    /**
     * Outstanding LPI on the loan today that will be auto-waived if a repayment/transfer is posted with this value date
     * (matches {@code CredXLoanChargeWritePlatformServiceImpl#waiveOverdueChargesAccruedAfterSettlementDate}).
     */
    private BigDecimal lpiWaivedOnSettlement = BigDecimal.ZERO;
}
