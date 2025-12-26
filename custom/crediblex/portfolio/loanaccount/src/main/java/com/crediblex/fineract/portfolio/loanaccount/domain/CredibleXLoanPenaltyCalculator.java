package com.crediblex.fineract.portfolio.loanaccount.domain;

import com.crediblex.fineract.portfolio.loanaccount.data.ExtendedLoanSchedulePeriodData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

    public CredibleXLoanPenaltyCalculator(List<ExtendedLoanSchedulePeriodData> periods, Collection<LoanChargeData> loanCharges,
            long penaltyWaitPeriodValue) {
        this(periods, loanCharges, penaltyWaitPeriodValue, false);
    }

    public CredibleXLoanPenaltyCalculator(List<ExtendedLoanSchedulePeriodData> periods, Collection<LoanChargeData> loanCharges,
            long penaltyWaitPeriodValue, boolean isDrawdownLoan) {
        // Always store installments sorted by period number
        this.loanInstallments = periods.stream().sorted(Comparator.comparingInt(ExtendedLoanSchedulePeriodData::getPeriod)).toList();

        // Always store charges sorted by due date (nulls last to avoid NPE issues)
        this.loanCharges = loanCharges.stream()
                .sorted(Comparator.comparing(LoanChargeData::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        this.penaltyWaitPeriodValue = penaltyWaitPeriodValue;
        this.isDrawdownLoan = isDrawdownLoan;
    }

    public BigDecimal calculatePenaltySum(LocalDate transactionDate) {

        final LocalDate firstPendingInstallmentDate = getFirstPendingInstallmentDate(transactionDate);

        // Business rule validation - allow early repayments for drawdown loans
        if (transactionDate.isBefore(firstPendingInstallmentDate) && !isDrawdownLoan) {
            throw new PlatformApiDataValidationException(
                    List.of(ApiParameterError.parameterError("validation.msg.transactionDate.before.nextPeriodDueDate",
                            "The parameter `transactionDate` cannot be before the first unpaid installment: " + firstPendingInstallmentDate,
                            "transactionDate", transactionDate, firstPendingInstallmentDate)));
        }

        // For drawdown loans with early repayment, use transaction date as lower bound
        LocalDate lower = isDrawdownLoan && transactionDate.isBefore(firstPendingInstallmentDate) 
                ? transactionDate 
                : firstPendingInstallmentDate;
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

        return switch (PenaltyApplicabilityWindow.of(chargeDueDate, firstPendingInstallmentDate, transactionDate)) {
            case EQUAL_TO_FIRST_PENDING_INSTALLMENT, BETWEEN -> true;
            default -> false;
        };
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
                .filter(charge -> charge.getDueDate() != null && !charge.getDueDate().isBefore(latestApplicableDueDate)) // after
                                                                                                                         // latest
                                                                                                                         // applicable
                .sorted(Comparator.comparing(LoanChargeData::getDueDate)) // ascending due date
                .collect(Collectors.toList());
    }

    public BigDecimal calculateTotalOutstandingPrincipal(LocalDate transactionDate) {
        LocalDate firstPendingInstallmentDate = getFirstPendingInstallmentDate(transactionDate);
        ExtendedLoanSchedulePeriodData targetInstallment = resolveInstallmentByTransactionDate(transactionDate);

        // For drawdown loans with early repayment, use the first installment's due date as lower bound
        LocalDate lowerBound = isDrawdownLoan && transactionDate.isBefore(firstPendingInstallmentDate) 
                && !loanInstallments.isEmpty() 
                ? loanInstallments.get(0).getDueDate() 
                : firstPendingInstallmentDate;

        return loanInstallments.stream().filter(p -> !p.getDueDate().isBefore(lowerBound)) // on or after lower bound
                .filter(p -> !p.getDueDate().isAfter(targetInstallment.getDueDate())) // on or before target
                .map(ExtendedLoanSchedulePeriodData::getPrincipalOutstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalOutstandingInterest(LocalDate transactionDate) {
        LocalDate firstPendingInstallmentDate = getFirstPendingInstallmentDate(transactionDate);
        ExtendedLoanSchedulePeriodData targetInstallment = resolveInstallmentByTransactionDate(transactionDate);

        // For drawdown loans with early repayment, use the first installment's due date as lower bound
        LocalDate lowerBound = isDrawdownLoan && transactionDate.isBefore(firstPendingInstallmentDate) 
                && !loanInstallments.isEmpty() 
                ? loanInstallments.get(0).getDueDate() 
                : firstPendingInstallmentDate;

        return loanInstallments.stream().filter(p -> !p.getDueDate().isBefore(lowerBound)) // on or after lower bound
                .filter(p -> !p.getDueDate().isAfter(targetInstallment.getDueDate())) // on or before target
                .map(ExtendedLoanSchedulePeriodData::getInterestOutstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ExtendedLoanSchedulePeriodData resolveInstallmentByTransactionDate(LocalDate transactionDate) {
        for (int i = 0; i < loanInstallments.size(); i++) {
            LocalDate currentDueDate = loanInstallments.get(i).getDueDate();
            LocalDate nextDueDate = (i + 1 < loanInstallments.size()) ? loanInstallments.get(i + 1).getDueDate() : null;

            if (nextDueDate != null && (!transactionDate.isBefore(currentDueDate) && transactionDate.isBefore(nextDueDate))) {
                return loanInstallments.get(i);
            }

            if (nextDueDate == null && !transactionDate.isBefore(currentDueDate)) {
                return loanInstallments.get(i);
            }
        }

        // For drawdown loans, allow early repayments before the first installment due date
        // Return the first installment in such cases
        if (isDrawdownLoan && !loanInstallments.isEmpty()) {
            return loanInstallments.get(0);
        }

        throw new PlatformApiDataValidationException(
                List.of(ApiParameterError.parameterError("validation.msg.transactionDate.before.firstInstallment",
                        "Transaction date is before the first installment due date.", "transactionDate", transactionDate)));
    }

    public BigDecimal getInterestDueForTransaction(LocalDate transactionDate) {
        for (int i = 0; i < loanInstallments.size(); i++) {
            LocalDate currentDueDate = loanInstallments.get(i).getDueDate();
            LocalDate nextDueDate = (i + 1 < loanInstallments.size()) ? loanInstallments.get(i + 1).getDueDate() : null;

            if (nextDueDate != null && (!transactionDate.isBefore(currentDueDate) && transactionDate.isBefore(nextDueDate))) {
                return loanInstallments.get(i).getInterestOutstanding();
            }

            if (nextDueDate == null && !transactionDate.isBefore(currentDueDate)) {
                return loanInstallments.get(i).getInterestOutstanding();
            }
        }

        // For drawdown loans, allow early repayments before the first installment due date
        // Return interest from the first installment in such cases
        if (isDrawdownLoan && !loanInstallments.isEmpty()) {
            return loanInstallments.get(0).getInterestOutstanding();
        }

        throw new PlatformApiDataValidationException(
                List.of(ApiParameterError.parameterError("validation.msg.transactionDate.before.firstInstallment",
                        "Transaction date is before the first installment due date.", "transactionDate", transactionDate)));
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
