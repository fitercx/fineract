package com.crediblex.fineract.portfolio.loanaccount.domain;

import com.crediblex.fineract.portfolio.loanaccount.data.ExtendedLoanSchedulePeriodData;
import com.crediblex.fineract.portfolio.loanaccount.service.CredXLoanChargeWritePlatformServiceImpl;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanBalanceChangedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.charge.LoanWaiveChargeBusinessEvent;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBePayedException;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBeWaivedException;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByData;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.fineract.commands.service.CommandWrapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Getter
public class CredibleXLoanPenaltyCalculator {

    private final List<ExtendedLoanSchedulePeriodData> loanInstallments;
    private final List<LoanChargeData> loanCharges;

    public CredibleXLoanPenaltyCalculator(List<ExtendedLoanSchedulePeriodData> periods,
                                          Collection<LoanChargeData> loanCharges) {
        // Always store installments sorted by period number
        this.loanInstallments = periods.stream()
                .sorted(Comparator.comparingInt(ExtendedLoanSchedulePeriodData::getPeriod))
                .toList();

        // Always store charges sorted by due date (nulls last to avoid NPE issues)
        this.loanCharges = loanCharges.stream()
                .sorted(Comparator.comparing(LoanChargeData::getDueDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public BigDecimal calculatePenaltySum(LocalDate transactionDate) {
        final BigDecimal principalDueForInstallment = getPrincipalDueForTransaction(transactionDate);

        LocalDate firstPendingInstallmentDate = loanInstallments.stream()
                .filter(p -> p.status != ExtendedLoanSchedulePeriodData.Status.PAID
                        && p.status != ExtendedLoanSchedulePeriodData.Status.SCHEDULED
                        && p.status != ExtendedLoanSchedulePeriodData.Status.DUE)
                .map(ExtendedLoanSchedulePeriodData::getDueDate)
                .min(LocalDate::compareTo)
                .orElse(transactionDate);

        // Business rule validation
        if (transactionDate.isBefore(firstPendingInstallmentDate)) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(
                    "validation.msg.transactionDate.before.nextPeriodDueDate",
                    "The parameter `transactionDate` cannot be before the first unpaid installment: " + firstPendingInstallmentDate,
                    "transactionDate",
                    transactionDate,
                    firstPendingInstallmentDate
            )));
        }

        LocalDate lower = firstPendingInstallmentDate;
        LocalDate upper = transactionDate;

        return loanCharges.stream()
                .filter(LoanChargeData::isPenalty)                    // only penalties
                .filter(charge -> !charge.isWaived())                 // exclude waived
                .filter(charge -> isChargeApplicable(charge, lower, upper, principalDueForInstallment))
                .map(LoanChargeData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getPrincipalDueForTransaction(LocalDate transactionDate) {
        for (int i = 0; i < loanInstallments.size(); i++) {
            LocalDate currentDueDate = loanInstallments.get(i).getDueDate();
            LocalDate nextDueDate = (i + 1 < loanInstallments.size())
                    ? loanInstallments.get(i + 1).getDueDate()
                    : null;

            if (nextDueDate != null &&
                    (!transactionDate.isBefore(currentDueDate) && transactionDate.isBefore(nextDueDate))) {
                return loanInstallments.get(i).getPrincipalDue();
            }

            if (nextDueDate == null && !transactionDate.isBefore(currentDueDate)) {
                return loanInstallments.get(i).getPrincipalDue();
            }
        }

        throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(
                "validation.msg.transactionDate.before.firstInstallment",
                "Transaction date is before the first installment due date.",
                "transactionDate",
                transactionDate
        )));
    }

    private boolean isChargeApplicable(
            LoanChargeData charge,
            LocalDate firstPendingInstallmentDate,
            LocalDate transactionDate,
            BigDecimal principalDueForInstallment) {

        LocalDate due = charge.getDueDate();
        if (due == null) {
            return false;
        }

        return switch (PenaltyApplicabilityWindow.of(due, firstPendingInstallmentDate, transactionDate)) {
            case EQUAL_TO_FIRST_PENDING_INSTALLMENT, BETWEEN -> true;
            case EQUAL_TO_TRANSACTION_DATE ->
                    charge.getAmountPercentageAppliedTo() == null
                            || !charge.getAmountPercentageAppliedTo().equals(principalDueForInstallment);
            default -> false;
        };
    }

    private enum PenaltyApplicabilityWindow {
        BEFORE_FIRST_PENDING_INSTALLMENT,
        EQUAL_TO_FIRST_PENDING_INSTALLMENT,
        BETWEEN,
        EQUAL_TO_TRANSACTION_DATE,
        AFTER_TRANSACTION_DATE;

        static PenaltyApplicabilityWindow of(LocalDate date, LocalDate lower, LocalDate upper) {
            if (date.isBefore(lower)) return BEFORE_FIRST_PENDING_INSTALLMENT;
            if (date.isEqual(lower)) return EQUAL_TO_FIRST_PENDING_INSTALLMENT;
            if (date.isAfter(lower) && date.isBefore(upper)) return BETWEEN;
            if (date.isEqual(upper)) return EQUAL_TO_TRANSACTION_DATE;
            return AFTER_TRANSACTION_DATE;
        }
    }

    public Collection<LoanChargeData> getApplicableCharges(LocalDate transactionDate) {
        BigDecimal principalDueForInstallment = this.getPrincipalDueForTransaction(transactionDate);
        LocalDate firstPendingInstallmentDate = loanInstallments.stream()
                .filter(p -> p.status != ExtendedLoanSchedulePeriodData.Status.PAID
                        && p.status != ExtendedLoanSchedulePeriodData.Status.SCHEDULED
                        && p.status != ExtendedLoanSchedulePeriodData.Status.DUE)
                .map(ExtendedLoanSchedulePeriodData::getDueDate)
                .min(LocalDate::compareTo)
                .orElse(transactionDate);

        return loanCharges.stream()
                .filter(LoanChargeData::isPenalty) // only penalties
                .filter(charge -> !charge.isWaived()) // exclude waived charges
                .filter(charge -> isChargeApplicable(charge, firstPendingInstallmentDate, transactionDate, principalDueForInstallment))
                .toList();
    }

    public List<LoanChargeData> getPenaltyIdsToDisable(final LocalDate transactionDate, final Long loanId) {
        // Get applicable charges for this transaction date
        Collection<LoanChargeData> applicableCharges = getApplicableCharges(transactionDate);

        // Build a set of applicable charge IDs for quick lookup
        Set<Long> applicableChargeIds = applicableCharges.stream()
                .map(LoanChargeData::getId)
                .collect(Collectors.toSet());

        // Collect penalties that are NOT applicable, sort by due date ascending
        return loanCharges.stream()
                .filter(LoanChargeData::isPenalty) // only penalties
                .filter(charge -> !applicableChargeIds.contains(charge.getId())) // not in applicable set
                .sorted(Comparator.comparing(LoanChargeData::getDueDate)) // ascending due date
                .collect(Collectors.toList());
    }

}
