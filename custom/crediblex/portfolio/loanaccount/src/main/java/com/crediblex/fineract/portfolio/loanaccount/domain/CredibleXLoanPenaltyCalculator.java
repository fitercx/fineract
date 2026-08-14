package com.crediblex.fineract.portfolio.loanaccount.domain;

import com.crediblex.fineract.portfolio.loanaccount.data.ExtendedLoanSchedulePeriodData;
import com.crediblex.fineract.portfolio.loanaccount.domain.transactionprocessor.EarlyRepaymentInterestCalculator;
import com.crediblex.fineract.portfolio.loanaccount.repository.LoanRepaymentsSummaryDAO.InstallmentPaymentsAsOf;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;

@Getter
public class CredibleXLoanPenaltyCalculator {

    private final List<ExtendedLoanSchedulePeriodData> loanInstallments;
    private final List<LoanChargeData> loanCharges;
    private final long penaltyWaitPeriodValue;
    private final boolean isDrawdownLoan;
    /** When backdating, repayments after the value date must not reduce P/I shown in the template. */
    private final Map<Integer, InstallmentPaymentsAsOf> paymentsOnOrBeforeValueDate;
    private final LocalDate businessDate;

    public CredibleXLoanPenaltyCalculator(List<ExtendedLoanSchedulePeriodData> periods, Collection<LoanChargeData> loanCharges,
            long penaltyWaitPeriodValue) {
        this(periods, loanCharges, penaltyWaitPeriodValue, false, null, null);
    }

    public CredibleXLoanPenaltyCalculator(List<ExtendedLoanSchedulePeriodData> periods, Collection<LoanChargeData> loanCharges,
            long penaltyWaitPeriodValue, boolean isDrawdownLoan) {
        this(periods, loanCharges, penaltyWaitPeriodValue, isDrawdownLoan, null, null);
    }

    public CredibleXLoanPenaltyCalculator(List<ExtendedLoanSchedulePeriodData> periods, Collection<LoanChargeData> loanCharges,
            long penaltyWaitPeriodValue, boolean isDrawdownLoan, Map<Integer, InstallmentPaymentsAsOf> paymentsOnOrBeforeValueDate,
            LocalDate businessDate) {
        // Always store installments sorted by period number
        this.loanInstallments = periods.stream().sorted(Comparator.comparingInt(ExtendedLoanSchedulePeriodData::getPeriod)).toList();

        // Always store charges sorted by due date (nulls last to avoid NPE issues)
        this.loanCharges = loanCharges.stream()
                .sorted(Comparator.comparing(LoanChargeData::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        this.penaltyWaitPeriodValue = penaltyWaitPeriodValue;
        this.isDrawdownLoan = isDrawdownLoan;
        this.paymentsOnOrBeforeValueDate = paymentsOnOrBeforeValueDate;
        this.businessDate = businessDate;
    }

    public BigDecimal calculatePenaltySum(LocalDate transactionDate) {

        final LocalDate firstPendingInstallmentDate = getFirstPendingInstallmentDate(transactionDate);

        // Business rule validation - allow early repayments for drawdown loans
        if (transactionDate.isBefore(firstPendingInstallmentDate) && !isDrawdownLoan && hasPendingEmiInstallment()) {
            throw new PlatformApiDataValidationException(
                    List.of(ApiParameterError.parameterError("validation.msg.transactionDate.before.nextPeriodDueDate",
                            "The parameter `transactionDate` cannot be before the first unpaid installment: " + firstPendingInstallmentDate,
                            "transactionDate", transactionDate, firstPendingInstallmentDate)));
        }

        // EMI fully settled but LPI remains — include every unpaid penalty due on or before the settlement date.
        if (!hasPendingEmiInstallment()) {
            return sumUnpaidPenaltiesDueOnOrBefore(transactionDate);
        }

        // For drawdown loans with early repayment, use transaction date as lower bound
        LocalDate lower = determineLowerBoundForPenaltyCalculation(transactionDate, firstPendingInstallmentDate);
        LocalDate upper = transactionDate;

        // Calculate the penalty sum from unpaid, applicable penalties
        return loanCharges.stream().filter(LoanChargeData::isPenalty) // only penalties
                .filter(charge -> !charge.isWaived()) // exclude waived
                .filter(charge -> !charge.isPaid()) // exclude already paid charges
                .filter(charge -> isChargeApplicable(charge, lower, upper)).map(LoanChargeData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate getFirstPendingInstallmentDate(LocalDate transactionDate) {
        return loanInstallments.stream()
                .filter(p -> p.status != ExtendedLoanSchedulePeriodData.Status.PAID
                        && p.status != ExtendedLoanSchedulePeriodData.Status.SCHEDULED
                        && p.status != ExtendedLoanSchedulePeriodData.Status.DUE)
                .map(ExtendedLoanSchedulePeriodData::getDueDate).min(LocalDate::compareTo).orElse(transactionDate);
    }

    public BigDecimal getPrincipalDueForTransaction(LocalDate transactionDate) {
        ExtendedLoanSchedulePeriodData installment = resolveInstallmentByTransactionDate(transactionDate);
        return installment.getPrincipalDue();
    }

    private boolean isChargeApplicable(LoanChargeData charge, LocalDate firstPendingInstallmentDate, LocalDate transactionDate) {

        LocalDate chargeDueDate = charge.getDueDate();
        if (chargeDueDate == null) {
            return false;
        }

        // Paying on an installment due date is on-time. LPI for that EMI is posted after midnight (charge dated
        // the next calendar day) and must not be quoted. Exclude a legacy charge dated on the due date itself;
        // the overnight charge is AFTER_TRANSACTION_DATE below.
        if (isOnInstallmentDueDate(transactionDate) && chargeDueDate.isEqual(transactionDate)) {
            return false;
        }

        return switch (PenaltyApplicabilityWindow.of(chargeDueDate, firstPendingInstallmentDate, transactionDate)) {
            case EQUAL_TO_FIRST_PENDING_INSTALLMENT, BETWEEN, EQUAL_TO_TRANSACTION_DATE -> true;
            default -> false;
        };
    }

    private boolean isOnInstallmentDueDate(final LocalDate transactionDate) {
        if (transactionDate == null) {
            return false;
        }
        return loanInstallments.stream().anyMatch(p -> p.getDueDate() != null && p.getDueDate().isEqual(transactionDate));
    }

    private boolean hasPendingEmiInstallment() {
        return loanInstallments.stream()
                .anyMatch(p -> p.status != ExtendedLoanSchedulePeriodData.Status.PAID
                        && (nullToZero(p.getPrincipalOutstanding()).compareTo(BigDecimal.ZERO) > 0
                                || nullToZero(p.getInterestOutstanding()).compareTo(BigDecimal.ZERO) > 0));
    }

    private BigDecimal sumUnpaidPenaltiesDueOnOrBefore(final LocalDate transactionDate) {
        return loanCharges.stream().filter(LoanChargeData::isPenalty).filter(charge -> !charge.isWaived())
                .filter(charge -> !charge.isPaid())
                .filter(charge -> charge.getDueDate() != null && !charge.getDueDate().isAfter(transactionDate))
                .map(LoanChargeData::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private enum PenaltyApplicabilityWindow {

        BEFORE_FIRST_PENDING_INSTALLMENT, EQUAL_TO_FIRST_PENDING_INSTALLMENT, BETWEEN, EQUAL_TO_TRANSACTION_DATE, AFTER_TRANSACTION_DATE;

        static PenaltyApplicabilityWindow of(LocalDate chargeDueDate, LocalDate lower, LocalDate upper) {
            if (chargeDueDate.isBefore(lower)) {
                return BEFORE_FIRST_PENDING_INSTALLMENT;
            }
            if (chargeDueDate.isEqual(lower)) {
                return EQUAL_TO_FIRST_PENDING_INSTALLMENT;
            }
            if (chargeDueDate.isAfter(lower) && chargeDueDate.isBefore(upper)) {
                return BETWEEN;
            }
            if (chargeDueDate.isEqual(upper)) {
                return EQUAL_TO_TRANSACTION_DATE;
            }
            return AFTER_TRANSACTION_DATE;
        }
    }

    public Collection<LoanChargeData> getApplicableCharges(LocalDate transactionDate) {
        LocalDate firstPendingInstallmentDate = this.getFirstPendingInstallmentDate(transactionDate);

        return loanCharges.stream().filter(LoanChargeData::isPenalty) // only penalties
                .filter(charge -> !charge.isWaived()) // exclude waived charges
                .filter(charge -> charge.getAmountPaid() == null || charge.getAmountPaid().compareTo(charge.getAmount()) < 0) // exclude
                                                                                                                              // fully
                                                                                                                              // paid
                                                                                                                              // charges
                .filter(charge -> isChargeApplicable(charge, firstPendingInstallmentDate, transactionDate)).toList();

    }

    public List<LoanChargeData> getPenaltiesToDisable(final LocalDate transactionDate, final Long loanId) {
        // Step 1: Get applicable charges for this transaction date
        Collection<LoanChargeData> applicableCharges = getApplicableCharges(transactionDate);

        if (applicableCharges.isEmpty()) {
            // If no applicable charges, return all penalties
            return loanCharges.stream().filter(LoanChargeData::isPenalty).sorted(Comparator.comparing(LoanChargeData::getDueDate))
                    .collect(Collectors.toList());
        }

        // Step 2: Find the latest due date among applicable charges
        LocalDate latestApplicableDueDate = applicableCharges.stream().map(LoanChargeData::getDueDate).max(LocalDate::compareTo)
                .orElse(transactionDate);

        // Step 3: Collect penalties that are after the latest applicable charge due date
        return loanCharges.stream().filter(LoanChargeData::isPenalty) // only penalties
                .filter(charge -> charge.getDueDate() != null && charge.getDueDate().isAfter(latestApplicableDueDate)) // after
                                                                                                                       // latest
                                                                                                                       // applicable
                .sorted(Comparator.comparing(LoanChargeData::getDueDate)) // ascending due date
                .collect(Collectors.toList());
    }

    public BigDecimal calculateTotalOutstandingPrincipal(LocalDate transactionDate) {
        LocalDate firstPendingInstallmentDate = getFirstPendingInstallmentDate(transactionDate);
        ExtendedLoanSchedulePeriodData targetInstallment = resolveInstallmentByTransactionDate(transactionDate);

        // For drawdown loans with early repayment, use the first installment's due date as lower bound
        LocalDate lowerBound = determineLowerBoundForOutstandingCalculation(transactionDate, firstPendingInstallmentDate);

        return loanInstallments.stream().filter(p -> !p.getDueDate().isBefore(lowerBound)) // on or after lower bound
                .filter(p -> !p.getDueDate().isAfter(targetInstallment.getDueDate())) // on or before target
                .map(p -> principalOutstandingForTransactionDate(p, transactionDate)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Remaining principal across every installment as of {@code transactionDate}. Used for full-settlement close amount
     * (mifos-standard / pro-rata-mifos-standard apply extra funds to later principal; they do not collect future EMI
     * interest).
     */
    public BigDecimal calculateRemainingPrincipalOutstanding(LocalDate transactionDate) {
        return loanInstallments.stream().map(p -> principalOutstandingForTransactionDate(p, transactionDate)).reduce(BigDecimal.ZERO,
                BigDecimal::add);
    }

    private BigDecimal principalOutstandingForTransactionDate(final ExtendedLoanSchedulePeriodData period,
            final LocalDate transactionDate) {
        if (useAsOfPayments(transactionDate)) {
            final InstallmentPaymentsAsOf paid = paymentsOnOrBeforeValueDate.getOrDefault(period.getPeriod(), InstallmentPaymentsAsOf.ZERO);
            final BigDecimal principalDue = nullToZero(period.getPrincipalDue());
            final BigDecimal writtenOff = nullToZero(period.getPrincipalWrittenOff());
            final BigDecimal outstanding = principalDue.subtract(paid.principalPaid()).subtract(writtenOff);
            return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
        }
        return nullToZero(period.getPrincipalOutstanding());
    }

    public BigDecimal calculateTotalOutstandingInterest(LocalDate transactionDate) {
        LocalDate firstPendingInstallmentDate = getFirstPendingInstallmentDate(transactionDate);
        ExtendedLoanSchedulePeriodData targetInstallment = resolveInstallmentByTransactionDate(transactionDate);

        // For drawdown loans with early repayment, use the first installment's due date as lower bound
        LocalDate lowerBound = determineLowerBoundForOutstandingCalculation(transactionDate, firstPendingInstallmentDate);

        return loanInstallments.stream().filter(p -> !p.getDueDate().isBefore(lowerBound)) // on or after lower bound
                .filter(p -> !p.getDueDate().isAfter(targetInstallment.getDueDate())) // on or before target
                .map(p -> interestOutstandingForTransactionDate(p, transactionDate)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * For early settlement (transaction before due date), outstanding interest is based on pro-rated charged interest
     * for days used in the period, minus already paid/waived amounts — matching backend
     * {@code EarlyRepaymentInterestHookImpl}.
     */
    private BigDecimal interestOutstandingForTransactionDate(final ExtendedLoanSchedulePeriodData period, final LocalDate transactionDate) {
        if (useAsOfPayments(transactionDate) && period.getDueDate() != null && !transactionDate.isBefore(period.getDueDate())) {
            final InstallmentPaymentsAsOf paid = paymentsOnOrBeforeValueDate.getOrDefault(period.getPeriod(), InstallmentPaymentsAsOf.ZERO);
            final BigDecimal interestDue = nullToZero(period.getInterestDue());
            final BigDecimal waived = nullToZero(period.getInterestWaived());
            final BigDecimal writtenOff = nullToZero(period.getInterestWrittenOff());
            final BigDecimal actualDue = interestDue.subtract(waived).subtract(writtenOff);
            final BigDecimal outstanding = actualDue.subtract(paid.interestPaid());
            return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
        }
        final BigDecimal outstanding = nullToZero(period.getInterestOutstanding());
        if (transactionDate == null || period.getDueDate() == null || !transactionDate.isBefore(period.getDueDate())) {
            return outstanding;
        }
        final BigDecimal paid = nullToZero(period.getInterestPaid());
        final BigDecimal waived = nullToZero(period.getInterestWaived());
        final BigDecimal writtenOff = nullToZero(period.getInterestWrittenOff());
        final BigDecimal charged = outstanding.add(paid).add(waived).add(writtenOff);
        BigDecimal proratedCharged = EarlyRepaymentInterestCalculator.calculateProRatedInterest(charged, period.getFromDate(),
                period.getDueDate(), transactionDate);
        // No genuine reduction (e.g. fromDate unknown, or transaction on/after due date): keep the schedule's own
        // scale rather than forcing currency precision on an unmodified amount.
        if (proratedCharged == null || proratedCharged.compareTo(charged) >= 0) {
            return outstanding;
        }
        proratedCharged = proratedCharged.setScale(2, RoundingMode.HALF_UP);
        final BigDecimal proratedOutstanding = proratedCharged.subtract(paid).subtract(waived).subtract(writtenOff);
        return proratedOutstanding.compareTo(BigDecimal.ZERO) > 0 ? proratedOutstanding : BigDecimal.ZERO;
    }

    private boolean useAsOfPayments(final LocalDate transactionDate) {
        return paymentsOnOrBeforeValueDate != null && businessDate != null && transactionDate != null
                && transactionDate.isBefore(businessDate);
    }

    private static BigDecimal nullToZero(final BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private ExtendedLoanSchedulePeriodData resolveInstallmentByTransactionDate(LocalDate transactionDate) {
        // First, try to find an installment whose period contains the transaction date (normal repayment case)
        // Skip PAID installments when checking periods
        for (int i = 0; i < loanInstallments.size(); i++) {
            ExtendedLoanSchedulePeriodData currentInstallment = loanInstallments.get(i);

            // Skip PAID installments for period matching
            if (currentInstallment.status == ExtendedLoanSchedulePeriodData.Status.PAID) {
                continue;
            }

            LocalDate currentDueDate = currentInstallment.getDueDate();
            LocalDate nextDueDate = (i + 1 < loanInstallments.size()) ? loanInstallments.get(i + 1).getDueDate() : null;

            if (nextDueDate != null && (!transactionDate.isBefore(currentDueDate) && transactionDate.isBefore(nextDueDate))) {
                return currentInstallment;
            }

            if (nextDueDate == null && !transactionDate.isBefore(currentDueDate)) {
                return currentInstallment;
            }
        }

        // If no installment period contains the transaction date, this is an early repayment
        // Find the first unpaid installment whose due date is after the transaction date
        ExtendedLoanSchedulePeriodData firstUnpaidInstallment = loanInstallments.stream()
                .filter(p -> p.status != ExtendedLoanSchedulePeriodData.Status.PAID && transactionDate.isBefore(p.getDueDate()))
                .min(Comparator.comparing(ExtendedLoanSchedulePeriodData::getDueDate)).orElse(null);

        if (firstUnpaidInstallment != null) {
            return firstUnpaidInstallment;
        }

        // Fallback: if all installments are paid or transaction date is after all due dates,
        // return the last installment
        if (!loanInstallments.isEmpty()) {
            return loanInstallments.get(loanInstallments.size() - 1);
        }

        throw new PlatformApiDataValidationException(
                List.of(ApiParameterError.parameterError("validation.msg.transactionDate.before.firstInstallment",
                        "Transaction date is before the first installment due date.", "transactionDate", transactionDate)));
    }

    public BigDecimal getInterestDueForTransaction(LocalDate transactionDate) {
        // First, try to find an installment whose period contains the transaction date (normal repayment case)
        // Skip PAID installments when checking periods
        for (int i = 0; i < loanInstallments.size(); i++) {
            ExtendedLoanSchedulePeriodData currentInstallment = loanInstallments.get(i);

            // Skip PAID installments for period matching
            if (currentInstallment.status == ExtendedLoanSchedulePeriodData.Status.PAID) {
                continue;
            }

            LocalDate currentDueDate = currentInstallment.getDueDate();
            LocalDate nextDueDate = (i + 1 < loanInstallments.size()) ? loanInstallments.get(i + 1).getDueDate() : null;

            if (nextDueDate != null && (!transactionDate.isBefore(currentDueDate) && transactionDate.isBefore(nextDueDate))) {
                return interestOutstandingForTransactionDate(currentInstallment, transactionDate);
            }

            if (nextDueDate == null && !transactionDate.isBefore(currentDueDate)) {
                return interestOutstandingForTransactionDate(currentInstallment, transactionDate);
            }
        }

        // If no installment period contains the transaction date, this is an early repayment
        // Find the first unpaid installment whose due date is after the transaction date
        ExtendedLoanSchedulePeriodData firstUnpaidInstallment = loanInstallments.stream()
                .filter(p -> p.status != ExtendedLoanSchedulePeriodData.Status.PAID && transactionDate.isBefore(p.getDueDate()))
                .min(Comparator.comparing(ExtendedLoanSchedulePeriodData::getDueDate)).orElse(null);

        if (firstUnpaidInstallment != null) {
            return interestOutstandingForTransactionDate(firstUnpaidInstallment, transactionDate);
        }

        // Fallback: if all installments are paid or transaction date is after all due dates,
        // return interest from the last installment
        if (!loanInstallments.isEmpty()) {
            return interestOutstandingForTransactionDate(loanInstallments.get(loanInstallments.size() - 1), transactionDate);
        }

        throw new PlatformApiDataValidationException(
                List.of(ApiParameterError.parameterError("validation.msg.transactionDate.before.firstInstallment",
                        "Transaction date is before the first installment due date.", "transactionDate", transactionDate)));
    }

    /**
     * Determines the lower bound date for penalty calculation. For drawdown loans with early repayment, uses
     * transaction date; otherwise uses first pending installment date.
     */
    private LocalDate determineLowerBoundForPenaltyCalculation(LocalDate transactionDate, LocalDate firstPendingInstallmentDate) {
        return isDrawdownLoan && transactionDate.isBefore(firstPendingInstallmentDate) ? transactionDate : firstPendingInstallmentDate;
    }

    /**
     * Determines the lower bound date for outstanding principal/interest calculation. For drawdown loans with early
     * repayment, uses the first installment's due date; otherwise uses first pending installment date. Note: This
     * method assumes loanInstallments is non-empty as it's called after resolveInstallmentByTransactionDate.
     */
    private LocalDate determineLowerBoundForOutstandingCalculation(LocalDate transactionDate, LocalDate firstPendingInstallmentDate) {
        if (isDrawdownLoan && transactionDate.isBefore(firstPendingInstallmentDate) && !loanInstallments.isEmpty()) {
            return loanInstallments.get(0).getDueDate();
        }
        return firstPendingInstallmentDate;
    }

    /**
     * Returns true if any paid installment's principalDue equals the charge's amountPercentageAppliedTo.
     */
    private boolean isAmountPercentageAppliedToAlreadyPaid(LoanChargeData charge) {
        BigDecimal appliedTo = charge.getAmountPercentageAppliedTo();
        if (appliedTo == null) {
            return false; // no restriction if not set
        }

        return loanInstallments.stream().filter(p -> p.status == ExtendedLoanSchedulePeriodData.Status.PAID)
                .anyMatch(p -> appliedTo.compareTo(p.getPrincipalDue()) == 0);
    }

}
