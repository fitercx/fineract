package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.account.data.PortfolioAccountData;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBeWaivedException;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByData;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeApiJsonValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualTransactionBusinessEventService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.loanaccount.service.adjustment.LoanAdjustmentService;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CredXLoanChargeWritePlatformServiceImplTest {

    private static final Long LOAN_ID = 1L;
    private static final Long LOAN_CHARGE_ID = 2L;
    private static final String CURRENCY_CODE = "USD";
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2024, 1, 15);
    private static final BigDecimal ADJUSTMENT_AMOUNT = new BigDecimal("100.00");
    private static final String EXTERNAL_ID_VALUE = "ext-123";
    private static final String LOCALE = "en";
    private static final String NOTE_TEXT = "Test note";

    @Mock
    private JsonCommand jsonCommand;

    @Mock
    private LoanChargeApiJsonValidator loanChargeApiJsonValidator;

    @Mock
    private ExternalIdFactory externalIdFactory;

    @Mock
    private LoanAssembler loanAssembler;

    @Mock
    private PaymentDetailWritePlatformService paymentDetailWritePlatformService;

    @Mock
    private BusinessEventNotifierService businessEventNotifierService;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private LoanChargeRepository loanChargeRepository;

    @Mock
    private LoanAccountService loanAccountService;

    @Mock
    private LoanChargeValidator loanChargeValidator;

    @Mock
    private LoanLifecycleStateMachine defaultLoanLifecycleStateMachine;

    @Mock
    private LoanAccrualsProcessingService loanAccrualsProcessingService;

    @Mock
    private LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService;

    @Mock
    private Loan loan;

    @Mock
    private LoanCharge loanCharge;

    @Mock
    private LoanTransaction loanTransaction;

    @Mock
    private ExternalId externalId;

    @Mock
    private MonetaryCurrency monetaryCurrency;

    @Mock
    private ChargeRepositoryWrapper chargeRepository;

    @Mock
    private LoanTransactionRepository loanTransactionRepository;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    @Mock
    private LoanChargeReadPlatformService loanChargeReadPlatformService;

    @Mock
    private LoanUtilService loanUtilService;

    @Mock
    private ScheduleGeneratorDTO scheduleGeneratorDTO;

    // Additional dependencies required by the constructor
    @Mock
    private AccountTransfersWritePlatformService accountTransfersWritePlatformService;

    @Mock
    private JournalEntryWritePlatformService journalEntryWritePlatformService;

    @Mock
    private LoanWritePlatformService loanWritePlatformService;

    @Mock
    private AccountAssociationsReadPlatformService accountAssociationsReadPlatformService;

    @Mock
    private FromJsonHelper fromApiJsonHelper;

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Mock
    private LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory;

    @Mock
    private AccountTransferDetailRepository accountTransferDetailRepository;

    @Mock
    private LoanChargeAssembler loanChargeAssembler;

    @Mock
    private LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator;

    @Mock
    private LoanScheduleService loanScheduleService;

    @Mock
    private ReprocessLoanTransactionsService reprocessLoanTransactionsService;

    @Mock
    private LoanAdjustmentService loanAdjustmentService;

    @Mock
    private LoanAccountingBridgeMapper loanAccountingBridgeMapper;

    // Additional dependencies from parent class
    @Mock
    private LoanAccountDomainService loanAccountDomainService;

    @Mock
    private SavingsAccountWritePlatformService savingsAccountWritePlatformService;

    @Mock
    private PaymentTypeReadPlatformService paymentTypeReadPlatformService;

    @InjectMocks
    private CredXLoanChargeWritePlatformServiceImpl credXLoanChargeWritePlatformService;

    private MockedStatic<MoneyHelper> moneyHelperMock;

    @BeforeEach
    void setUp() {
        // Setup MoneyHelper static configuration
        moneyHelperMock = mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);

        // Setup common mocks
        when(loan.getCurrency()).thenReturn(monetaryCurrency);
        when(monetaryCurrency.getCode()).thenReturn(CURRENCY_CODE);
        when(monetaryCurrency.getDigitsAfterDecimal()).thenReturn(2);
        when(monetaryCurrency.getCurrencyInMultiplesOf()).thenReturn(1);

        // Mock the toData() method to return a proper CurrencyData
        CurrencyData currencyData = new CurrencyData(CURRENCY_CODE, 2, 1);
        when(monetaryCurrency.toData()).thenReturn(currencyData);
        when(loan.getId()).thenReturn(LOAN_ID);
        when(loanCharge.getId()).thenReturn(LOAN_CHARGE_ID);
        when(externalId.getValue()).thenReturn(EXTERNAL_ID_VALUE);
        when(loanCharge.getExternalId()).thenReturn(externalId);
        when(loanTransaction.getId()).thenReturn(3L);
        when(loanTransaction.getExternalId()).thenReturn(externalId);

        // Mock loan charge amount methods
        Money amountOutstanding = Money.of(currencyData, new BigDecimal("100.00"));
        Money zeroAmount = Money.of(currencyData, BigDecimal.ZERO);
        when(loanCharge.getAmountOutstanding(any(MonetaryCurrency.class))).thenReturn(amountOutstanding);
        when(loanCharge.getAmount(any(MonetaryCurrency.class))).thenReturn(amountOutstanding);
        when(loanCharge.getAmountPaid(any(MonetaryCurrency.class))).thenReturn(zeroAmount);
        when(loanCharge.getAmountWaived(any(MonetaryCurrency.class))).thenReturn(zeroAmount);

        // Mock installment charge for installment fee tests
        LoanInstallmentCharge installmentCharge = mock(LoanInstallmentCharge.class);
        when(installmentCharge.waive(any(MonetaryCurrency.class))).thenReturn(amountOutstanding);
        when(loanCharge.getInstallmentLoanCharge(anyInt())).thenReturn(installmentCharge);
        when(loanCharge.getUnpaidInstallmentLoanCharge()).thenReturn(installmentCharge);

        // Mock repayment installment
        LoanRepaymentScheduleInstallment repaymentInstallment = mock(LoanRepaymentScheduleInstallment.class);
        when(repaymentInstallment.getInstallmentNumber()).thenReturn(1);
        when(installmentCharge.getRepaymentInstallment()).thenReturn(repaymentInstallment);

        // Mock schedule generator DTO
        ScheduleGeneratorDTO scheduleGeneratorDTO = mock(ScheduleGeneratorDTO.class);
        when(loanUtilService.buildScheduleGeneratorDTO(any(Loan.class), isNull(LocalDate.class))).thenReturn(scheduleGeneratorDTO);

        // Setup JsonCommand mocks
        when(jsonCommand.bigDecimalValueOfParameterNamed("amount")).thenReturn(ADJUSTMENT_AMOUNT);
        when(jsonCommand.locale()).thenReturn(LOCALE);
        when(jsonCommand.commandId()).thenReturn(1L);
        when(jsonCommand.stringValueOfParameterNamed("note")).thenReturn(NOTE_TEXT);
        when(jsonCommand.json()).thenReturn("{}");

        // Setup external ID factory
        when(externalIdFactory.createFromCommand(any(JsonCommand.class), anyString())).thenReturn(externalId);

        // Setup loan assembler
        when(loanAssembler.assembleFrom(LOAN_ID)).thenReturn(loan);

        // Setup loan charge repository to return our mock loan charge
        when(loanChargeRepository.findById(LOAN_CHARGE_ID)).thenReturn(Optional.of(loanCharge));

        // Setup loan util service
        when(loanUtilService.buildScheduleGeneratorDTO(any(Loan.class), any(LocalDate.class))).thenReturn(scheduleGeneratorDTO);

        // Setup loan charge read platform service
        when(loanChargeReadPlatformService.retrieveLoanChargesPaidBy(anyLong(), any(), any())).thenReturn(Collections.emptyList());

        // Non-down-payment installment so LoanRepaymentScheduleProcessingWrapper#reprocess (invoked by the waive
        // flow when the charge is not paid/partially paid) finds a "first normal period" instead of throwing
        // NoSuchElementException on an empty repayment schedule.
        final Money zeroMoneyForInstallment = Money.zero(monetaryCurrency);
        LoanRepaymentScheduleInstallment repaymentScheduleInstallmentForReprocess = mock(LoanRepaymentScheduleInstallment.class);
        when(repaymentScheduleInstallmentForReprocess.getInstallmentNumber()).thenReturn(1);
        when(repaymentScheduleInstallmentForReprocess.isDownPayment()).thenReturn(false);
        when(repaymentScheduleInstallmentForReprocess.getDueDate()).thenReturn(BUSINESS_DATE);
        when(repaymentScheduleInstallmentForReprocess.isRecalculatedInterestComponent()).thenReturn(false);
        when(repaymentScheduleInstallmentForReprocess.getInterestCharged(any(MonetaryCurrency.class))).thenReturn(zeroMoneyForInstallment);
        when(repaymentScheduleInstallmentForReprocess.getPrincipal(any(MonetaryCurrency.class))).thenReturn(zeroMoneyForInstallment);
        when(repaymentScheduleInstallmentForReprocess.getFeeChargesCharged(any(MonetaryCurrency.class)))
                .thenReturn(zeroMoneyForInstallment);
        when(repaymentScheduleInstallmentForReprocess.getPenaltyChargesCharged(any(MonetaryCurrency.class)))
                .thenReturn(zeroMoneyForInstallment);
        when(loan.getRepaymentScheduleInstallments()).thenReturn(List.of(repaymentScheduleInstallmentForReprocess));

        // LoanChargeSettlementUtils.closeIfFullySettled (invoked after every waive) needs a non-null summary;
        // default to "not repaid in full" so existing ACTIVE-status assertions in tests keep holding.
        LoanSummary loanSummary = mock(LoanSummary.class);
        when(loanSummary.isRepaidInFull(any(MonetaryCurrency.class))).thenReturn(false);
        when(loan.getSummary()).thenReturn(loanSummary);
    }

    @AfterEach
    void tearDown() {
        if (moneyHelperMock != null) {
            moneyHelperMock.close();
        }
    }

    @Test
    void testAdjustmentForLoanChargeValidatesRequest() {
        // Given
        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // When
            try {
                credXLoanChargeWritePlatformService.adjustmentForLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand);
            } catch (Exception e) {
                // Expected to fail due to missing mocks, but we can verify the validation was called
            }

            // Then
            verify(loanChargeApiJsonValidator).validateLoanChargeAdjustmentRequest(LOAN_ID, LOAN_CHARGE_ID, jsonCommand.json());
        }
    }

    @Test
    void testWaiveLoanChargeLoanInactiveThrowsException() {
        // Given
        when(loan.getStatus()).thenReturn(LoanStatus.CLOSED_OBLIGATIONS_MET);

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // When & Then
            assertThrows(LoanChargeCannotBeWaivedException.class,
                    () -> credXLoanChargeWritePlatformService.waiveLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand));
        }
    }

    @Test
    void testWaiveLoanChargeAlreadyWaivedThrowsException() {
        // Given
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loanCharge.isWaived()).thenReturn(true);

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // When & Then
            assertThrows(LoanChargeCannotBeWaivedException.class,
                    () -> credXLoanChargeWritePlatformService.waiveLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand));
        }
    }

    @Test
    void testWaiveLoanChargeAlreadyPaidThrowsException() {
        // Given
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loanCharge.isWaived()).thenReturn(false);
        when(loanCharge.isPaid()).thenReturn(true);

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // When & Then
            assertThrows(LoanChargeCannotBeWaivedException.class,
                    () -> credXLoanChargeWritePlatformService.waiveLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand));
        }
    }

    @Test
    void testWaiveLoanChargeWithInstallmentFeeValidatesInstallmentCharge() {
        // Given
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loanCharge.isWaived()).thenReturn(false);
        when(loanCharge.isPaid()).thenReturn(false);
        when(loanCharge.isInstalmentFee()).thenReturn(true);
        when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(false);
        when(loan.isInterestBearingAndInterestRecalculationEnabled()).thenReturn(false);
        when(loan.getDisbursementDate()).thenReturn(BUSINESS_DATE);
        when(loan.getOfficeId()).thenReturn(1L);
        when(loan.getClientId()).thenReturn(1L);
        when(loan.getGroupId()).thenReturn(null);
        when(loanTransactionRepository.saveAndFlush(any(LoanTransaction.class))).thenReturn(loanTransaction);
        when(loanRepositoryWrapper.save(any(Loan.class))).thenReturn(loan);
        when(loan.findExistingTransactionIds()).thenReturn(Collections.emptyList());
        when(loan.findExistingReversedTransactionIds()).thenReturn(Collections.emptyList());

        // Mock installment charge
        LoanInstallmentCharge installmentCharge = mock(LoanInstallmentCharge.class);
        when(installmentCharge.isWaived()).thenReturn(false);
        when(installmentCharge.isPaid()).thenReturn(false);
        when(installmentCharge.getRepaymentInstallment()).thenReturn(mock(LoanRepaymentScheduleInstallment.class));
        when(loanCharge.getUnpaidInstallmentLoanCharge()).thenReturn(installmentCharge);

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // When
            try {
                credXLoanChargeWritePlatformService.waiveLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand);
            } catch (Exception e) {
                // Expected to fail due to missing mocks, but we can verify the installment charge was accessed
            }

            // Then
            verify(loanCharge, times(2)).getInstallmentLoanCharge(1);
        }
    }

    @Test
    void testWaiveLoanChargeSuccess() {
        // Given
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loanCharge.isWaived()).thenReturn(false);
        when(loanCharge.isPaid()).thenReturn(false);
        when(loanCharge.isInstalmentFee()).thenReturn(false);
        when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(false);
        when(loan.isInterestBearingAndInterestRecalculationEnabled()).thenReturn(false);
        when(loan.getDisbursementDate()).thenReturn(BUSINESS_DATE);
        when(loan.getOfficeId()).thenReturn(1L);
        when(loan.getClientId()).thenReturn(1L);
        when(loan.getGroupId()).thenReturn(null);
        when(loanTransactionRepository.saveAndFlush(any(LoanTransaction.class))).thenReturn(loanTransaction);
        when(loanRepositoryWrapper.save(any(Loan.class))).thenReturn(loan);
        when(loan.findExistingTransactionIds()).thenReturn(Collections.emptyList());
        when(loan.findExistingReversedTransactionIds()).thenReturn(Collections.emptyList());
        when(loanCharge.getAmount()).thenReturn(ADJUSTMENT_AMOUNT);
        when(loanCharge.getAmountOutstanding()).thenReturn(ADJUSTMENT_AMOUNT);
        when(loanCharge.getLoan()).thenReturn(loan);
        when(loanCharge.getCharge()).thenReturn(mock(Charge.class));
        when(loanCharge.getDueDate()).thenReturn(BUSINESS_DATE);

        // Mock charge paid by data
        LoanChargePaidByData chargePaidByData = mock(LoanChargePaidByData.class);
        when(chargePaidByData.getTransactionId()).thenReturn(1L);
        when(loanChargeReadPlatformService.retrieveLoanChargesPaidBy(anyLong(), any(), any())).thenReturn(Collections.singletonList(chargePaidByData));

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // When
            CommandProcessingResult result = credXLoanChargeWritePlatformService.waiveLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand);

            // Then
            assertNotNull(result);
            assertEquals(LOAN_CHARGE_ID, result.getResourceId());
            assertEquals(EXTERNAL_ID_VALUE, result.getResourceExternalId().getValue());

            // Verify key interactions
            verify(loanAssembler).assembleFrom(LOAN_ID);
            verify(loanChargeRepository).findById(LOAN_CHARGE_ID);
            verify(loanChargeValidator).validateLoanIsNotClosed(loan, loanCharge);
            verify(loanTransactionRepository).saveAndFlush(any(LoanTransaction.class));
            verify(loanRepositoryWrapper).save(loan);
            verify(businessEventNotifierService, times(2)).notifyPostBusinessEvent(any());
        }
    }

    @Test
    void testWaiveLoanChargeWithInstallmentFeeSuccess() {
        // Given
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loanCharge.isWaived()).thenReturn(false);
        when(loanCharge.isPaid()).thenReturn(false);
        when(loanCharge.isInstalmentFee()).thenReturn(true);
        when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(false);
        when(loan.isInterestBearingAndInterestRecalculationEnabled()).thenReturn(false);
        when(loan.getDisbursementDate()).thenReturn(BUSINESS_DATE);
        when(loan.getOfficeId()).thenReturn(1L);
        when(loan.getClientId()).thenReturn(1L);
        when(loan.getGroupId()).thenReturn(null);
        when(loanTransactionRepository.saveAndFlush(any(LoanTransaction.class))).thenReturn(loanTransaction);
        when(loanRepositoryWrapper.save(any(Loan.class))).thenReturn(loan);
        when(loan.findExistingTransactionIds()).thenReturn(Collections.emptyList());
        when(loan.findExistingReversedTransactionIds()).thenReturn(Collections.emptyList());
        when(loanCharge.getAmount()).thenReturn(ADJUSTMENT_AMOUNT);
        when(loanCharge.getAmountOutstanding()).thenReturn(ADJUSTMENT_AMOUNT);
        when(loanCharge.getLoan()).thenReturn(loan);
        when(loanCharge.getCharge()).thenReturn(mock(Charge.class));
        when(loanCharge.getDueDate()).thenReturn(BUSINESS_DATE);

        // Mock installment charge
        LoanInstallmentCharge installmentCharge = mock(LoanInstallmentCharge.class);
        when(installmentCharge.isWaived()).thenReturn(false);
        when(installmentCharge.isPaid()).thenReturn(false);
        when(installmentCharge.getRepaymentInstallment()).thenReturn(mock(LoanRepaymentScheduleInstallment.class));
        when(loanCharge.getUnpaidInstallmentLoanCharge()).thenReturn(installmentCharge);

        // Mock charge paid by data
        LoanChargePaidByData chargePaidByData = mock(LoanChargePaidByData.class);
        when(chargePaidByData.getTransactionId()).thenReturn(1L);
        when(loanChargeReadPlatformService.retrieveLoanChargesPaidBy(anyLong(), any(), any())).thenReturn(Collections.singletonList(chargePaidByData));

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // When
            CommandProcessingResult result = credXLoanChargeWritePlatformService.waiveLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand);

            // Then
            assertNotNull(result);
            assertEquals(LOAN_CHARGE_ID, result.getResourceId());
            assertEquals(EXTERNAL_ID_VALUE, result.getResourceExternalId().getValue());

            // Verify installment charge was accessed
            verify(loanCharge, times(2)).getInstallmentLoanCharge(1);
            verify(loanChargeApiJsonValidator).validateInstallmentChargeTransaction(jsonCommand.json());
        }
    }

    @Test
    void testWaiveLoanChargeWithAccrualAccountingSuccess() {
        // Given
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loanCharge.isWaived()).thenReturn(false);
        when(loanCharge.isPaid()).thenReturn(false);
        when(loanCharge.isInstalmentFee()).thenReturn(false);
        when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(true);
        when(loan.isInterestBearingAndInterestRecalculationEnabled()).thenReturn(false);
        when(loan.getDisbursementDate()).thenReturn(BUSINESS_DATE);
        when(loan.getOfficeId()).thenReturn(1L);
        when(loan.getClientId()).thenReturn(1L);
        when(loan.getGroupId()).thenReturn(null);
        when(loanTransactionRepository.saveAndFlush(any(LoanTransaction.class))).thenReturn(loanTransaction);
        when(loanRepositoryWrapper.save(any(Loan.class))).thenReturn(loan);
        when(loan.findExistingTransactionIds()).thenReturn(Collections.emptyList());
        when(loan.findExistingReversedTransactionIds()).thenReturn(Collections.emptyList());
        when(loanCharge.getAmount()).thenReturn(ADJUSTMENT_AMOUNT);
        when(loanCharge.getAmountOutstanding()).thenReturn(ADJUSTMENT_AMOUNT);
        when(loanCharge.getLoan()).thenReturn(loan);
        when(loanCharge.getCharge()).thenReturn(mock(Charge.class));
        when(loanCharge.getDueDate()).thenReturn(BUSINESS_DATE);

        // Mock charge paid by data for accrual
        LoanChargePaidByData chargePaidByData = mock(LoanChargePaidByData.class);
        when(chargePaidByData.getTransactionId()).thenReturn(1L);
        when(loanChargeReadPlatformService.retrieveLoanChargesPaidBy(LOAN_CHARGE_ID, LoanTransactionType.ACCRUAL, null)).thenReturn(Collections.singletonList(chargePaidByData));

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // When
            CommandProcessingResult result = credXLoanChargeWritePlatformService.waiveLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand);

            // Then
            assertNotNull(result);
            assertEquals(LOAN_CHARGE_ID, result.getResourceId());
            assertEquals(EXTERNAL_ID_VALUE, result.getResourceExternalId().getValue());

            // Verify accrual processing was called
            verify(loanChargeReadPlatformService).retrieveLoanChargesPaidBy(LOAN_CHARGE_ID, LoanTransactionType.ACCRUAL, null);
        }
    }

    /**
     * Reproduces the "Meltdown in advanced accounting...sum of all charges is not equal to the fee/penalty charge
     * for a transaction" bug: when periodic accrual accounting is enabled and a charge (e.g. a daily overdue/LPI
     * penalty charge, as waived by the backdated-settlement auto-waiver) is waived before it has been fully
     * accrued, the accrued amount is less than the amount being waived. The transaction's penaltyChargesPortion
     * (used by accounting) must then equal the "recognized" component only — NOT the full waived amount — and the
     * LoanChargePaidBy record attached to the transaction must match it exactly, otherwise
     * AccountingProcessorHelper#createJournalEntriesForLoanCharges throws the "Meltdown" exception because
     * sum(loanChargesPaid.amount) != transaction.penaltyChargesPortion.
     */
    @Test
    void testWaiveLoanChargePartiallyAccrued_transactionChargePortionMatchesLoanChargePaidByAmount() {
        // Given: a penalty charge with 100.00 outstanding, but only 40.00 of it has actually been accrued so far.
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loanCharge.isWaived()).thenReturn(false);
        when(loanCharge.isPaid()).thenReturn(false);
        when(loanCharge.isInstalmentFee()).thenReturn(false);
        when(loanCharge.isPenaltyCharge()).thenReturn(true);
        when(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()).thenReturn(true);
        when(loan.isInterestBearingAndInterestRecalculationEnabled()).thenReturn(false);
        when(loan.getDisbursementDate()).thenReturn(BUSINESS_DATE);
        when(loan.getOfficeId()).thenReturn(1L);
        when(loan.getClientId()).thenReturn(1L);
        when(loan.getGroupId()).thenReturn(null);
        when(loanTransactionRepository.saveAndFlush(any(LoanTransaction.class))).thenReturn(loanTransaction);
        when(loanRepositoryWrapper.save(any(Loan.class))).thenReturn(loan);
        when(loan.findExistingTransactionIds()).thenReturn(Collections.emptyList());
        when(loan.findExistingReversedTransactionIds()).thenReturn(Collections.emptyList());
        when(loanCharge.getAmount()).thenReturn(ADJUSTMENT_AMOUNT);
        when(loanCharge.getAmountOutstanding()).thenReturn(ADJUSTMENT_AMOUNT);
        when(loanCharge.getLoan()).thenReturn(loan);
        when(loanCharge.getCharge()).thenReturn(mock(Charge.class));
        when(loanCharge.getDueDate()).thenReturn(BUSINESS_DATE);

        // The full outstanding (100.00) is what gets waived...
        Money fullOutstanding = Money.of(monetaryCurrency.toData(), ADJUSTMENT_AMOUNT);
        when(loanCharge.getAmountOutstanding(any(MonetaryCurrency.class))).thenReturn(fullOutstanding);
        when(loanCharge.getAmountWaived(any(MonetaryCurrency.class))).thenReturn(fullOutstanding);

        // ...but only 40.00 of it has been accrued so far (simulating a same-day auto-waive that races ahead of the
        // nightly periodic accrual job).
        LoanChargePaidByData chargePaidByData = mock(LoanChargePaidByData.class);
        when(chargePaidByData.getAmount()).thenReturn(new BigDecimal("40.00"));
        when(loanChargeReadPlatformService.retrieveLoanChargesPaidBy(LOAN_CHARGE_ID, LoanTransactionType.ACCRUAL, null))
                .thenReturn(Collections.singletonList(chargePaidByData));

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            // When
            credXLoanChargeWritePlatformService.waiveLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand);

            // Then: capture the real LoanTransaction that was built and would be handed to the accounting bridge.
            ArgumentCaptor<LoanTransaction> transactionCaptor = ArgumentCaptor.forClass(LoanTransaction.class);
            verify(loanTransactionRepository).saveAndFlush(transactionCaptor.capture());
            LoanTransaction waiveTransaction = transactionCaptor.getValue();

            BigDecimal penaltyChargesPortion = waiveTransaction.getPenaltyChargesPortion();
            BigDecimal sumOfLoanChargesPaid = waiveTransaction.getLoanChargesPaid().stream().map(LoanChargePaidBy::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Only 40.00 was actually recognized as accrued income, so that's what must flow through accounting -
            // NOT the full 100.00 waived amount.
            assertEquals(new BigDecimal("40.00"), penaltyChargesPortion);
            assertEquals(penaltyChargesPortion, sumOfLoanChargesPaid,
                    "sum(loanChargesPaid.amount) must equal transaction.penaltyChargesPortion, or accounting throws "
                            + "'Meltdown in advanced accounting...'");
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // reversePaidLoanCharge - regression coverage for the loan-2091 phantom-overpayment fix, plus the
    // Cloud Fifty One follow-up: do NOT full-history reprocess (that recasts unrelated repayments).
    // Savings refund stays conditional on a genuine post-reallocation overpayment.
    // ----------------------------------------------------------------------------------------------------------------

    private static final BigDecimal REVERSED_PENALTY = new BigDecimal("483.87");

    /** Common wiring for a reversible, already-paid overdue (LPI) penalty charge on loan {@link #LOAN_ID}. */
    private void givenReversablePaidPenalty() {
        final CurrencyData currencyData = new CurrencyData(CURRENCY_CODE, 2, 1);
        // Precompute OUTSIDE when(...) so the mocked-static MoneyHelper interaction does not interleave with stubbing.
        final Money paidAmount = Money.of(currencyData, REVERSED_PENALTY);
        when(loanCharge.getAmountPaid(any(MonetaryCurrency.class))).thenReturn(paidAmount);
        when(loanCharge.isActive()).thenReturn(true);
        when(loanCharge.isPenaltyCharge()).thenReturn(true);
        when(loanCharge.name()).thenReturn("Overdue Interest (LPI)");
        when(loanCharge.getLoanChargePaidBySet()).thenReturn(new HashSet<>());
        when(loan.getLoanTransactions()).thenReturn(Collections.emptyList());
        when(loan.getRepaymentScheduleInstallments()).thenReturn(Collections.emptyList());
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getOffice()).thenReturn(mock(Office.class));
        when(loanRepositoryWrapper.findOneWithNotFoundDetection(LOAN_ID)).thenReturn(loan);
        when(externalIdFactory.create()).thenReturn(externalId);
    }

    @Test
    void reversePaidLoanCharge_onPartiallyPaidLoan_doesNotReprocessOrRefundToSavings() {
        givenReversablePaidPenalty();
        // Partially-paid loan: the reprocess re-applies the freed penalty to principal, so it is never overpaid.
        when(loan.getTotalOverpaid()).thenReturn(BigDecimal.ZERO);

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            credXLoanChargeWritePlatformService.reversePaidLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand);

            // Must not replay the full history — that is what recast Cloud Fifty One loan 1.
            verify(reprocessLoanTransactionsService, never()).reprocessTransactions(loan);
            // Nothing became a genuine overpayment, so NO money is credited back to savings (prevents the
            // double-credit).
            verify(savingsAccountWritePlatformService, never()).deposit(anyLong(), any(JsonCommand.class));
        }
    }

    @Test
    void reversePaidLoanCharge_refundsOnlyTheGenuinePostReprocessOverpaymentNotTheFullReversedAmount() {
        givenReversablePaidPenalty();
        // Reverse the 483.87 penalty on a loan that ends up only 200.00 overpaid after the reprocess (the other 283.87
        // was re-applied to principal). The refund to savings must be exactly the 200.00 genuine overpayment - NOT the
        // full 483.87 reversed amount (that unconditional refund was the historical double-credit bug).
        // The `getTotalOverpaid() != null ? getTotalOverpaid() : ZERO` ternary calls the getter twice per read, so stub
        // four sequential values: (before: 0, 0) then (after: 200.00, 200.00).
        final BigDecimal genuineOverpayment = new BigDecimal("200.00");
        when(loan.getTotalOverpaid()).thenReturn(BigDecimal.ZERO, BigDecimal.ZERO, genuineOverpayment, genuineOverpayment);

        final PortfolioAccountData linkedSavings = mock(PortfolioAccountData.class);
        when(linkedSavings.getId()).thenReturn(3393L);
        when(linkedSavings.getAccountNo()).thenReturn("000003393");
        when(accountAssociationsReadPlatformService.retriveLoanLinkedAssociation(LOAN_ID)).thenReturn(linkedSavings);

        final CommandProcessingResult depositResult = mock(CommandProcessingResult.class);
        when(depositResult.getResourceId()).thenReturn(999L);
        when(savingsAccountWritePlatformService.deposit(eq(3393L), any(JsonCommand.class))).thenReturn(depositResult);
        when(paymentTypeReadPlatformService.retrieveAllPaymentTypesWithCode()).thenReturn(Collections.emptyList());
        // toJson(Object) - disambiguate from the toJson(JsonElement) overload with a Map matcher - so the built deposit
        // command carries real JSON we can assert the amount on.
        when(fromApiJsonHelper.toJson(any(Map.class)))
                .thenAnswer(invocation -> new com.google.gson.Gson().toJson((Object) invocation.getArgument(0)));
        when(fromApiJsonHelper.parse(anyString()))
                .thenAnswer(invocation -> com.google.gson.JsonParser.parseString(invocation.getArgument(0)));

        try (MockedStatic<DateUtils> mockedDateUtils = mockStatic(DateUtils.class)) {
            mockedDateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(BUSINESS_DATE);

            credXLoanChargeWritePlatformService.reversePaidLoanCharge(LOAN_ID, LOAN_CHARGE_ID, jsonCommand);

            verify(reprocessLoanTransactionsService, never()).reprocessTransactions(loan);
            final ArgumentCaptor<JsonCommand> depositCommand = ArgumentCaptor.forClass(JsonCommand.class);
            verify(savingsAccountWritePlatformService).deposit(eq(3393L), depositCommand.capture());
            final String depositJson = depositCommand.getValue().json();
            assertTrue(depositJson.contains("200"), "savings deposit must be for the genuine overpayment amount 200.00");
            assertFalse(depositJson.contains("483.87"), "savings deposit must NOT refund the full reversed amount 483.87");
        }
    }

}
