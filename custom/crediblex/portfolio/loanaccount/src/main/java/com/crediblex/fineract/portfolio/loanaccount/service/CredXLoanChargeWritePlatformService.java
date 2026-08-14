package com.crediblex.fineract.portfolio.loanaccount.service;

import java.time.LocalDate;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformService;

/**
 * Extended interface for CredibleX loan charge write operations. Adds custom methods like reversePaidLoanCharge without
 * modifying the base interface.
 */
public interface CredXLoanChargeWritePlatformService extends LoanChargeWritePlatformService {

    /**
     * Reverses a paid loan charge by: 1. Creating a CHARGE_ADJUSTMENT transaction for audit trail (no journal entries)
     * 2. Marking the charge as inactive and resetting paid amounts 3. Updating the loan schedule and summary 4.
     * Creating GL entries when savings deposit is credited (Debit 100062, Credit 210003) 5. Crediting the reversed
     * amount to the linked savings account 6. Creating audit trail
     *
     * @param loanId
     *            The loan account ID
     * @param loanChargeId
     *            The charge ID to reverse
     * @param command
     *            The JSON command containing optional parameters
     * @return CommandProcessingResult with the reversal details
     */
    CommandProcessingResult reversePaidLoanCharge(Long loanId, Long loanChargeId, JsonCommand command);

    /**
     * Waives the outstanding overdue (LPI) charges that accrued strictly AFTER a backdated settlement date, i.e. the
     * charges for the days between the actual payment day and the day the settlement is being recorded (e.g. money
     * received Friday, settled Monday backdated to Friday -> Sat/Sun/Mon LPI is waived). Each charge is waived through
     * the standard per-charge waiver core so a proper waive transaction with journal entries is posted and the charge
     * keeps a full audit trail (visible as "waived" in the charges tab). Paid portions are preserved. Repayment
     * schedule dates are never touched.
     *
     * @param loanId
     *            the loan being settled
     * @param settlementDate
     *            the (backdated) transaction date of the settlement. Paying on an installment due date waives LPI dated
     *            that day as well (on-time). Paying on any other date keeps that day's LPI (window starts the next
     *            calendar day) and waives later charges up to the current business date.
     * @return summary map: {@code chargesWaived}, {@code totalAmountWaived}, {@code daysCovered}, {@code fromDate},
     *         {@code toDate}. Empty counts when the settlement is not backdated or there is nothing to waive.
     */
    Map<String, Object> waiveOverdueChargesAccruedAfterSettlementDate(Long loanId, LocalDate settlementDate);

    /**
     * Waives the outstanding overdue (LPI) charges dated ON OR AFTER a backdated repayment value date, up to the
     * current business date. Used by the direct loan-repayment path (UI "Make Repayment") so that a repayment recorded
     * with a past value date settles the loan exactly: the customer pays LPI only for the days strictly before the
     * value date (as returned by the penalties preview), while the LPI for the value date itself and every later day
     * until the settlement was recorded is waived. Interest and future installments are never touched; the repayment
     * schedule is not regenerated. This differs from {@link #waiveOverdueChargesAccruedAfterSettlementDate} only in
     * that the window is inclusive of the value date, keeping the waived set complementary to the paid set.
     *
     * @param loanId
     *            the loan being settled
     * @param valueDate
     *            the (backdated) transaction/value date of the repayment; LPI with due date on or after this and up to
     *            the current business date is waived
     * @return summary map: {@code chargesWaived}, {@code totalAmountWaived}, {@code daysCovered}, {@code fromDate},
     *         {@code toDate}. Empty counts when the repayment is not backdated or there is nothing to waive.
     */
    Map<String, Object> waiveOverdueChargesOnOrAfterDate(Long loanId, LocalDate valueDate);

    /**
     * Copies unpaid overdue/LPI charge outstanding that is missing from the repayment schedule onto the last
     * installment, then flushes. Must run immediately before a repayment for every product so the strategy collects
     * that LPI instead of treating the same amount as an overpayment.
     */
    void syncOutstandingOverduePenaltyOntoSchedule(Long loanId);
}
