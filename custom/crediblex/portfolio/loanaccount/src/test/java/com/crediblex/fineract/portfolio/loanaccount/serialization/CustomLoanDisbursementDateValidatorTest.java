package com.crediblex.fineract.portfolio.loanaccount.serialization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for CustomLoanDisbursementDateValidator. Tests all validation rules for updating future tranche
 * disbursement dates.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomLoanDisbursementDateValidatorTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2024, 3, 15);
    private static final LocalDate PAST_DISBURSEMENT_DATE = LocalDate.of(2024, 1, 15);
    private static final LocalDate FUTURE_DISBURSEMENT_DATE = LocalDate.of(2024, 4, 15);
    private static final LocalDate VALID_NEW_DATE = LocalDate.of(2024, 5, 15);
    private static final LocalDate PAST_INSTALMENT_DATE = LocalDate.of(2024, 2, 15);
    private static final Long LOAN_ID = 1L;
    private static final Long OFFICE_ID = 100L;

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Mock
    private HolidayRepositoryWrapper holidayRepository;

    @Mock
    private WorkingDaysRepositoryWrapper workingDaysRepository;

    @Mock
    private Loan loan;

    @Mock
    private LoanDisbursementDetails disbursementDetails;

    @Mock
    private LoanDisbursementDetails pastDisbursementDetails;

    @Mock
    private LoanRepaymentScheduleInstallment unpaidInstallment;

    @Mock
    private LoanRepaymentScheduleInstallment paidInstallment;

    @Mock
    private LoanTransaction repaymentTransaction;

    @Mock
    private WorkingDays workingDays;

    @Mock
    private Holiday holiday;

    @InjectMocks
    private CustomLoanDisbursementDateValidator validator;

    @BeforeEach
    void setUp() {
        // Setup loan
        when(loan.getId()).thenReturn(LOAN_ID);
        when(loan.getOfficeId()).thenReturn(OFFICE_ID);

        // Setup disbursement details (future tranche - not yet disbursed)
        when(disbursementDetails.actualDisbursementDate()).thenReturn(null);
        when(disbursementDetails.expectedDisbursementDate()).thenReturn(FUTURE_DISBURSEMENT_DATE);

        // Setup past disbursement details
        when(pastDisbursementDetails.actualDisbursementDate()).thenReturn(PAST_DISBURSEMENT_DATE);

        // Setup working days
        when(workingDaysRepository.findOne()).thenReturn(workingDays);

        // Setup holidays (empty list by default)
        when(holidayRepository.findByOfficeIdAndGreaterThanDate(eq(OFFICE_ID), any(LocalDate.class)))
                .thenReturn(new ArrayList<>());

        // Default configuration: allow transactions on holidays and non-working days
        when(configurationDomainService.allowTransactionsOnHolidayEnabled()).thenReturn(true);
        when(configurationDomainService.allowTransactionsOnNonWorkingDayEnabled()).thenReturn(true);
    }

    @Test
    void testValidateFutureTrancheDateUpdate_Success() {
        // Setup: No past disbursements, no past instalments
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(new ArrayList<>());
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());

        // Mock DateUtils.getBusinessLocalDate() to return a date before the new date
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isBefore(date2);
            });

            // Should not throw any exception
            assertDoesNotThrow(() -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, VALID_NEW_DATE));
        }
    }

    @Test
    void testValidateFutureTrancheDateUpdate_AlreadyDisbursed_ThrowsException() {
        // Setup: Tranche is already disbursed
        when(disbursementDetails.actualDisbursementDate()).thenReturn(PAST_DISBURSEMENT_DATE);

        // Should throw PlatformApiDataValidationException
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, VALID_NEW_DATE));

        assertNotNull(exception);
    }

    @Test
    void testValidateFutureTrancheDateUpdate_NullDate_ThrowsException() {
        // Setup: No past disbursements
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));

        // Should throw PlatformApiDataValidationException
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, null));

        assertNotNull(exception);
    }

    @Test
    void testValidateNotBeforePastDisbursements_ThrowsException() {
        // Setup: Has past disbursement
        when(loan.getDisbursementDetails()).thenReturn(List.of(pastDisbursementDetails, disbursementDetails));

        // Mock DateUtils
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isBefore(date2);
            });
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // New date is before past disbursement date
            LocalDate newDateBeforePastDisbursement = PAST_DISBURSEMENT_DATE.minusDays(1);

            // Should throw PlatformApiDataValidationException
            PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                    () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, newDateBeforePastDisbursement));

            assertNotNull(exception);
        }
    }

    @Test
    void testValidateNotBeforePastDisbursements_Success() {
        // Setup: Has past disbursement, but new date is after it
        when(loan.getDisbursementDetails()).thenReturn(List.of(pastDisbursementDetails, disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(new ArrayList<>());
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());

        // Mock DateUtils
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            // Set business date to be before the new date
            LocalDate businessDateBeforeNewDate = PAST_DISBURSEMENT_DATE.plusDays(5);
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(businessDateBeforeNewDate);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isBefore(date2);
            });

            // New date is after both past disbursement date and business date
            LocalDate newDateAfterPastDisbursement = PAST_DISBURSEMENT_DATE.plusDays(20);

            // Should not throw exception
            assertDoesNotThrow(
                    () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, newDateAfterPastDisbursement));
        }
    }

    @Test
    void testValidateNotBeforePastInstalments_UnpaidInstallment_ThrowsException() {
        // Setup: Has unpaid instalment
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(unpaidInstallment));
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());
        when(unpaidInstallment.isNotFullyPaidOff()).thenReturn(true);
        when(unpaidInstallment.getDueDate()).thenReturn(PAST_INSTALMENT_DATE);

        // Mock DateUtils
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isBefore(date2);
            });
            dateUtilsMock.when(() -> DateUtils.isAfter(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isAfter(date2);
            });

            // New date is before unpaid instalment date
            LocalDate newDateBeforeInstalment = PAST_INSTALMENT_DATE.minusDays(1);

            // Should throw PlatformApiDataValidationException
            PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                    () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, newDateBeforeInstalment));

            assertNotNull(exception);
        }
    }

    @Test
    void testValidateNotBeforePastInstalments_RepaymentTransaction_ThrowsException() {
        // Setup: Has repayment transaction
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(new ArrayList<>());
        when(loan.getLoanTransactions()).thenReturn(List.of(repaymentTransaction));
        when(repaymentTransaction.isRepaymentLikeType()).thenReturn(true);
        when(repaymentTransaction.isNotReversed()).thenReturn(true);
        when(repaymentTransaction.isGreaterThanZero()).thenReturn(true);
        when(repaymentTransaction.getTransactionDate()).thenReturn(PAST_INSTALMENT_DATE);

        // Mock DateUtils
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isBefore(date2);
            });
            dateUtilsMock.when(() -> DateUtils.isAfter(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isAfter(date2);
            });

            // New date is before repayment transaction date
            LocalDate newDateBeforeTransaction = PAST_INSTALMENT_DATE.minusDays(1);

            // Should throw PlatformApiDataValidationException
            PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                    () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, newDateBeforeTransaction));

            assertNotNull(exception);
        }
    }

    @Test
    void testValidateBusinessDate_BeforeBusinessDate_ThrowsException() {
        // Setup: No past disbursements or instalments
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(new ArrayList<>());
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());

        // Mock DateUtils to return a business date after the new date
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            LocalDate futureBusinessDate = LocalDate.of(2024, 6, 15);
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(futureBusinessDate);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isBefore(date2);
            });

            // New date is before business date
            LocalDate newDateBeforeBusinessDate = BUSINESS_DATE;

            // Should throw PlatformApiDataValidationException
            PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                    () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, newDateBeforeBusinessDate));

            assertNotNull(exception);
        }
    }

    @Test
    void testValidateHolidayAndWorkingDayRules_OnHoliday_ThrowsException() {
        // Setup: No past disbursements or instalments
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(new ArrayList<>());
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());

        // Configuration: Do not allow transactions on holidays
        when(configurationDomainService.allowTransactionsOnHolidayEnabled()).thenReturn(false);
        when(holidayRepository.findByOfficeIdAndGreaterThanDate(eq(OFFICE_ID), any(LocalDate.class)))
                .thenReturn(List.of(holiday));

        // Mock DateUtils and HolidayUtil
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class);
                MockedStatic<HolidayUtil> holidayUtilMock = mockStatic(HolidayUtil.class);
                MockedStatic<WorkingDaysUtil> workingDaysUtilMock = mockStatic(WorkingDaysUtil.class)) {

            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenReturn(false);
            holidayUtilMock.when(() -> HolidayUtil.isHoliday(any(LocalDate.class), anyList())).thenReturn(true);
            workingDaysUtilMock.when(() -> WorkingDaysUtil.isWorkingDay(any(), any())).thenReturn(true);

            // Should throw LoanApplicationDateException
            LoanApplicationDateException exception = assertThrows(LoanApplicationDateException.class,
                    () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, VALID_NEW_DATE));

            assertNotNull(exception);
            assertTrue(exception.getGlobalisationMessageCode().contains("holiday"));
        }
    }

    @Test
    void testValidateHolidayAndWorkingDayRules_OnNonWorkingDay_ThrowsException() {
        // Setup: No past disbursements or instalments
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(new ArrayList<>());
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());

        // Configuration: Do not allow transactions on non-working days
        when(configurationDomainService.allowTransactionsOnNonWorkingDayEnabled()).thenReturn(false);
        when(holidayRepository.findByOfficeIdAndGreaterThanDate(eq(OFFICE_ID), any(LocalDate.class)))
                .thenReturn(new ArrayList<>());

        // Mock DateUtils and WorkingDaysUtil
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class);
                MockedStatic<WorkingDaysUtil> workingDaysUtilMock = mockStatic(WorkingDaysUtil.class)) {

            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenReturn(false);
            workingDaysUtilMock.when(() -> WorkingDaysUtil.isWorkingDay(any(), any())).thenReturn(false);

            // Should throw LoanApplicationDateException
            LoanApplicationDateException exception = assertThrows(LoanApplicationDateException.class,
                    () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, VALID_NEW_DATE));

            assertNotNull(exception);
            assertTrue(exception.getGlobalisationMessageCode().contains("non.working.day"));
        }
    }

    @Test
    void testValidateHolidayAndWorkingDayRules_AllowedOnHoliday_Success() {
        // Setup: No past disbursements or instalments
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(new ArrayList<>());
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());

        // Configuration: Allow transactions on holidays
        when(configurationDomainService.allowTransactionsOnHolidayEnabled()).thenReturn(true);
        when(holidayRepository.findByOfficeIdAndGreaterThanDate(eq(OFFICE_ID), any(LocalDate.class)))
                .thenReturn(List.of(holiday));

        // Mock DateUtils and HolidayUtil
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class);
                MockedStatic<HolidayUtil> holidayUtilMock = mockStatic(HolidayUtil.class);
                MockedStatic<WorkingDaysUtil> workingDaysUtilMock = mockStatic(WorkingDaysUtil.class)) {

            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenReturn(false);
            holidayUtilMock.when(() -> HolidayUtil.isHoliday(any(LocalDate.class), anyList())).thenReturn(true);
            workingDaysUtilMock.when(() -> WorkingDaysUtil.isWorkingDay(any(), any())).thenReturn(true);

            // Should not throw exception (holidays are allowed)
            assertDoesNotThrow(() -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, VALID_NEW_DATE));
        }
    }

    @Test
    void testValidateNotBeforePastInstalments_NoPastInstalments_Success() {
        // Setup: No past instalments or transactions
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(new ArrayList<>());
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());

        // Mock DateUtils
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenReturn(false);

            // Should not throw exception
            assertDoesNotThrow(() -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, VALID_NEW_DATE));
        }
    }

    @Test
    void testValidateNotBeforePastInstalments_PaidInstallment_Ignored() {
        // Setup: Has paid instalment (should be ignored)
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(paidInstallment));
        when(loan.getLoanTransactions()).thenReturn(new ArrayList<>());
        when(paidInstallment.isNotFullyPaidOff()).thenReturn(false);

        // Mock DateUtils
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenReturn(false);

            // Should not throw exception (paid instalments are ignored)
            assertDoesNotThrow(() -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, VALID_NEW_DATE));
        }
    }

    @Test
    void testValidateNotBeforePastInstalments_ReversedTransaction_Ignored() {
        // Setup: Has reversed repayment transaction (should be ignored)
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(new ArrayList<>());
        when(loan.getLoanTransactions()).thenReturn(List.of(repaymentTransaction));
        when(repaymentTransaction.isRepaymentLikeType()).thenReturn(true);
        when(repaymentTransaction.isNotReversed()).thenReturn(false); // Reversed transaction

        // Mock DateUtils
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenReturn(false);

            // Should not throw exception (reversed transactions are ignored)
            assertDoesNotThrow(() -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, VALID_NEW_DATE));
        }
    }

    @Test
    void testValidateNotBeforePastInstalments_UsesLaterDate() {
        // Setup: Has both unpaid instalment and repayment transaction
        when(loan.getDisbursementDetails()).thenReturn(List.of(disbursementDetails));
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(unpaidInstallment));
        when(loan.getLoanTransactions()).thenReturn(List.of(repaymentTransaction));
        when(unpaidInstallment.isNotFullyPaidOff()).thenReturn(true);
        when(unpaidInstallment.getDueDate()).thenReturn(PAST_INSTALMENT_DATE);
        when(repaymentTransaction.isRepaymentLikeType()).thenReturn(true);
        when(repaymentTransaction.isNotReversed()).thenReturn(true);
        when(repaymentTransaction.isGreaterThanZero()).thenReturn(true);
        LocalDate laterTransactionDate = PAST_INSTALMENT_DATE.plusDays(10);
        when(repaymentTransaction.getTransactionDate()).thenReturn(laterTransactionDate);

        // Mock DateUtils
        try (MockedStatic<DateUtils> dateUtilsMock = mockStatic(DateUtils.class)) {
            dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);
            dateUtilsMock.when(() -> DateUtils.isBefore(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isBefore(date2);
            });
            dateUtilsMock.when(() -> DateUtils.isAfter(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
                LocalDate date1 = invocation.getArgument(0);
                LocalDate date2 = invocation.getArgument(1);
                return date1.isAfter(date2);
            });

            // New date is before the later transaction date but after instalment date
            LocalDate newDateBetweenDates = PAST_INSTALMENT_DATE.plusDays(5);

            // Should throw exception (uses the later date - transaction date)
            PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                    () -> validator.validateFutureTrancheDateUpdate(loan, disbursementDetails, newDateBetweenDates));

            assertNotNull(exception);
        }
    }
}
