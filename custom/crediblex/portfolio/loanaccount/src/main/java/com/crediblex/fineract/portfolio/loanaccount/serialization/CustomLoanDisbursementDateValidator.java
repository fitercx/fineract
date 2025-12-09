package com.crediblex.fineract.portfolio.loanaccount.serialization;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.holiday.domain.Holiday;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.holiday.service.HolidayUtil;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.service.WorkingDaysUtil;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.exception.LoanApplicationDateException;
import org.springframework.stereotype.Component;

/**
 * Custom validator for updating future tranche disbursement dates on active loans.
 *
 * Validates that: - Only future (not yet disbursed) tranches can be updated - New date cannot be before past
 * disbursements - New date cannot be before past repayment instalments - New date respects holiday/working day rules -
 * New date respects business date rules
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomLoanDisbursementDateValidator {

    private final ConfigurationDomainService configurationDomainService;
    private final HolidayRepositoryWrapper holidayRepository;
    private final WorkingDaysRepositoryWrapper workingDaysRepository;

    /**
     * Validates that a future tranche disbursement date can be updated to the new date.
     *
     * @param loan
     *            The loan containing the tranche
     * @param disbursementDetails
     *            The tranche disbursement details to update
     * @param newExpectedDate
     *            The new expected disbursement date
     * @throws PlatformApiDataValidationException
     *             if validation fails
     */
    public void validateFutureTrancheDateUpdate(final Loan loan, final LoanDisbursementDetails disbursementDetails,
            final LocalDate newExpectedDate) {

        log.debug("Validating future tranche date update for loan {}: {} -> {}", loan.getId(),
                disbursementDetails.expectedDisbursementDate(), newExpectedDate);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("loan.update.disbursement");

        // 1. Verify tranche is not yet disbursed
        if (disbursementDetails.actualDisbursementDate() != null) {
            final String errorMessage = "Cannot update disbursement date for already disbursed tranche";
            baseDataValidator.reset().parameter("expectedDisbursementDate").failWithCode("error.msg.loan.tranche.already.disbursed",
                    errorMessage);
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        // 2. Validate new date is not null
        baseDataValidator.reset().parameter("expectedDisbursementDate").value(newExpectedDate).notNull();
        if (newExpectedDate == null) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        // 3. Validate new date is not before latest actual disbursement date
        validateNotBeforePastDisbursements(loan, newExpectedDate);

        // 4. Validate new date is not before past repayment instalments
        validateNotBeforePastInstalments(loan, newExpectedDate);

        // 5. Validate new date respects business date
        validateBusinessDate(newExpectedDate);

        // 6. Validate new date respects holiday/working day rules
        validateHolidayAndWorkingDayRules(loan, newExpectedDate);
    }

    /**
     * Validates that the new date is not before any already disbursed tranche.
     */
    private void validateNotBeforePastDisbursements(final Loan loan, final LocalDate newExpectedDate) {
        final LocalDate latestActualDisbursementDate = loan.getDisbursementDetails().stream()
                .filter(detail -> detail.actualDisbursementDate() != null).map(LoanDisbursementDetails::actualDisbursementDate)
                .max(LocalDate::compareTo).orElse(null);

        if (latestActualDisbursementDate != null && DateUtils.isBefore(newExpectedDate, latestActualDisbursementDate)) {
            final String errorMessage = String.format(
                    "New disbursement date (%s) cannot be before the latest actual disbursement date (%s)", newExpectedDate,
                    latestActualDisbursementDate);
            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                    .resource("loan.update.disbursement");
            baseDataValidator.reset().parameter("expectedDisbursementDate")
                    .failWithCode("error.msg.loan.tranche.date.before.past.disbursement", errorMessage);
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    /**
     * Validates that the new date is not before any past repayment instalment due date.
     */
    private void validateNotBeforePastInstalments(final Loan loan, final LocalDate newExpectedDate) {
        // Get earliest unpaid instalment due date
        LocalDate earliestUnpaidInstallmentDate = null;
        final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
        for (final LoanRepaymentScheduleInstallment installment : installments) {
            if (installment.isNotFullyPaidOff()) {
                earliestUnpaidInstallmentDate = installment.getDueDate();
                break;
            }
        }

        // Get last repayment transaction date
        LocalDate lastRepaymentTransactionDate = null;
        final List<LoanTransaction> transactions = loan.getLoanTransactions();
        for (final LoanTransaction transaction : transactions) {
            if (transaction.isRepaymentLikeType() && transaction.isNotReversed() && transaction.isGreaterThanZero()) {
                final LocalDate transactionDate = transaction.getTransactionDate();
                if (lastRepaymentTransactionDate == null || DateUtils.isAfter(transactionDate, lastRepaymentTransactionDate)) {
                    lastRepaymentTransactionDate = transactionDate;
                }
            }
        }

        // Use the later of the two dates (earliest unpaid instalment or last repayment transaction)
        LocalDate minimumAllowedDate = null;
        if (earliestUnpaidInstallmentDate != null && lastRepaymentTransactionDate != null) {
            minimumAllowedDate = DateUtils.isAfter(earliestUnpaidInstallmentDate, lastRepaymentTransactionDate)
                    ? earliestUnpaidInstallmentDate
                    : lastRepaymentTransactionDate;
        } else if (earliestUnpaidInstallmentDate != null) {
            minimumAllowedDate = earliestUnpaidInstallmentDate;
        } else if (lastRepaymentTransactionDate != null) {
            minimumAllowedDate = lastRepaymentTransactionDate;
        }

        if (minimumAllowedDate != null && DateUtils.isBefore(newExpectedDate, minimumAllowedDate)) {
            final String errorMessage = String.format(
                    "New disbursement date (%s) cannot be before past repayment instalments or transactions (minimum allowed: %s)",
                    newExpectedDate, minimumAllowedDate);
            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                    .resource("loan.update.disbursement");
            baseDataValidator.reset().parameter("expectedDisbursementDate")
                    .failWithCode("error.msg.loan.tranche.date.before.past.instalments", errorMessage);
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    /**
     * Validates that the new date is not before the business date.
     */
    private void validateBusinessDate(final LocalDate newExpectedDate) {
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        if (DateUtils.isBefore(newExpectedDate, businessDate)) {
            final String errorMessage = String.format("New disbursement date (%s) cannot be before the business date (%s)", newExpectedDate,
                    businessDate);
            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                    .resource("loan.update.disbursement");
            baseDataValidator.reset().parameter("expectedDisbursementDate").failWithCode("error.msg.loan.tranche.date.before.business.date",
                    errorMessage);
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    /**
     * Validates that the new date respects holiday and working day rules.
     */
    private void validateHolidayAndWorkingDayRules(final Loan loan, final LocalDate newExpectedDate) {
        final Long officeId = loan.getOfficeId();
        final List<Holiday> holidays = holidayRepository.findByOfficeIdAndGreaterThanDate(officeId, newExpectedDate);

        final WorkingDays workingDays = workingDaysRepository.findOne();
        final boolean allowTransactionsOnHoliday = configurationDomainService.allowTransactionsOnHolidayEnabled();
        final boolean allowTransactionsOnNonWorkingDay = configurationDomainService.allowTransactionsOnNonWorkingDayEnabled();

        // Validate working day
        if (!allowTransactionsOnNonWorkingDay && !WorkingDaysUtil.isWorkingDay(workingDays, newExpectedDate)) {
            final String errorMessage = "Expected disbursement date cannot be on a non working day";
            throw new LoanApplicationDateException("disbursement.date.on.non.working.day", errorMessage, newExpectedDate);
        }

        // Validate holiday
        if (!allowTransactionsOnHoliday && HolidayUtil.isHoliday(newExpectedDate, holidays)) {
            final String errorMessage = "Expected disbursement date cannot be on a holiday";
            throw new LoanApplicationDateException("disbursement.date.on.holiday", errorMessage, newExpectedDate);
        }
    }
}
