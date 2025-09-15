package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.account.data.CustomAccountTransferDTO;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.cob.service.LoanAccountLockService;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanDisbursalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanDisbursalTransactionBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.teller.data.CashierTransactionDataValidator;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.data.PortfolioAccountData;
import org.apache.fineract.portfolio.account.domain.AccountAssociationsRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferType;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarRepository;
import org.apache.fineract.portfolio.collectionsheet.command.CollectionSheetBulkDisbursalCommand;
import org.apache.fineract.portfolio.collectionsheet.command.SingleDisbursalCommand;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.exception.LoanTransactionNotFoundException;
import org.apache.fineract.portfolio.loanaccount.guarantor.service.GuarantorDomainService;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleHistoryWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanApplicationValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanOfficerValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanUpdateCommandFromApiJsonDeserializer;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualTransactionBusinessEventService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementService;
import org.apache.fineract.portfolio.loanaccount.service.LoanDownPaymentHandlerService;
import org.apache.fineract.portfolio.loanaccount.service.LoanJournalEntryPoster;
import org.apache.fineract.portfolio.loanaccount.service.LoanOfficerService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.loanaccount.service.adjustment.LoanAdjustmentService;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.exception.LinkedAccountRequiredException;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecks;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecksRepository;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.service.RepaymentWithPostDatedChecksAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Primary
public class CustomLoanWritePlatformServiceJpaRepositoryImpl extends LoanWritePlatformServiceJpaRepositoryImpl {


    private static final Logger log = LoggerFactory.getLogger(CustomLoanWritePlatformServiceJpaRepositoryImpl.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomLoanInterestCalculationService customLoanInterestCalculationService;

    @Autowired
    private SavingsAccountWritePlatformService savingsAccountWritePlatformService;

    public CustomLoanWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context, LoanTransactionValidator loanTransactionValidator, LoanUpdateCommandFromApiJsonDeserializer loanUpdateCommandFromApiJsonDeserializer, LoanRepositoryWrapper loanRepositoryWrapper, LoanAccountDomainService loanAccountDomainService, NoteRepository noteRepository, LoanTransactionRepository loanTransactionRepository, LoanTransactionRelationRepository loanTransactionRelationRepository, LoanAssembler loanAssembler, JournalEntryWritePlatformService journalEntryWritePlatformService, CalendarInstanceRepository calendarInstanceRepository, PaymentDetailWritePlatformService paymentDetailWritePlatformService, HolidayRepositoryWrapper holidayRepository, ConfigurationDomainService configurationDomainService, WorkingDaysRepositoryWrapper workingDaysRepository, AccountTransfersWritePlatformService accountTransfersWritePlatformService, AccountTransfersReadPlatformService accountTransfersReadPlatformService, AccountAssociationsReadPlatformService accountAssociationsReadPlatformService, LoanReadPlatformService loanReadPlatformService, FromJsonHelper fromApiJsonHelper, CalendarRepository calendarRepository, LoanScheduleHistoryWritePlatformService loanScheduleHistoryWritePlatformService, LoanApplicationValidator loanApplicationValidator, AccountAssociationsRepository accountAssociationRepository, AccountTransferDetailRepository accountTransferDetailRepository, BusinessEventNotifierService businessEventNotifierService, GuarantorDomainService guarantorDomainService, LoanUtilService loanUtilService, EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, CodeValueRepositoryWrapper codeValueRepository, CashierTransactionDataValidator cashierTransactionDataValidator, GLIMAccountInfoRepository glimRepository, LoanRepository loanRepository, RepaymentWithPostDatedChecksAssembler repaymentWithPostDatedChecksAssembler, PostDatedChecksRepository postDatedChecksRepository, LoanRepaymentScheduleInstallmentRepository loanRepaymentScheduleInstallmentRepository, LoanLifecycleStateMachine loanLifecycleStateMachine, LoanAccountLockService loanAccountLockService, ExternalIdFactory externalIdFactory, LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService, ErrorHandler errorHandler, LoanDownPaymentHandlerService loanDownPaymentHandlerService, LoanTransactionAssembler loanTransactionAssembler, LoanAccrualsProcessingService loanAccrualsProcessingService, LoanOfficerValidator loanOfficerValidator, LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator, LoanDisbursementService loanDisbursementService, LoanScheduleService loanScheduleService, LoanChargeValidator loanChargeValidator, LoanOfficerService loanOfficerService, ReprocessLoanTransactionsService reprocessLoanTransactionsService, LoanAccountService loanAccountService, LoanJournalEntryPoster journalEntryPoster, LoanAdjustmentService loanAdjustmentService, LoanAccountingBridgeMapper loanAccountingBridgeMapper, LoanMapper loanMapper, LoanTransactionProcessingService loanTransactionProcessingService, FineractProperties fineractProperties, JdbcTemplate jdbcTemplate) {
        super(context, loanTransactionValidator, loanUpdateCommandFromApiJsonDeserializer, loanRepositoryWrapper, loanAccountDomainService, noteRepository, loanTransactionRepository, loanTransactionRelationRepository, loanAssembler, journalEntryWritePlatformService, calendarInstanceRepository, paymentDetailWritePlatformService, holidayRepository, configurationDomainService, workingDaysRepository, accountTransfersWritePlatformService, accountTransfersReadPlatformService, accountAssociationsReadPlatformService, loanReadPlatformService, fromApiJsonHelper, calendarRepository, loanScheduleHistoryWritePlatformService, loanApplicationValidator, accountAssociationRepository, accountTransferDetailRepository, businessEventNotifierService, guarantorDomainService, loanUtilService, entityDatatableChecksWritePlatformService, codeValueRepository, cashierTransactionDataValidator, glimRepository, loanRepository, repaymentWithPostDatedChecksAssembler, postDatedChecksRepository, loanRepaymentScheduleInstallmentRepository, loanLifecycleStateMachine, loanAccountLockService, externalIdFactory, loanAccrualTransactionBusinessEventService, errorHandler, loanDownPaymentHandlerService, loanTransactionAssembler, loanAccrualsProcessingService, loanOfficerValidator, loanDownPaymentTransactionValidator, loanDisbursementService, loanScheduleService, loanChargeValidator, loanOfficerService, reprocessLoanTransactionsService, loanAccountService, journalEntryPoster, loanAdjustmentService, loanAccountingBridgeMapper, loanMapper, loanTransactionProcessingService, fineractProperties);
        this.jdbcTemplate = jdbcTemplate;
    }


    /**
     * Override the loan summary calculation for RECEIVABLE LOC loans to use the approved amount
     * (discounted amount) for the outstanding balance calculation while preserving original values.
     */
    private void updateLoanSummaryDerivedFieldsForReceivableLoc(Loan loan) {
        String locProductType = getLocProductType(loan.getId());

        if (LocProductType.RECEIVABLE.name().equals(locProductType)) {
            BigDecimal approvedAmount = loan.getApprovedPrincipal();
            if (approvedAmount == null) {
                approvedAmount = loan.getPrincipal().getAmount();
            }

            // Store original values before updating summary
            BigDecimal originalPrincipalDisbursed = loan.getSummary().getTotalPrincipalDisbursed();
            BigDecimal originalInterestCharged = loan.getSummary().getTotalInterestCharged();

            // Get the net disbursement amount
            BigDecimal netDisbursementAmount = loan.getNetDisbursalAmount();
            if (netDisbursementAmount == null) {
                // Calculate net disbursement amount: Approved Amount - Expected Interest
                BigDecimal expectedInterest = getExpectedInterestForReceivableLoan(loan.getId());
                if (expectedInterest != null) {
                    netDisbursementAmount = approvedAmount.subtract(expectedInterest);
                } else {
                    netDisbursementAmount = approvedAmount;
                }
            }

            // Create Money object for net disbursement amount
            Money netDisbursementAmountMoney = Money.of(loan.getCurrency(), netDisbursementAmount);

            // Update the summary using the net disbursement amount for principal calculations
            loan.getSummary().updateSummary(loan.getCurrency(), netDisbursementAmountMoney,
                    loan.getRepaymentScheduleInstallments(), new HashSet<>(loan.getCharges()));

            // Restore original values to preserve them in the summary using reflection
            try {
                if (originalPrincipalDisbursed != null) {
                    java.lang.reflect.Field totalPrincipalDisbursedField = loan.getSummary().getClass().getDeclaredField("totalPrincipalDisbursed");
                    totalPrincipalDisbursedField.setAccessible(true);
                    totalPrincipalDisbursedField.set(loan.getSummary(), originalPrincipalDisbursed);
                }
                if (originalInterestCharged != null) {
                    java.lang.reflect.Field totalInterestChargedField = loan.getSummary().getClass().getDeclaredField("totalInterestCharged");
                    totalInterestChargedField.setAccessible(true);
                    totalInterestChargedField.set(loan.getSummary(), originalInterestCharged);
                }
            } catch (Exception e) {
                log.warn("Failed to restore original values for RECEIVABLE LOC loan {}: {}", loan.getId(), e.getMessage());
            }

            try {
                java.lang.reflect.Field totalExpectedRepaymentField = loan.getSummary().getClass().getDeclaredField("totalExpectedRepayment");
                totalExpectedRepaymentField.setAccessible(true);
                totalExpectedRepaymentField.set(loan.getSummary(), approvedAmount);


                java.lang.reflect.Field totalOutstandingField = loan.getSummary().getClass().getDeclaredField("totalOutstanding");
                totalOutstandingField.setAccessible(true);
                totalOutstandingField.set(loan.getSummary(), approvedAmount);

            } catch (Exception e) {
                log.warn("Failed to set total fields for RECEIVABLE LOC loan {}: {}", loan.getId(), e.getMessage());
            }

            loan.updateLoanOutstandingBalances();

        } else {
            loan.updateLoanSummaryDerivedFields();
        }
    }


    @Transactional
    @Override
    public CommandProcessingResult disburseLoan(final Long loanId, final JsonCommand command, Boolean isAccountTransfer,
                                                Boolean isWithoutAutoPayment) {

        // Check if this is a RECEIVABLE LOC loan
        String locProductType = getLocProductType(loanId);
        if (LocProductType.RECEIVABLE.name().equals(locProductType)) {
            log.info("Using custom disbursement logic for RECEIVABLE LOC loan {} - skipping validation", loanId);

            // For RECEIVABLE LOC loans, skip validation and apply custom logic
            try {
                // Get the loan
                Loan loan = loanAssembler.assembleFrom(loanId);

                // Apply our custom logic
                BigDecimal approvedAmount = loan.getApprovedPrincipal();
                if (approvedAmount == null) {
                    log.warn("Approved principal not set for RECEIVABLE loan {}, using current principal: {}",
                            loanId, loan.getPrincipal().getAmount());
                    approvedAmount = loan.getPrincipal().getAmount();
                }

                BigDecimal netDisbursementAmount = loan.getNetDisbursalAmount();
                if (netDisbursementAmount == null) {
                    BigDecimal expectedInterest = calculateExpectedInterest(loanId, approvedAmount);
                    if (expectedInterest != null) {
                        netDisbursementAmount = approvedAmount.subtract(expectedInterest);
                    } else {
                        netDisbursementAmount = approvedAmount;
                    }
                }

                // Set the loan's principal to the net disbursement amount
                loan.getLoanRepaymentScheduleDetail().setPrincipal(netDisbursementAmount);

                log.info("Set principal to net disbursement amount {} for RECEIVABLE LOC loan {}",
                        netDisbursementAmount, loanId);

                // Continue with the rest of the disbursement logic
                return processReceivableLocDisbursement(loanId, command, isAccountTransfer, isWithoutAutoPayment);

            } catch (Exception e) {
                log.error("Failed to handle custom disbursement for RECEIVABLE LOC loan {}: {}", loanId, e.getMessage());
                throw e;
            }
        }

        // For non-RECEIVABLE loans, use the standard validation and disbursement
        loanTransactionValidator.validateDisbursement(command, isAccountTransfer, loanId);

        Loan loan = loanAssembler.assembleFrom(loanId);

        if (loan.loanProduct().isDisallowExpectedDisbursements()) {
            List<LoanDisbursementDetails> filteredList = loan.getDisbursementDetails().stream()
                    .filter(disbursementDetails -> disbursementDetails.actualDisbursementDate() == null).toList();
            // Check whether a new LoanDisbursementDetails is required
            if (filteredList.isEmpty()) {
                // create artificial 'tranche/expected disbursal' as current disburse code expects it for
                // multi-disbursal products
                final LocalDate artificialExpectedDate = loan.getExpectedDisbursedOnLocalDate();
                LoanDisbursementDetails disbursementDetail = new LoanDisbursementDetails(artificialExpectedDate, null,
                        loan.getDisbursedAmount(), null, false);
                disbursementDetail.updateLoan(loan);
                loan.getAllDisbursementDetails().add(disbursementDetail);
            }
        }

        final LocalDate nextPossibleRepaymentDate = loan.getNextPossibleRepaymentDateForRescheduling();
        final LocalDate rescheduledRepaymentDate = command.localDateValueOfParameterNamed("adjustRepaymentDate");
        final LocalDate actualDisbursementDate = command.localDateValueOfParameterNamed("actualDisbursementDate");
        if (!loan.isMultiDisburmentLoan()) {
            loan.setActualDisbursementDate(actualDisbursementDate);
        }

        // validate actual disbursement date against meeting date
        ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, null);

        businessEventNotifierService.notifyPreBusinessEvent(new LoanDisbursalBusinessEvent(loan));

        List<Long> existingTransactionIds = new ArrayList<>();
        List<Long> existingReversedTransactionIds = new ArrayList<>();

        final AppUser currentUser = getAppUserIfPresent();
        final Map<String, Object> changes = new LinkedHashMap<>();

        final PaymentDetail paymentDetail = this.paymentDetailWritePlatformService.createAndPersistPaymentDetail(command, changes);
        if (paymentDetail != null && paymentDetail.getPaymentType() != null && paymentDetail.getPaymentType().getIsCashPayment()) {
            BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed("transactionAmount");
            this.cashierTransactionDataValidator.validateOnLoanDisbursal(currentUser, loan.getCurrencyCode(), transactionAmount);
        }
        final boolean isPaymentTypeApplicableForDisbursementCharge = configurationDomainService
                .isPaymentTypeApplicableForDisbursementCharge();

        // Recalculate first repayment date based in actual disbursement date.
        updateLoanCounters(loan, actualDisbursementDate);
        Money amountBeforeAdjust = loan.getPrincipal();
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);

        if (loan.canDisburse()) {
            // Get netDisbursalAmount from disbursal screen field.
            final BigDecimal netDisbursalAmount = command
                    .bigDecimalValueOfParameterNamed(LoanApiConstants.disbursementNetDisbursalAmountParameterName);
            if (netDisbursalAmount != null) {
                log.info("Setting net disbursement amount from command parameter: {} for loan {}", netDisbursalAmount, loanId);
                loan.setNetDisbursalAmount(netDisbursalAmount);
            }

            // Check if this is a RECEIVABLE type LOC loan and adjust disbursement amount accordingly
            String locProductTypeForDisbursement = getLocProductType(loan.getId());
            Money disburseAmount = loanDisbursementService.adjustDisburseAmount(loan, command, actualDisbursementDate);

            // Store the original disbursement amount for LOC balance computation
            Money originalDisburseAmount = disburseAmount;


            if (LocProductType.RECEIVABLE.name().equals(locProductTypeForDisbursement)) {
                BigDecimal discountedAmount = disburseAmount.getAmount();

                // Calculate expected interest based on the discounted amount
                BigDecimal expectedInterest = calculateExpectedInterest(loan.getId(), discountedAmount);

                // Calculate net disbursed amount: Discounted Amount - Expected Interest
                BigDecimal netDisbursedAmount = discountedAmount.subtract(expectedInterest);

                if (expectedInterest != null) {
                    disburseAmount = Money.of(loan.getCurrency(), netDisbursedAmount);

                    // Update the loan's netDisbursalAmount field with the calculated net disbursed amount
                    loan.setNetDisbursalAmount(netDisbursedAmount);

                }
            }

            // For RECEIVABLE LOC loans, use the calculated disburseAmount directly
            // For other loans, use the copy as before
            Money amountToDisburse;
            if (LocProductType.RECEIVABLE.name().equals(locProductTypeForDisbursement)) {
                amountToDisburse = disburseAmount; // Use the calculated amount directly
            } else {
                amountToDisburse = disburseAmount.copy(); // Use copy for non-RECEIVABLE loans
            }
            boolean recalculateSchedule = amountBeforeAdjust.isNotEqualTo(loan.getPrincipal());
            final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

            if (loan.isTopup() && loan.getClientId() != null) {
                final BigDecimal loanOutstanding = loanApplicationValidator.validateTopupLoan(loan, actualDisbursementDate);

                amountToDisburse = disburseAmount.minus(loanOutstanding);
                // Ensure we use the correct amount for LOC loans after topup adjustment
                amountToDisburse = getCorrectDisbursementAmount(loan.getId(), amountToDisburse, locProductType);
                disburseLoanToLoan(loan, command, loanOutstanding);
            }

            LoanTransaction disbursementTransaction = null;
            if (isAccountTransfer) {

                final Set<LoanCharge> loanCharges = loan.getActiveCharges();
                BigDecimal chargeReducableFromDisbursement = BigDecimal.ZERO;
                for (final LoanCharge loanCharge : loanCharges) {
                    if (loanCharge.isDueAtDisbursement() && !loanCharge.getChargePaymentMode().isPaymentModeAccountTransfer()
                            && loanCharge.isChargePending()) {
                        chargeReducableFromDisbursement = chargeReducableFromDisbursement.add(loanCharge.amountOutstanding());
                    }

                }

                loan.getSummary().setTotalChargesPayableByPrincipalDeduction(chargeReducableFromDisbursement);

                // Ensure we use the correct amount for LOC loans in account transfer
                Money correctAmountForTransfer = getCorrectDisbursementAmount(loan.getId(), amountToDisburse, locProductType);
                disburseLoanToSavings(loan, command, correctAmountForTransfer, paymentDetail);
                existingTransactionIds.addAll(loan.findExistingTransactionIds());
                existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
            } else {
                existingTransactionIds.addAll(loan.findExistingTransactionIds());
                existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
                // Ensure we use the correct amount for LOC loans in disbursement transaction
                Money correctAmountForTransaction = getCorrectDisbursementAmount(loan.getId(), amountToDisburse, locProductType);
                disbursementTransaction = LoanTransaction.disbursement(loan, correctAmountForTransaction, paymentDetail, actualDisbursementDate,
                        txnExternalId, loan.getTotalOverpaidAsMoney());
                disbursementTransaction.updateLoan(loan);
                loan.addLoanTransaction(disbursementTransaction);
                loanTransactionRepository.saveAndFlush(disbursementTransaction);
            }
            if (loan.getRepaymentScheduleInstallments().isEmpty()) {
                /*
                 * If no schedule, generate one (applicable to non-tranche multi-disbursal loans)
                 */
                recalculateSchedule = true;
            }
            // For all LOC loans, use the approved_principal from database for LOC balance adjustments
            BigDecimal locBalanceAmount = loan.getApprovedPrincipal();

            // Compute and update Line of Credit balance upon disbursement
            computeLocBalance(loanId, locBalanceAmount, actualDisbursementDate);


            regenerateScheduleOnDisbursement(command, loan, recalculateSchedule, scheduleGeneratorDTO, nextPossibleRepaymentDate,
                    rescheduledRepaymentDate);

            boolean downPaymentEnabled = loan.repaymentScheduleDetail().isEnableDownPayment();
            if (loan.isInterestBearingAndInterestRecalculationEnabled() || downPaymentEnabled) {
                createAndSaveLoanScheduleArchive(loan, scheduleGeneratorDTO);
            }
            disburseLoan(command, isPaymentTypeApplicableForDisbursementCharge, paymentDetail, loan, currentUser, changes,
                    scheduleGeneratorDTO);
            // Ensure we use the correct amount for net disbursal amount adjustment
            Money correctAmountForNetDisbursal = getCorrectDisbursementAmount(loan.getId(), amountToDisburse, locProductType);
            loan.adjustNetDisbursalAmount(correctAmountForNetDisbursal.getAmount());

            loanAccrualsProcessingService.reprocessExistingAccruals(loan);

            LocalDate firstInstallmentDueDate = loan.fetchRepaymentScheduleInstallment(1).getDueDate();
            if (loan.isInterestBearingAndInterestRecalculationEnabled()
                    && (DateUtils.isBeforeBusinessDate(firstInstallmentDueDate) || loan.isDisbursementMissed())) {
                loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
            }

            if (loan.isAutoRepaymentForDownPaymentEnabled() && !isWithoutAutoPayment) {
                // updating linked savings account for auto down payment transaction for disbursement to savings account
                if (isAccountTransfer && loan.shouldCreateStandingInstructionAtDisbursement()) {
                    final PortfolioAccountData linkedSavingsAccountData = this.accountAssociationsReadPlatformService
                            .retriveLoanLinkedAssociation(loanId);
                    final SavingsAccount fromSavingsAccount = null;
                    final boolean isRegularTransaction = true;
                    final boolean isExceptionForBalanceCheck = false;

                    BigDecimal disbursedAmountPercentageForDownPayment = loan.getLoanRepaymentScheduleDetail()
                            .getDisbursedAmountPercentageForDownPayment();
                    // Ensure we use the correct amount for down payment calculation
                    Money correctAmountForDownPayment = getCorrectDisbursementAmount(loan.getId(), amountToDisburse, locProductType);
                    Money downPaymentMoney = Money.of(loan.getCurrency(),
                            MathUtil.percentageOf(correctAmountForDownPayment.getAmount(), disbursedAmountPercentageForDownPayment, 19));
                    if (loan.getLoanProduct().getInstallmentAmountInMultiplesOf() != null) {
                        downPaymentMoney = Money.roundToMultiplesOf(downPaymentMoney,
                                loan.getLoanProduct().getInstallmentAmountInMultiplesOf());
                    }
                    final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(actualDisbursementDate,
                            downPaymentMoney.getAmount(), PortfolioAccountType.SAVINGS, PortfolioAccountType.LOAN,
                            linkedSavingsAccountData.getId(), loan.getId(),
                            "To loan " + loan.getAccountNumber() + " from savings " + linkedSavingsAccountData.getAccountNo()
                                    + " Standing instruction transfer ",
                            locale, fmt, null, null, LoanTransactionType.DOWN_PAYMENT.getValue(), null, null,
                            AccountTransferType.LOAN_DOWN_PAYMENT.getValue(), null, null, ExternalId.empty(), null, null,
                            fromSavingsAccount, isRegularTransaction, isExceptionForBalanceCheck);
                    this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
                } else {
                    loanDownPaymentHandlerService.handleDownPayment(scheduleGeneratorDTO, command, disbursementTransaction, loan);
                    loanAccrualsProcessingService.reprocessExistingAccruals(loan);
                    if (loan.isInterestBearingAndInterestRecalculationEnabled()) {
                        loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
                    }
                }
            }
        }
        if (!changes.isEmpty()) {
            loan.updateLoanScheduleDependentDerivedFields();
            loan = saveAndFlushLoanWithDataIntegrityViolationChecks(loan);

            createNote(loan, command, changes);
            // auto create standing instruction
            createStandingInstruction(loan);
        }

        final Set<LoanCharge> loanCharges = loan.getActiveCharges();
        final Map<Long, BigDecimal> disBuLoanCharges = new HashMap<>();
        final Map<Long, BigDecimal> disBuLoanChargesTax = new HashMap<>();
        for (final LoanCharge loanCharge : loanCharges) {
            if (loanCharge.isDueAtDisbursement() && loanCharge.getChargePaymentMode().isPaymentModeAccountTransfer()
                    && loanCharge.isChargePending()) {
                if (loanCharge.hasTax() && loanCharge.getTaxAmount() != null && loanCharge.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                    disBuLoanChargesTax.put(loanCharge.getId(), loanCharge.getTaxAmount());
                    disBuLoanCharges.put(loanCharge.getId(), loanCharge.amountOutstanding().subtract(loanCharge.getTaxAmount()));
                } else {
                    disBuLoanCharges.put(loanCharge.getId(), loanCharge.amountOutstanding());
                }
            }
        }

        // TODO: The right thing to do is have a flag that dictates whether to tax goes to a different transaction or
        // stays on the same transaction
        for (final Map.Entry<Long, BigDecimal> entrySet : disBuLoanCharges.entrySet()) {
            final PortfolioAccountData savingAccountData = this.accountAssociationsReadPlatformService.retriveLoanLinkedAssociation(loanId);
            final SavingsAccount fromSavingsAccount = null;
            final boolean isRegularTransaction = true;
            final boolean isExceptionForBalanceCheck = false;
            final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(actualDisbursementDate, entrySet.getValue(),
                    PortfolioAccountType.SAVINGS, PortfolioAccountType.LOAN, savingAccountData.getId(), loanId, "Loan Charge Payment",
                    locale, fmt, null, null, LoanTransactionType.REPAYMENT_AT_DISBURSEMENT.getValue(), entrySet.getKey(), null,
                    AccountTransferType.CHARGE_PAYMENT.getValue(), null, null, ExternalId.empty(), null, null, fromSavingsAccount,
                    isRegularTransaction, isExceptionForBalanceCheck);
            this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);

            if (!disBuLoanChargesTax.isEmpty() && disBuLoanChargesTax.containsKey(entrySet.getKey())) {
                BigDecimal taxAmount = disBuLoanChargesTax.get(entrySet.getKey());
                final AccountTransferDTO accountTransferTaxDTO = new AccountTransferDTO(actualDisbursementDate, taxAmount,
                        PortfolioAccountType.SAVINGS, PortfolioAccountType.LOAN, savingAccountData.getId(), loanId, "Tax Payment", locale,
                        fmt, null, null, LoanTransactionType.VAT_DEDUCTION_AT_DISBURSEMENT.getValue(), entrySet.getKey(), null,
                        AccountTransferType.CHARGE_PAYMENT.getValue(), null, null, ExternalId.empty(), null, null, fromSavingsAccount,
                        isRegularTransaction, isExceptionForBalanceCheck);
                this.accountTransfersWritePlatformService.transferFunds(accountTransferTaxDTO);
            }
        }
        updateRecurringCalendarDatesForInterestRecalculation(loan);
        loanAccrualsProcessingService.processAccrualsOnInterestRecalculation(loan, loan.isInterestBearingAndInterestRecalculationEnabled(),
                false);
        this.loanAccountDomainService.setLoanDelinquencyTag(loan, DateUtils.getBusinessLocalDate());

        // Post Dated Checks
        if (command.parameterExists("postDatedChecks")) {
            // get repayment with post dates checks to update
            Set<PostDatedChecks> postDatedChecks = this.repaymentWithPostDatedChecksAssembler.fromParsedJson(command.json(), loan);
            updatePostDatedChecks(postDatedChecks);
        }

        businessEventNotifierService.notifyPostBusinessEvent(new LoanDisbursalBusinessEvent(loan));

        Long disbursalTransactionId = null;
        ExternalId disbursalTransactionExternalId = null;

        if (!isAccountTransfer) {
            // If accounting is not periodic accrual, the last transaction might be the accrual not the disbursement
            LoanTransaction disbursalTransaction = Lists.reverse(loan.getLoanTransactions()).stream()
                    .filter(e -> LoanTransactionType.DISBURSEMENT.equals(e.getTypeOf())).findFirst().orElseThrow();
            disbursalTransactionId = disbursalTransaction.getId();
            disbursalTransactionExternalId = disbursalTransaction.getExternalId();
            businessEventNotifierService.notifyPostBusinessEvent(new LoanDisbursalTransactionBusinessEvent(disbursalTransaction));
        }

        journalEntryPoster.postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds);
        loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);

        return new CommandProcessingResultBuilder()
                .withCommandId(command.commandId())
                .withEntityId(loan.getId())
                .withEntityExternalId(loan.getExternalId())
                .withSubEntityId(disbursalTransactionId)
                .withSubEntityExternalId(disbursalTransactionExternalId)
                .withOfficeId(loan.getOfficeId())
                .withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId())
                .withLoanId(loanId)
                .with(changes)
                .build();
    }

    /****
     * TODO Vishwas: Pair with Ashok and re-factor collection sheet code-base
     *
     * May of the changes made to disburseLoan aren't being made here, should refactor to reuse disburseLoan ASAP
     *****/
    @Transactional
    @Override
    public Map<String, Object> bulkLoanDisbursal(final JsonCommand command, final CollectionSheetBulkDisbursalCommand bulkDisbursalCommand,
                                                 Boolean isAccountTransfer) {
        final AppUser currentUser = getAppUserIfPresent();

        final SingleDisbursalCommand[] disbursalCommand = bulkDisbursalCommand.getDisburseTransactions();
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (disbursalCommand == null) {
            return changes;
        }

        final LocalDate nextPossibleRepaymentDate = null;
        final LocalDate rescheduledRepaymentDate = null;

        for (final SingleDisbursalCommand singleLoanDisbursalCommand : disbursalCommand) {
            Loan loan = this.loanAssembler.assembleFrom(singleLoanDisbursalCommand.getLoanId());
            final LocalDate actualDisbursementDate = command.localDateValueOfParameterNamed("actualDisbursementDate");

            // validate ActualDisbursement Date Against Expected Disbursement
            // Date
            LoanProduct loanProduct = loan.loanProduct();
            if (loanProduct.isSyncExpectedWithDisbursementDate()) {
                syncExpectedDateWithActualDisbursementDate(loan, actualDisbursementDate);
            }
            checkClientOrGroupActive(loan);
            businessEventNotifierService.notifyPreBusinessEvent(new LoanDisbursalBusinessEvent(loan));

            final List<Long> existingTransactionIds = new ArrayList<>();
            final List<Long> existingReversedTransactionIds = new ArrayList<>();

            final PaymentDetail paymentDetail = this.paymentDetailWritePlatformService.createAndPersistPaymentDetail(command, changes);

            // Bulk disbursement should happen on meeting date (mostly from
            // collection sheet).
            // FIXME: AA - this should be first meeting date based on
            // disbursement date and next available meeting dates
            // assuming repayment schedule won't regenerate because expected
            // disbursement and actual disbursement happens on same date
            loanDownPaymentTransactionValidator.validateAccountStatus(loan, LoanEvent.LOAN_DISBURSED);
            updateLoanCounters(loan, actualDisbursementDate);
            boolean canDisburse = loan.canDisburse();
            if (canDisburse) {
                Money amountBeforeAdjust = loan.getPrincipal();
                Money disburseAmount = loanDisbursementService.adjustDisburseAmount(loan, command, actualDisbursementDate);

                // Store the original disbursement amount for LOC balance computation
                Money originalDisburseAmount = disburseAmount;

                // Check if this is a RECEIVABLE type LOC loan and adjust disbursement amount accordingly
                String locProductType = getLocProductType(loan.getId());
                if (LocProductType.RECEIVABLE.name().equals(locProductType)) {
                    // For RECEIVABLE type, calculate net disbursed amount correctly
                    // The disburseAmount here is the discounted amount (approved_principal)
                    BigDecimal discountedAmount = disburseAmount.getAmount();

                    // Calculate expected interest based on the discounted amount
                    BigDecimal expectedInterest = calculateExpectedInterest(loan.getId(), discountedAmount);

                    // Calculate net disbursed amount: Discounted Amount - Expected Interest
                    BigDecimal netDisbursedAmount = discountedAmount.subtract(expectedInterest);

                    if (expectedInterest != null) {
                        disburseAmount = Money.of(loan.getCurrency(), netDisbursedAmount);

                        // Update the loan's netDisbursalAmount field with the calculated net disbursed amount
                        loan.setNetDisbursalAmount(netDisbursedAmount);

                    }
                }

                boolean recalculateSchedule = amountBeforeAdjust.isNotEqualTo(loan.getPrincipal());
                final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

                if (isAccountTransfer) {

                    final Set<LoanCharge> loanCharges = loan.getActiveCharges();
                    BigDecimal chargeReducableFromDisbursement = BigDecimal.ZERO;
                    for (final LoanCharge loanCharge : loanCharges) {
                        if (loanCharge.isDueAtDisbursement() && !loanCharge.getChargePaymentMode().isPaymentModeAccountTransfer()
                                && loanCharge.isChargePending()) {
                            chargeReducableFromDisbursement = chargeReducableFromDisbursement.add(loanCharge.amountOutstanding());
                        }

                    }

                    loan.getSummary().setTotalChargesPayableByPrincipalDeduction(chargeReducableFromDisbursement);

                    // Ensure we use the correct amount for LOC loans in bulk account transfer
                    Money correctAmountForBulkTransfer = getCorrectDisbursementAmount(loan.getId(), disburseAmount, locProductType);
                    disburseLoanToSavings(loan, command, correctAmountForBulkTransfer, paymentDetail);
                    existingTransactionIds.addAll(loan.findExistingTransactionIds());
                    existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());

                } else {
                    existingTransactionIds.addAll(loan.findExistingTransactionIds());
                    existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
                    // Ensure we use the correct amount for LOC loans in bulk disbursement transaction
                    Money correctAmountForBulkTransaction = getCorrectDisbursementAmount(loan.getId(), disburseAmount, locProductType);
                    LoanTransaction disbursementTransaction = LoanTransaction.disbursement(loan, correctAmountForBulkTransaction, paymentDetail,
                            actualDisbursementDate, txnExternalId, loan.getTotalOverpaidAsMoney());
                    disbursementTransaction.updateLoan(loan);
                    loan.addLoanTransaction(disbursementTransaction);
                    businessEventNotifierService
                            .notifyPostBusinessEvent(new LoanDisbursalTransactionBusinessEvent(disbursementTransaction));
                }
                LocalDate recalculateFrom = null;
                final ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);
                regenerateScheduleOnDisbursement(command, loan, recalculateSchedule, scheduleGeneratorDTO, nextPossibleRepaymentDate,
                        rescheduledRepaymentDate);


                boolean downPaymentEnabled = loan.repaymentScheduleDetail().isEnableDownPayment();
                if (loan.isInterestBearingAndInterestRecalculationEnabled() || downPaymentEnabled) {
                    createAndSaveLoanScheduleArchive(loan, scheduleGeneratorDTO);
                }
                disburseLoan(command, configurationDomainService.isPaymentTypeApplicableForDisbursementCharge(), paymentDetail, loan,
                        currentUser, changes, scheduleGeneratorDTO);

                // Compute and update Line of Credit balance upon disbursement
                // For all LOC loans, use the approved_principal from database for LOC balance adjustments
                BigDecimal bulkLocBalanceAmount = loan.getApprovedPrincipal();
                log.info("Bulk LOC loan {} - Using approved_principal for LOC balance: {} (net disbursed: {})",
                        loan.getId(), bulkLocBalanceAmount, disburseAmount.getAmount());
                log.info("Calling computeLocBalance for bulk disbursal loan {} with amount {} on date {}", loan.getId(), bulkLocBalanceAmount, actualDisbursementDate);
                boolean bulkLocBalanceComputed = computeLocBalance(loan.getId(), bulkLocBalanceAmount, actualDisbursementDate);
                log.info("Bulk LOC balance computation result for loan {}: {}", loan.getId(), bulkLocBalanceComputed);


                loanAccrualsProcessingService.reprocessExistingAccruals(loan);

                LocalDate firstInstallmentDueDate = loan.fetchRepaymentScheduleInstallment(1).getDueDate();
                if (loan.isInterestBearingAndInterestRecalculationEnabled()
                        && (DateUtils.isBeforeBusinessDate(firstInstallmentDueDate) || loan.isDisbursementMissed())) {
                    loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
                }
            }
            if (!changes.isEmpty()) {
                createNote(loan, command, changes);
                loan = saveAndFlushLoanWithDataIntegrityViolationChecks(loan);
                journalEntryPoster.postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds);
                loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);
            }
            final Set<LoanCharge> loanCharges = loan.getActiveCharges();
            final Map<Long, BigDecimal> disBuLoanCharges = new HashMap<>();
            final Map<Long, BigDecimal> disBuLoanChargesTax = new HashMap<>();
            for (final LoanCharge loanCharge : loanCharges) {
                if (loanCharge.isDueAtDisbursement() && loanCharge.getChargePaymentMode().isPaymentModeAccountTransfer()
                        && loanCharge.isChargePending()) {
                    if (loanCharge.hasTax() && loanCharge.getTaxAmount() != null
                            && loanCharge.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                        disBuLoanChargesTax.put(loanCharge.getId(), loanCharge.getTaxAmount());
                        disBuLoanCharges.put(loanCharge.getId(), loanCharge.amountOutstanding().subtract(loanCharge.getTaxAmount()));
                    } else {
                        disBuLoanCharges.put(loanCharge.getId(), loanCharge.amountOutstanding());
                    }
                }
            }
            final Locale locale = command.extractLocale();
            final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);
            for (final Map.Entry<Long, BigDecimal> entrySet : disBuLoanCharges.entrySet()) {
                final PortfolioAccountData savingAccountData = this.accountAssociationsReadPlatformService
                        .retriveLoanLinkedAssociation(loan.getId());
                final SavingsAccount fromSavingsAccount = null;
                final boolean isRegularTransaction = true;
                final boolean isExceptionForBalanceCheck = false;
                final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(actualDisbursementDate, entrySet.getValue(),
                        PortfolioAccountType.SAVINGS, PortfolioAccountType.LOAN, savingAccountData.getId(), loan.getId(),
                        "Loan Charge Payment", locale, fmt, null, null, LoanTransactionType.REPAYMENT_AT_DISBURSEMENT.getValue(),
                        entrySet.getKey(), null, AccountTransferType.CHARGE_PAYMENT.getValue(), null, null, ExternalId.empty(), null, null,
                        fromSavingsAccount, isRegularTransaction, isExceptionForBalanceCheck);
                this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);

                if (!disBuLoanChargesTax.isEmpty() && disBuLoanChargesTax.containsKey(entrySet.getKey())) {
                    BigDecimal taxAmount = disBuLoanChargesTax.get(entrySet.getKey());
                    final AccountTransferDTO accountTransferTaxDTO = new AccountTransferDTO(actualDisbursementDate, taxAmount,
                            PortfolioAccountType.SAVINGS, PortfolioAccountType.LOAN, savingAccountData.getId(), loan.getId(), "Tax Payment",
                            locale, fmt, null, null, LoanTransactionType.VAT_DEDUCTION_AT_DISBURSEMENT.getValue(), entrySet.getKey(), null,
                            AccountTransferType.CHARGE_PAYMENT.getValue(), null, null, ExternalId.empty(), null, null, fromSavingsAccount,
                            isRegularTransaction, isExceptionForBalanceCheck);
                    this.accountTransfersWritePlatformService.transferFunds(accountTransferTaxDTO);
                }
            }
            updateRecurringCalendarDatesForInterestRecalculation(loan);
            loanAccrualsProcessingService.processAccrualsOnInterestRecalculation(loan,
                    loan.isInterestBearingAndInterestRecalculationEnabled(), true);
            loanAccountDomainService.setLoanDelinquencyTag(loan, DateUtils.getBusinessLocalDate());
            businessEventNotifierService.notifyPostBusinessEvent(new LoanDisbursalBusinessEvent(loan));
        }

        return changes;
    }


    private void disburseLoanToSavings(final Loan loan, final JsonCommand command, final Money amount, final PaymentDetail paymentDetail) {
        final LocalDate transactionDate = command.localDateValueOfParameterNamed("actualDisbursementDate");
        final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);
        final PortfolioAccountData portfolioAccountData = this.accountAssociationsReadPlatformService
                .retriveLoanLinkedAssociation(loan.getId());
        if (portfolioAccountData == null) {
            final String errorMessage = "Disburse Loan with id:" + loan.getId() + " requires linked savings account for payment";
            throw new LinkedAccountRequiredException("loan.disburse.to.savings", errorMessage, loan.getId());
        }
        final SavingsAccount fromSavingsAccount = null;
        final boolean isExceptionForBalanceCheck = false;
        final boolean isRegularTransaction = true;
        final CustomAccountTransferDTO accountTransferDTO = new CustomAccountTransferDTO(transactionDate, amount.getAmount(), PortfolioAccountType.LOAN,
                PortfolioAccountType.SAVINGS, loan.getId(), portfolioAccountData.getId(), "Loan Disbursement", locale, fmt, paymentDetail,
                LoanTransactionType.DISBURSEMENT.getValue(), null, null, null, AccountTransferType.ACCOUNT_TRANSFER.getValue(), null, null,
                txnExternalId, loan, null, fromSavingsAccount, isRegularTransaction, isExceptionForBalanceCheck);
        accountTransferDTO.setNetLoanDisbursementAmount(loan.getNetDisbursalAmount());
        this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
    }


    @Override
    @Transactional
    public CommandProcessingResult forecloseLoan(Long loanId, JsonCommand command) {

        final Boolean isForcedClosure = command.booleanObjectValueOfParameterNamed("isForcedClosure");
        final Boolean isRestructured = command.booleanObjectValueOfParameterNamed("isRestructured");

        if (isForcedClosure != null && !(isForcedClosure instanceof Boolean)) {
            ApiParameterError error = ApiParameterError.parameterError(
                    "validation.msg.loan.isForcedClosure.invalid",
                    "The parameter isForcedClosure must be a boolean value",
                    "isForcedClosure", isForcedClosure
            );
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }

        if (isRestructured != null && !(isRestructured instanceof Boolean)) {
            ApiParameterError error = ApiParameterError.parameterError(
                    "validation.msg.loan.isRestructured.invalid",
                    "The parameter isRestructured must be a boolean value",
                    "isRestructured", isRestructured
            );
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }

        JsonElement parsedJson = command.parsedJson();
        if (parsedJson != null && parsedJson.isJsonObject()) {
            parsedJson.getAsJsonObject().remove("isForcedClosure");
            parsedJson.getAsJsonObject().remove("isRestructured");
        }

        JsonCommand cleanedCommand = JsonCommand.fromExistingCommand(command, parsedJson, null);

        // Get the loan BEFORE foreclosure to apply custom interest calculations
        Loan loan = loanAssembler.assembleFrom(loanId);

        // Apply custom interest calculations for LOC loans BEFORE foreclosure
        String productType = getLocProductType(loanId);

        // Get original values BEFORE foreclosure to preserve them
        BigDecimal originalInterestChargeDerived = null;
        BigDecimal originalPrincipalDisbursed = null;
        if (productType != null) {
            originalInterestChargeDerived = getInterestChargeDerived(loanId);
            originalPrincipalDisbursed = getApprovedPrincipalForLoan(loanId);
        }

        // First call the parent foreclosure method
        CommandProcessingResult result = super.forecloseLoan(loanId, cleanedCommand);

        // After standard foreclosure, process LOC-specific interest calculations
        if (productType != null && originalInterestChargeDerived != null) {
            BigDecimal annualInterestRate = getAnnualInterestRate(loanId);
            if (annualInterestRate != null) {
                // Calculate interest earned and refund for LOC loans using original values
                processLocLoanForeclosureInterest(loan, annualInterestRate, originalInterestChargeDerived, productType, originalPrincipalDisbursed);
            }
        }

        jdbcTemplate.update(
                "UPDATE m_loan SET is_forced_closure = ?, is_restructured = ? WHERE id = ?",
                Boolean.TRUE.equals(isForcedClosure),
                Boolean.TRUE.equals(isRestructured),
                loanId
        );

        // Ensure loan status is properly set to closed and foreclosed after custom processing
        jdbcTemplate.update(
                "UPDATE m_loan SET loan_status_id = ?, loan_sub_status_id = ? WHERE id = ?",
                600,  // CLOSED_OBLIGATIONS_MET
                100,  // FORECLOSED
                loanId
        );


        return result;
    }

    @Override
    @Transactional
    public CommandProcessingResult makeLoanRepayment(final LoanTransactionType repaymentTransactionType, final Long loanId,
                                                     final JsonCommand command, final boolean isRecoveryRepayment) {
        // Check if this is a RECEIVABLE LOC loan and handle it specially
        String productType = getLocProductType(loanId);
        if (LocProductType.RECEIVABLE.name().equals(productType)) {
            return processReceivableLocRepayment(repaymentTransactionType, loanId, command, isRecoveryRepayment);
        }

        // For non-RECEIVABLE loans, use the standard parent implementation
        CommandProcessingResult result = super.makeLoanRepayment(repaymentTransactionType, loanId, command, isRecoveryRepayment);

        // Adjust LOC balance on repayment (general logic for all LOC loans)
        if (result != null && result.hasChanges()) {
            // Get transaction amount from the command - try multiple parameter names
            BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed("transactionAmount");
            if (transactionAmount == null) {
                transactionAmount = command.bigDecimalValueOfParameterNamed("amount");
            }
            if (transactionAmount == null) {
                transactionAmount = command.bigDecimalValueOfParameterNamed("principal");
            }


            if (transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) > 0) {
                adjustLocBalanceOnRepayment(loanId, transactionAmount);
            }
        }

        return result;
    }

    /**
     * Processes repayment for RECEIVABLE LOC loans with custom logic
     * For RECEIVABLE LOC loans, the entire repayment should be treated as principal
     * since the interest was already "paid" upfront during disbursement
     */
    private CommandProcessingResult processReceivableLocRepayment(final LoanTransactionType repaymentTransactionType, final Long loanId,
                                                                  final JsonCommand command, final boolean isRecoveryRepayment) {
        try {
            // Get the loan
            Loan loan = loanAssembler.assembleFrom(loanId);

            // Get the repayment amount from the command
            BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed("transactionAmount");
            if (transactionAmount == null) {
                transactionAmount = command.bigDecimalValueOfParameterNamed("amount");
            }
            if (transactionAmount == null) {
                transactionAmount = command.bigDecimalValueOfParameterNamed("principal");
            }

            if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Invalid repayment amount");
            }


            // Create a custom repayment transaction with the correct breakdown
            LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");
            if (transactionDate == null) {
                transactionDate = LocalDate.now();
            }

            // Create the repayment transaction
            Money repaymentAmount = Money.of(loan.getCurrency(), transactionAmount);
            PaymentDetail paymentDetail = this.paymentDetailWritePlatformService.createAndPersistPaymentDetail(command, new LinkedHashMap<>());
            ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

            // For RECEIVABLE LOC loans, create a repayment transaction with correct breakdown
            LoanTransaction repaymentTransaction = LoanTransaction.repayment(loan.getOffice(), repaymentAmount, paymentDetail, transactionDate, txnExternalId);

            // Get the expected interest and net disbursement amount for RECEIVABLE LOC loans
            BigDecimal expectedInterest = getExpectedInterestForReceivableLoan(loanId);
            BigDecimal netDisbursementAmount = loan.getNetDisbursalAmount();

            if (expectedInterest == null || netDisbursementAmount == null) {
                // Fallback: treat entire amount as principal
                Money principalMoney = Money.of(loan.getCurrency(), transactionAmount);
                Money interestMoney = Money.zero(loan.getCurrency());
                Money feeChargesMoney = Money.zero(loan.getCurrency());
                Money penaltyChargesMoney = Money.zero(loan.getCurrency());
                repaymentTransaction.updateComponentsAndTotal(principalMoney, interestMoney, feeChargesMoney, penaltyChargesMoney);
            } else {
                // Set the breakdown: net disbursement amount as principal, expected interest as interest
                Money principalMoney = Money.of(loan.getCurrency(), netDisbursementAmount);
                Money interestMoney = Money.of(loan.getCurrency(), expectedInterest);
                Money feeChargesMoney = Money.zero(loan.getCurrency());
                Money penaltyChargesMoney = Money.zero(loan.getCurrency());

                // Update the transaction components
                repaymentTransaction.updateComponentsAndTotal(principalMoney, interestMoney, feeChargesMoney, penaltyChargesMoney);
            }

            // Add the transaction to the loan
            repaymentTransaction.updateLoan(loan);
            loan.addLoanTransaction(repaymentTransaction);

            // Save the transaction
            loanTransactionRepository.saveAndFlush(repaymentTransaction);

            BigDecimal newOutstanding = BigDecimal.ZERO;

            // Update the loan summary's total outstanding balance to zero
            loan.getSummary().updateTotalOutstanding(newOutstanding);

            // Also update the individual transaction's outstanding balance
            repaymentTransaction.updateOutstandingLoanBalance(newOutstanding);

            // Update all other transactions' outstanding balances to maintain consistency
            for (LoanTransaction transaction : loan.getLoanTransactions()) {
                if (transaction.isDisbursement()) {
                    // Disbursement should show the full amount as outstanding
                    transaction.updateOutstandingLoanBalance(transaction.getAmount(loan.getCurrency()).getAmount());
                } else if (transaction.isRepayment()) {
                    // Repayment should show zero outstanding
                    transaction.updateOutstandingLoanBalance(BigDecimal.ZERO);
                }
            }


            // Save the loan
            loan = saveAndFlushLoanWithDataIntegrityViolationChecks(loan);


            // Adjust LOC balance
            adjustLocBalanceOnRepayment(loanId, transactionAmount);

            return new CommandProcessingResultBuilder() //
                    .withCommandId(command.commandId()) //
                    .withLoanId(loanId) //
                    .withEntityId(repaymentTransaction.getId()) //
                    .withEntityExternalId(repaymentTransaction.getExternalId()) //
                    .withOfficeId(loan.getOfficeId()) //
                    .withClientId(loan.getClientId()) //
                    .withGroupId(loan.getGroupId()) //
                    .build();

        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to process RECEIVABLE LOC repayment", e);
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult closeAsRescheduled(final Long loanId, final JsonCommand command) {
        // Apply custom interest calculations for RECEIVABLE LOC loans before rescheduling
        String productType = getLocProductType(loanId);
        if (LocProductType.RECEIVABLE.name().equals(productType)) {
            Loan loan = loanAssembler.assembleFrom(loanId);
            BigDecimal expectedInterest = getExpectedInterestForReceivableLoan(loanId);
            if (expectedInterest != null) {
                // Apply the expected interest calculations to the loan before rescheduling
                customLoanInterestCalculationService.adjustInterestCalculationsForRescheduling(loan, productType, expectedInterest);
            }
        }

        // Call the parent implementation to handle the core rescheduling logic
        return super.closeAsRescheduled(loanId, command);
    }

    /**
     * Calculates the discounted amount based on the principal and advance percentage from the associated Line of Credit.
     * This method:
     * 1. Retrieves the loan's associated Line of Credit
     * 2. Gets the advance percentage from the LOC
     * 3. Calculates the discounted amount as principal * advance_percentage
     *
     * @param loanId    the loan ID
     * @param principal the principal amount to be discounted
     * @return the discounted amount (principal * advance_percentage), or null if no LOC association exists
     */
    public BigDecimal calculateDiscountedAmount(Long loanId, BigDecimal principal) {
        try {
            // Check if loan is associated with a line of credit
            String sql = "SELECT line_of_credit_id FROM m_loan WHERE id = ?";
            Long lineOfCreditId = jdbcTemplate.queryForObject(sql, Long.class, loanId);

            if (lineOfCreditId == null) {
                return null;
            }

            // Get the advance percentage from the line of credit
            String locSql = "SELECT advance_percentage FROM m_line_of_credit WHERE id = ?";
            BigDecimal advancePercentage = jdbcTemplate.queryForObject(locSql, BigDecimal.class, lineOfCreditId);

            if (advancePercentage == null) {
                return null;
            }


            BigDecimal advancePercentageDecimal = advancePercentage.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal discountedAmount = principal.multiply(advancePercentageDecimal);

            return discountedAmount;

        } catch (PlatformApiDataValidationException e) {
            throw new PlatformApiDataValidationException("error.msg.loc.discounted.amount.calculation.failed",
                    "Failed to calculate discounted amount from Line of Credit", "loanId", loanId, e);
        }
    }

    /**
     * Calculates the expected interest based on the annual interest rate, discounted amount, and tenure days from the associated Line of Credit.
     * This method:
     * 1. Retrieves the loan's associated Line of Credit
     * 2. Gets the annual interest rate and tenure days from the LOC
     * 3. Calculates the expected interest using the formula: Annual Interest Rate * Discounted Amount * (Tenure Days / 360)
     *
     * @param loanId           the loan ID
     * @param discountedAmount the discounted amount (principal * advance_percentage)
     * @return the expected interest amount, or null if no LOC association exists or required fields are missing
     */
    public BigDecimal calculateExpectedInterest(Long loanId, BigDecimal discountedAmount) {
        try {
            // Check if loan is associated with a line of credit
            String sql = "SELECT line_of_credit_id FROM m_loan WHERE id = ?";
            Long lineOfCreditId = jdbcTemplate.queryForObject(sql, Long.class, loanId);

            if (lineOfCreditId == null) {
                return null;
            }

            // Get the annual interest rate and tenure days from the line of credit
            String locSql = "SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?";
            Map<String, Object> locData = jdbcTemplate.queryForMap(locSql, lineOfCreditId);

            BigDecimal annualInterestRate = (BigDecimal) locData.get("annual_interest_rate");
            Integer tenorDays = (Integer) locData.get("tenor_days");

            if (annualInterestRate == null) {
                return null;
            }

            if (tenorDays == null) {
                return null;
            }

            if (discountedAmount == null || discountedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }

            // Calculate expected interest: Annual Interest Rate * Discounted Amount * (Tenure Days / 360)
            // annual_interest_rate is stored as a percentage (e.g., 12.00 for 12%), so divide by 100
            BigDecimal annualRateDecimal = annualInterestRate.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
            BigDecimal daysInYear = new BigDecimal("360");
            BigDecimal timeFactor = new BigDecimal(tenorDays).divide(daysInYear, 10, java.math.RoundingMode.HALF_UP);
            BigDecimal expectedInterest = annualRateDecimal.multiply(discountedAmount).multiply(timeFactor);

            // Round to 2 decimal places for currency precision
            expectedInterest = expectedInterest.setScale(2, java.math.RoundingMode.HALF_UP);


            return expectedInterest;

        } catch (PlatformApiDataValidationException e) {
            throw new PlatformApiDataValidationException("error.msg.loc.expected.interest.calculation.failed",
                    "Failed to calculate expected interest from Line of Credit", "loanId", loanId, e);
        }
    }

    /**
     * Gets the correct disbursement amount based on LOC type and loan configuration.
     * For RECEIVABLE LOC loans, this ensures the calculated net disbursed amount is used.
     * For other loans, it returns the provided amount as-is.
     *
     * @param loanId         the loan ID
     * @param originalAmount the original disbursement amount
     * @param locProductType the LOC product type
     * @return the correct disbursement amount to use
     */
    private Money getCorrectDisbursementAmount(Long loanId, Money originalAmount, String locProductType) {
        if (LocProductType.RECEIVABLE.name().equals(locProductType)) {
            // For RECEIVABLE LOC loans, we should use the calculated amount
            // The originalAmount should already be the calculated net disbursed amount
            return originalAmount;
        } else {
            // For non-RECEIVABLE loans, use the original amount
            return originalAmount;
        }
    }

    /**
     * Gets the product type of the Line of Credit associated with the loan.
     *
     * @param loanId the loan ID
     * @return the product type ("RECEIVABLE" or "PAYABLE"), or null if no LOC association exists
     */
    public String getLocProductType(Long loanId) {
        try {
            // Check if loan is associated with a line of credit
            String sql = "SELECT line_of_credit_id FROM m_loan WHERE id = ?";
            Long lineOfCreditId = jdbcTemplate.queryForObject(sql, Long.class, loanId);

            if (lineOfCreditId == null) {
                return null;
            }

            // Get the product type from the line of credit
            String locSql = "SELECT product_type FROM m_line_of_credit WHERE id = ?";

            return jdbcTemplate.queryForObject(locSql, String.class, lineOfCreditId);

        } catch (RuntimeException e) {
            return null;
        }
    }


    /**
     * Calculates the net disbursed amount by subtracting the expected interest from the discounted amount.
     * This method:
     * 1. Calculates the discounted amount (principal * advance_percentage)
     * 2. Calculates the expected interest (annual_rate * discounted_amount * (tenor_days / 360))
     * 3. Returns the net disbursed amount (discounted_amount - expected_interest)
     *
     * @param loanId    the loan ID
     * @param principal the principal amount
     * @return the net disbursed amount, or null if calculation cannot be performed
     */
    public BigDecimal calculateNetDisbursedAmount(Long loanId, BigDecimal principal) {
        try {
            // First calculate the discounted amount
            BigDecimal discountedAmount = calculateDiscountedAmount(loanId, principal);
            if (discountedAmount == null) {
                return null;
            }

            // Then calculate the expected interest
            BigDecimal expectedInterest = calculateExpectedInterest(loanId, discountedAmount);
            if (expectedInterest == null) {
                return null;
            }

            // Calculate net disbursed amount: Discounted Amount - Expected Interest
            return discountedAmount.subtract(expectedInterest);

        } catch (Exception e) {
            throw new PlatformApiDataValidationException("error.msg.loc.net.disbursed.amount.calculation.failed",
                    "Failed to calculate net disbursed amount from Line of Credit", "loanId", loanId, e);
        }
    }

    /**
     * Computes and updates Line of Credit balance upon loan disbursement.
     * This method:
     * 1. Automatically reduces the available LOC balance upon disbursement
     * 2. Increases the LOC consumed amount
     * 3. Creates and persists a LOC transaction history record with disbursement metadata
     *
     * @param loanId             the loan ID
     * @param disbursementAmount the disbursement amount (should be the approved_principal from the loan)
     * @param transactionDate    the disbursement date
     * @return true if LOC balance was successfully updated, false if no LOC association exists
     */
    @Transactional
    public boolean computeLocBalance(Long loanId, BigDecimal disbursementAmount, LocalDate transactionDate) {
        try {

            // Check if loan is associated with a line of credit
            String sql = "SELECT line_of_credit_id FROM m_loan WHERE id = ?";
            Long lineOfCreditId = jdbcTemplate.queryForObject(sql, Long.class, loanId);

            if (lineOfCreditId == null) {
                return false;
            }

            // Get the line of credit details
            String locSql = "SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?";
            Map<String, Object> locData = jdbcTemplate.queryForMap(locSql, lineOfCreditId);

            BigDecimal currentAvailableBalance = (BigDecimal) locData.get("available_balance");
            BigDecimal currentConsumedAmount = (BigDecimal) locData.get("consumed_amount");

            // Validate that there's sufficient available balance
            if (currentAvailableBalance.compareTo(disbursementAmount) < 0) {
                throw new PlatformApiDataValidationException("error.msg.loc.insufficient.balance",
                        "Insufficient line of credit balance for disbursement",
                        "disbursementAmount", disbursementAmount);
            }

            // Calculate new balances
            BigDecimal newAvailableBalance = currentAvailableBalance.subtract(disbursementAmount);
            BigDecimal newConsumedAmount = currentConsumedAmount.add(disbursementAmount);

            // Update LOC balances
            String updateSql = "UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ? WHERE id = ?";
            int rowsUpdated = jdbcTemplate.update(updateSql, newAvailableBalance, newConsumedAmount, lineOfCreditId);

            if (rowsUpdated == 0) {
                throw new PlatformApiDataValidationException("error.msg.loc.update.failed",
                        "Failed to update line of credit balances", "lineOfCreditId", lineOfCreditId);
            }

            // Create LOC transaction history record
            String insertSql = "INSERT INTO m_line_of_credit_transactions " +
                    "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                    "transaction_date, reference_number, description, created_on_utc, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            String referenceNumber = "LOAN_" + loanId + "_DISBURSEMENT";
            String description = String.format("Loan disbursement - LOC balance reduced by %s for loan %s",
                    disbursementAmount, loanId);


            jdbcTemplate.update(insertSql,
                    lineOfCreditId,
                    "DISBURSEMENT",
                    disbursementAmount,
                    currentAvailableBalance,
                    newAvailableBalance,
                    transactionDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toOffsetDateTime(),
                    referenceNumber,
                    description,
                    java.time.OffsetDateTime.now(),
                    null); // Set created_by to null for now to avoid UserDetails access

            return true;

        } catch (PlatformApiDataValidationException e) {
            throw new PlatformApiDataValidationException("error.msg.loc.computation.failed",
                    "Failed to compute line of credit balance", "loanId", loanId, e);
        }
    }

    /**
     * Adjusts LOC balance when a loan repayment is made
     * This is a general logic that applies to all LOC loans regardless of product type
     *
     * @param loanId          The ID of the loan
     * @param repaymentAmount The amount being repaid
     * @return true if adjustment was successful, false otherwise
     */
    @Transactional
    public boolean adjustLocBalanceOnRepayment(Long loanId, BigDecimal repaymentAmount) {
        try {
            // Validate repayment amount
            if (repaymentAmount == null || repaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            // Get LOC ID for the loan
            Long lineOfCreditId = jdbcTemplate.queryForObject(
                    "SELECT line_of_credit_id FROM m_loan WHERE id = ?",
                    Long.class,
                    loanId
            );

            if (lineOfCreditId == null) {
                return true; // No LOC to adjust
            }

            // Get current LOC balances
            Map<String, Object> locData = jdbcTemplate.queryForMap(
                    "SELECT consumed_amount, available_balance FROM m_line_of_credit WHERE id = ?",
                    lineOfCreditId
            );

            BigDecimal currentConsumedAmount = (BigDecimal) locData.get("consumed_amount");
            BigDecimal currentAvailableBalance = (BigDecimal) locData.get("available_balance");

            // Handle null values
            if (currentConsumedAmount == null) currentConsumedAmount = BigDecimal.ZERO;
            if (currentAvailableBalance == null) currentAvailableBalance = BigDecimal.ZERO;

            // Calculate new balances
            BigDecimal newConsumedAmount = currentConsumedAmount.subtract(repaymentAmount);
            BigDecimal newAvailableBalance = currentAvailableBalance.add(repaymentAmount);

            // Ensure consumed amount doesn't go below zero
            if (newConsumedAmount.compareTo(BigDecimal.ZERO) < 0) {
                newConsumedAmount = BigDecimal.ZERO;
                // Adjust available balance accordingly
                newAvailableBalance = currentAvailableBalance.add(currentConsumedAmount);
            }

            // Update LOC balances
            int updateCount = jdbcTemplate.update(
                    "UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_on_utc = NOW() WHERE id = ?",
                    newConsumedAmount,
                    newAvailableBalance,
                    lineOfCreditId
            );

            if (updateCount == 0) {
                return false;
            }

            // Create transaction record
            jdbcTemplate.update(
                    "INSERT INTO m_line_of_credit_transactions " +
                            "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                            "transaction_date, description, created_on_utc) " +
                            "VALUES (?, 'REPAYMENT', ?, ?, ?, NOW(), ?, NOW())",
                    lineOfCreditId,
                    repaymentAmount,
                    currentAvailableBalance, // balance_before
                    newAvailableBalance,     // balance_after
                    String.format("Loan repayment adjustment - Loan ID: %d, Amount: %s", loanId, repaymentAmount)
            );
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the expected interest amount for RECEIVABLE LOC loans
     * This is used to override Fineract's interest calculations for RECEIVABLE loans
     *
     * @param loanId The ID of the loan
     * @return The expected interest amount, or null if not applicable
     */
    public BigDecimal getExpectedInterestForReceivableLoan(Long loanId) {
        try {
            String productType = getLocProductType(loanId);

            if (!LocProductType.RECEIVABLE.name().equals(productType)) {
                return null; // Only applicable for RECEIVABLE loans
            }

            // Get the approved amount from the loan (this is the discounted amount for RECEIVABLE loans)
            BigDecimal approvedAmount = jdbcTemplate.queryForObject(
                    "SELECT approved_principal FROM m_loan WHERE id = ?",
                    BigDecimal.class,
                    loanId
            );

            if (approvedAmount == null) {
                return null;
            }

            // Calculate the expected interest using the approved amount (discounted amount)
            BigDecimal expectedInterest = calculateExpectedInterest(loanId, approvedAmount);

            if (expectedInterest != null) {
                log.debug("Calculated expected interest {} for RECEIVABLE loan {} (approved amount: {})",
                        expectedInterest, loanId, approvedAmount);
                return expectedInterest;
            }

            return null;

        } catch (Exception e) {
            log.error("Error calculating expected interest for RECEIVABLE loan {}: {}", loanId, e.getMessage());
            return null;
        }
    }

    /**
     * Override the parent's recalculateInterest method to include custom interest calculations for RECEIVABLE LOC loans
     */
    @Override
    @Transactional
    public Loan recalculateInterest(Loan loan) {
        // Call the parent implementation first
        Loan updatedLoan = super.recalculateInterest(loan);

        // Apply custom interest calculations for RECEIVABLE LOC loans after standard recalculation
        try {
            String productType = getLocProductType(updatedLoan.getId());
            if (LocProductType.RECEIVABLE.name().equals(productType)) {
                BigDecimal expectedInterest = getExpectedInterestForReceivableLoan(updatedLoan.getId());
                customLoanInterestCalculationService.adjustInterestCalculationsForReceivableLoan(updatedLoan, productType, expectedInterest);
            }
        } catch (Exception e) {
            log.warn("Failed to adjust interest calculations for RECEIVABLE LOC loan {} during recalculation: {}",
                    updatedLoan.getId(), e.getMessage());
        }

        return updatedLoan;
    }

    /**
     * Override the parent's disburseLoan method to apply custom logic after disbursement for RECEIVABLE LOC loans
     */
    @Override
    protected void disburseLoan(JsonCommand command, boolean isPaymentTypeApplicableForDisbursementCharge, PaymentDetail paymentDetail,
                                Loan loan, AppUser currentUser, Map<String, Object> changes, ScheduleGeneratorDTO scheduleGeneratorDTO) {

        // Call the parent method first to handle all the standard disbursement logic
        super.disburseLoan(command, isPaymentTypeApplicableForDisbursementCharge, paymentDetail, loan, currentUser, changes, scheduleGeneratorDTO);

        // After the parent method completes, apply final custom logic for LOC loans
        String locProductType = getLocProductType(loan.getId());
        if (locProductType != null && !LocProductType.RECEIVABLE.name().equals(locProductType)) {
            // Compute LOC balance for non-RECEIVABLE LOC loans only
            // (RECEIVABLE loans already have LOC balance computation in the main disbursal logic)
            BigDecimal locBalanceAmount = loan.getApprovedPrincipal();
            LocalDate actualDisbursementDate = command.localDateValueOfParameterNamed("actualDisbursementDate");
            if (actualDisbursementDate == null) {
                actualDisbursementDate = DateUtils.getBusinessLocalDate();
            }

            computeLocBalance(loan.getId(), locBalanceAmount, actualDisbursementDate);
        }

        if (LocProductType.RECEIVABLE.name().equals(locProductType)) {
            // Apply expected interest calculations
            BigDecimal expectedInterest = getExpectedInterestForReceivableLoan(loan.getId());
            if (expectedInterest != null) {
                customLoanInterestCalculationService.adjustInterestCalculationsForReceivableLoan(loan, locProductType, expectedInterest);

                // Adjust principal due in installments: Approved Amount - Interest
                BigDecimal approvedAmount = loan.getApprovedPrincipal();
                if (approvedAmount != null) {
                    BigDecimal correctPrincipalDue = approvedAmount.subtract(expectedInterest);
                    customLoanInterestCalculationService.adjustPrincipalDueForReceivableLoan(loan, correctPrincipalDue);

                    log.info("Applied final custom logic for RECEIVABLE LOC loan {}: interest={}, principal due={}",
                            loan.getId(), expectedInterest, correctPrincipalDue);
                }
            }

            // Update the summary with the correct amounts
            updateLoanSummaryDerivedFieldsForReceivableLoc(loan);
        }
    }

    /**
     * Process disbursement for RECEIVABLE LOC loans with custom logic
     */
    @Transactional
    protected CommandProcessingResult processReceivableLocDisbursement(final Long loanId, final JsonCommand command,
                                                                       Boolean isAccountTransfer, Boolean isWithoutAutoPayment) {
        try {
            Loan loan = loanAssembler.assembleFrom(loanId);

            // Handle expected disbursements
            if (loan.loanProduct().isDisallowExpectedDisbursements()) {
                List<LoanDisbursementDetails> filteredList = loan.getDisbursementDetails().stream()
                        .filter(disbursementDetails -> disbursementDetails.actualDisbursementDate() == null).toList();
                if (filteredList.isEmpty()) {
                    final LocalDate artificialExpectedDate = loan.getExpectedDisbursedOnLocalDate();
                    LoanDisbursementDetails disbursementDetail = new LoanDisbursementDetails(artificialExpectedDate, null,
                            loan.getDisbursedAmount(), null, false);
                    disbursementDetail.updateLoan(loan);
                    loan.getAllDisbursementDetails().add(disbursementDetail);
                }
            }

            final LocalDate nextPossibleRepaymentDate = loan.getNextPossibleRepaymentDateForRescheduling();
            final LocalDate rescheduledRepaymentDate = command.localDateValueOfParameterNamed("adjustRepaymentDate");
            final LocalDate actualDisbursementDate = command.localDateValueOfParameterNamed("actualDisbursementDate");
            if (!loan.isMultiDisburmentLoan()) {
                loan.setActualDisbursementDate(actualDisbursementDate);
            }

            ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, null);
            businessEventNotifierService.notifyPreBusinessEvent(new LoanDisbursalBusinessEvent(loan));

            List<Long> existingTransactionIds = new ArrayList<>();
            List<Long> existingReversedTransactionIds = new ArrayList<>();
            final AppUser currentUser = getAppUserIfPresent();
            final Map<String, Object> changes = new LinkedHashMap<>();

            final PaymentDetail paymentDetail = this.paymentDetailWritePlatformService.createAndPersistPaymentDetail(command, changes);
            if (paymentDetail != null && paymentDetail.getPaymentType() != null && paymentDetail.getPaymentType().getIsCashPayment()) {
                BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed("transactionAmount");
                this.cashierTransactionDataValidator.validateOnLoanDisbursal(currentUser, loan.getCurrencyCode(), transactionAmount);
            }
            final boolean isPaymentTypeApplicableForDisbursementCharge = configurationDomainService
                    .isPaymentTypeApplicableForDisbursementCharge();

            updateLoanCounters(loan, actualDisbursementDate);
            Money amountBeforeAdjust = loan.getPrincipal();

            if (loan.canDisburse()) {
                // For RECEIVABLE LOC loans, calculate the correct net disbursement amount
                BigDecimal approvedAmount = loan.getApprovedPrincipal();
                if (approvedAmount == null) {
                    approvedAmount = loan.getPrincipal().getAmount();
                }

                BigDecimal expectedInterest = calculateExpectedInterest(loan.getId(), approvedAmount);
                BigDecimal correctNetDisbursementAmount = approvedAmount.subtract(expectedInterest);

                // Set the correct net disbursement amount for RECEIVABLE LOC loans
                loan.setNetDisbursalAmount(correctNetDisbursementAmount);

                Money disburseAmount = loanDisbursementService.adjustDisburseAmount(loan, command, actualDisbursementDate);
                Money amountToDisburse = disburseAmount.copy();
                boolean recalculateSchedule = amountBeforeAdjust.isNotEqualTo(loan.getPrincipal());
                final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

                if (loan.isTopup() && loan.getClientId() != null) {
                    final BigDecimal loanOutstanding = loanApplicationValidator.validateTopupLoan(loan, actualDisbursementDate);
                    amountToDisburse = disburseAmount.minus(loanOutstanding);
                    disburseLoanToLoan(loan, command, loanOutstanding);
                }

                LoanTransaction disbursementTransaction = null;
                if (isAccountTransfer) {
                    disburseLoanToSavings(loan, command, amountToDisburse, paymentDetail);
                    existingTransactionIds.addAll(loan.findExistingTransactionIds());
                    existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
                } else {
                    existingTransactionIds.addAll(loan.findExistingTransactionIds());
                    existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());

                    // For RECEIVABLE LOC loans, create a disbursement transaction with proper principal and interest breakdown
                    disbursementTransaction = LoanTransaction.disbursement(loan, amountToDisburse, paymentDetail, actualDisbursementDate,
                            txnExternalId, loan.getTotalOverpaidAsMoney());

                    // Set the principal and interest portions for RECEIVABLE LOC loans
                    // Use the expected interest that was calculated earlier
                    if (expectedInterest != null) {
                        BigDecimal principalPortion = correctNetDisbursementAmount; // 87,300
                        BigDecimal interestPortion = expectedInterest; // 2,700

                        // Create Money objects for the components
                        Money principalMoney = Money.of(loan.getCurrency(), principalPortion);
                        Money interestMoney = Money.of(loan.getCurrency(), interestPortion);
                        Money feeChargesMoney = Money.zero(loan.getCurrency());
                        Money penaltyChargesMoney = Money.zero(loan.getCurrency());

                        // Update the transaction components
                        disbursementTransaction.updateComponentsAndTotal(principalMoney, interestMoney, feeChargesMoney, penaltyChargesMoney);
                    }

                    disbursementTransaction.updateLoan(loan);
                    loan.addLoanTransaction(disbursementTransaction);
                    loanTransactionRepository.saveAndFlush(disbursementTransaction);
                }

                if (loan.getRepaymentScheduleInstallments().isEmpty()) {
                    recalculateSchedule = true;
                }

                // Compute and update Line of Credit balance upon disbursement
                computeLocBalance(loanId, amountToDisburse.getAmount(), actualDisbursementDate);

                // Use our custom schedule generation
                regenerateScheduleOnDisbursement(command, loan, recalculateSchedule, scheduleGeneratorDTO, nextPossibleRepaymentDate,
                        rescheduledRepaymentDate);

                boolean downPaymentEnabled = loan.repaymentScheduleDetail().isEnableDownPayment();
                if (loan.isInterestBearingAndInterestRecalculationEnabled() || downPaymentEnabled) {
                    createAndSaveLoanScheduleArchive(loan, scheduleGeneratorDTO);
                }

                // Use our custom disbursement logic
                disburseLoan(command, isPaymentTypeApplicableForDisbursementCharge, paymentDetail, loan, currentUser, changes,
                        scheduleGeneratorDTO);

                loanAccrualsProcessingService.reprocessExistingAccruals(loan);
                LocalDate firstInstallmentDueDate = loan.fetchRepaymentScheduleInstallment(1).getDueDate();
                if (loan.isInterestBearingAndInterestRecalculationEnabled()
                        && (DateUtils.isBeforeBusinessDate(firstInstallmentDueDate) || loan.isDisbursementMissed())) {
                    loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
                }
            }

            if (!changes.isEmpty()) {
                createNote(loan, command, changes);
                loan = saveAndFlushLoanWithDataIntegrityViolationChecks(loan);
                journalEntryPoster.postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds);
                loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);
            }

            final Set<LoanCharge> loanCharges = loan.getActiveCharges();
            final Map<Long, BigDecimal> disBuLoanCharges = new HashMap<>();
            for (final LoanCharge loanCharge : loanCharges) {
                if (loanCharge.isDisbursementCharge()) {
                    disBuLoanCharges.put(loanCharge.getId(), loanCharge.amount());
                }
            }

            businessEventNotifierService.notifyPostBusinessEvent(new LoanDisbursalBusinessEvent(loan));

            return new CommandProcessingResultBuilder() //
                    .withCommandId(command.commandId()) //
                    .withEntityId(loanId) //
                    .withOfficeId(loan.getOfficeId()) //
                    .withClientId(loan.getClientId()) //
                    .withGroupId(loan.getGroupId()) //
                    .withLoanId(loanId) //
                    .with(changes) //
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to process RECEIVABLE LOC disbursement", e);
        }
    }

    /**
     * Override the parent's regenerateScheduleOnDisbursement method to use correct amounts for LOC receivable loans.
     * For RECEIVABLE LOC loans, this method ensures that:
     * 1. Interest is calculated based on the approved amount (discounted amount)
     * 2. Principal portion is calculated as Approved Amount - Interest
     * 3. The schedule uses the net disbursement amount as the principal for repayment
     */
    @Override
    protected void regenerateScheduleOnDisbursement(final JsonCommand command, final Loan loan, final boolean recalculateSchedule,
                                                    final ScheduleGeneratorDTO scheduleGeneratorDTO, final LocalDate nextPossibleRepaymentDate,
                                                    final LocalDate rescheduledRepaymentDate) {

        String locProductType = getLocProductType(loan.getId());

        if (LocProductType.RECEIVABLE.name().equals(locProductType)) {
            // Get the approved amount (this is the discounted amount for RECEIVABLE loans)
            BigDecimal approvedAmount = loan.getApprovedPrincipal();
            if (approvedAmount == null) {
                approvedAmount = loan.getPrincipal().getAmount();
            }

            BigDecimal netDisbursementAmount = loan.getNetDisbursalAmount();
            if (netDisbursementAmount == null) {
                // Calculate net disbursement amount: Approved Amount - Expected Interest
                BigDecimal expectedInterest = calculateExpectedInterest(loan.getId(), approvedAmount);
                if (expectedInterest != null) {
                    netDisbursementAmount = approvedAmount.subtract(expectedInterest);
                    log.debug("Calculated net disbursement amount for RECEIVABLE loan {}: {} (approved: {}, expected interest: {})",
                            loan.getId(), netDisbursementAmount, approvedAmount, expectedInterest);
                } else {
                    netDisbursementAmount = approvedAmount;
                    log.debug("Could not calculate expected interest for RECEIVABLE loan {}, using approved amount as net disbursement: {}",
                            loan.getId(), netDisbursementAmount);
                }
            }

            // Call the parent method to generate the standard schedule
            super.regenerateScheduleOnDisbursement(command, loan, recalculateSchedule, scheduleGeneratorDTO,
                    nextPossibleRepaymentDate, rescheduledRepaymentDate);

        } else {
            // For non-RECEIVABLE loans, use the standard parent method
            super.regenerateScheduleOnDisbursement(command, loan, recalculateSchedule, scheduleGeneratorDTO,
                    nextPossibleRepaymentDate, rescheduledRepaymentDate);
        }
    }

    /**
     * Reverses LOC balance adjustments when a disbursal is undone.
     * This method:
     * 1. Increases the available LOC balance by the reversed disbursal amount
     * 2. Decreases the consumed amount by the reversed disbursal amount
     * 3. Creates and persists a LOC transaction history record with reversal metadata
     *
     * @param loanId          the loan ID
     * @param reversedAmount  the amount being reversed (should be the approved_principal from the loan)
     * @param transactionDate the reversal date
     * @return true if LOC balance was successfully updated, false if no LOC association exists
     */
    @Transactional
    public boolean reverseLocBalanceOnDisbursalUndo(Long loanId, BigDecimal reversedAmount, LocalDate transactionDate) {
        try {
            // Check if loan is associated with a line of credit
            String sql = "SELECT line_of_credit_id FROM m_loan WHERE id = ?";
            Long lineOfCreditId = jdbcTemplate.queryForObject(sql, Long.class, loanId);

            if (lineOfCreditId == null) {
                return true; // No LOC to adjust
            }

            // Get the line of credit details
            String locSql = "SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?";
            Map<String, Object> locData = jdbcTemplate.queryForMap(locSql, lineOfCreditId);

            BigDecimal currentAvailableBalance = (BigDecimal) locData.get("available_balance");
            BigDecimal currentConsumedAmount = (BigDecimal) locData.get("consumed_amount");

            // Handle null values
            if (currentAvailableBalance == null) currentAvailableBalance = BigDecimal.ZERO;
            if (currentConsumedAmount == null) currentConsumedAmount = BigDecimal.ZERO;


            // Calculate new balances (reverse the disbursal effect)
            BigDecimal newAvailableBalance = currentAvailableBalance.add(reversedAmount);
            BigDecimal newConsumedAmount = currentConsumedAmount.subtract(reversedAmount);

            // Ensure consumed amount doesn't go below zero
            if (newConsumedAmount.compareTo(BigDecimal.ZERO) < 0) {
                newConsumedAmount = BigDecimal.ZERO;
                // Adjust available balance accordingly
                newAvailableBalance = currentAvailableBalance.add(currentConsumedAmount);
            }

            // Update LOC balances
            String updateSql = "UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ?, last_modified_on_utc = NOW() WHERE id = ?";
            int rowsUpdated = jdbcTemplate.update(updateSql, newAvailableBalance, newConsumedAmount, lineOfCreditId);

            if (rowsUpdated == 0) {
                log.error("Failed to update LOC balances for line of credit {}", lineOfCreditId);
                throw new PlatformApiDataValidationException("error.msg.loc.update.failed",
                        "Failed to update line of credit balances", "lineOfCreditId", lineOfCreditId);
            }

            // Create LOC transaction history record
            String insertSql = "INSERT INTO m_line_of_credit_transactions " +
                    "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                    "transaction_date, reference_number, description, created_on_utc, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            String referenceNumber = "LOAN_" + loanId + "_DISBURSAL_REVERSAL";
            String description = String.format("Loan disbursal reversal - LOC balance increased by %s for loan %s",
                    reversedAmount, loanId);

            jdbcTemplate.update(insertSql,
                    lineOfCreditId,
                    "DISBURSAL_REVERSAL",
                    reversedAmount,
                    currentAvailableBalance,
                    newAvailableBalance,
                    transactionDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toOffsetDateTime(),
                    referenceNumber,
                    description,
                    java.time.OffsetDateTime.now(),
                    null);

            return true;

        } catch (PlatformApiDataValidationException e) {
            log.error("Failed to reverse LOC balance for loan {}: {}", loanId, e.getMessage());
            throw new PlatformApiDataValidationException("error.msg.loc.reversal.failed",
                    "Failed to reverse line of credit balance", "loanId", loanId, e);
        }
    }

    /**
     * Override the parent's undoLoanDisbursal method to include LOC balance adjustments
     */
    @Override
    @Transactional
    public CommandProcessingResult undoLoanDisbursal(final Long loanId, final JsonCommand command) {
        // Get the disbursal amount before calling parent method
        BigDecimal disbursalAmount = null;
        // Get the loan's approved principal (this is the amount that was disbursed)
        disbursalAmount = jdbcTemplate.queryForObject(
                "SELECT approved_principal FROM m_loan WHERE id = ?",
                BigDecimal.class,
                loanId
        );


        // Call the parent method to perform the standard undo disbursal
        CommandProcessingResult result = super.undoLoanDisbursal(loanId, command);

        // If the undo was successful and we have a disbursal amount, adjust LOC balances
        if (disbursalAmount != null && disbursalAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Get the transaction date from the command or use current date
            LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");
            if (transactionDate == null) {
                transactionDate = DateUtils.getBusinessLocalDate();
            }

            // Reverse the LOC balance adjustments
            reverseLocBalanceOnDisbursalUndo(loanId, disbursalAmount, transactionDate);

        }

        return result;
    }

    /**
     * Override the parent's undoLastLoanDisbursal method to include LOC balance adjustments
     */
    @Override
    @Transactional
    public CommandProcessingResult undoLastLoanDisbursal(Long loanId, JsonCommand command) {
        // Get the last disbursal amount before calling parent method
        BigDecimal lastDisbursalAmount = null;
        // For undo last disbursal, we need to get the amount from the last disbursal transaction
        // But for LOC balance reversal, we should use the approved_principal
        String sql = "SELECT approved_principal FROM m_loan WHERE id = ?";
        lastDisbursalAmount = jdbcTemplate.queryForObject(sql, BigDecimal.class, loanId);


        // Call the parent method to perform the standard undo last disbursal
        CommandProcessingResult result = super.undoLastLoanDisbursal(loanId, command);

        // If the undo was successful and we have a disbursal amount, adjust LOC balances
        if (lastDisbursalAmount != null && lastDisbursalAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Get the transaction date from the command or use current date
            LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");
            if (transactionDate == null) {
                transactionDate = DateUtils.getBusinessLocalDate();
            }

            // Reverse the LOC balance adjustments
            reverseLocBalanceOnDisbursalUndo(loanId, lastDisbursalAmount, transactionDate);

        }

        return result;
    }

    /**
     * Override the parent's adjustLoanTransaction method to include LOC balance adjustments
     * when undoing/reversing loan transactions (especially repayments)
     */
    @Override
    @Transactional
    public CommandProcessingResult adjustLoanTransaction(final Long loanId, final Long transactionId, final JsonCommand command) {
        // Get the transaction details before calling parent method
        LoanTransaction transactionToAdjust = this.loanTransactionRepository.findByIdAndLoanId(transactionId, loanId)
                .orElseThrow(() -> new LoanTransactionNotFoundException(transactionId, loanId));

        // Check if this is a repayment transaction being reversed
        boolean isRepaymentReversal = transactionToAdjust.isRepayment() &&
                command.bigDecimalValueOfParameterNamed("transactionAmount").compareTo(BigDecimal.ZERO) == 0;

        BigDecimal repaymentAmount = null;
        if (isRepaymentReversal) {
            repaymentAmount = transactionToAdjust.getAmount();
        }

        // Call the parent method to perform the standard transaction adjustment
        CommandProcessingResult result = super.adjustLoanTransaction(loanId, transactionId, command);

        // After the parent method completes, apply custom logic for RECEIVABLE LOC loans
        String locProductType = getLocProductType(loanId);
        if (LocProductType.RECEIVABLE.name().equals(locProductType)) {
            // Reload the loan to get the updated state after the parent method
            Loan loan = loanAssembler.assembleFrom(loanId);

            // Update the loan summary with correct amounts for RECEIVABLE LOC loans
            updateLoanSummaryDerivedFieldsForReceivableLoc(loan);

            // Save the loan with updated summary
            saveAndFlushLoanWithDataIntegrityViolationChecks(loan);

        }

        // If this was a repayment reversal and we have a repayment amount, adjust LOC balances
        if (isRepaymentReversal && repaymentAmount != null && repaymentAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Get the transaction date from the command or use current date
            LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");
            if (transactionDate == null) {
                transactionDate = DateUtils.getBusinessLocalDate();
            }

            // Reverse the LOC balance adjustments (opposite of what happens during repayment)
            reverseLocBalanceOnRepaymentUndo(loanId, repaymentAmount, transactionDate);

        }

        return result;
    }

    /**
     * Reverses LOC balance adjustments when a repayment is undone.
     * This method:
     * 1. Decreases the available LOC balance by the reversed repayment amount
     * 2. Increases the consumed amount by the reversed repayment amount
     * 3. Creates and persists a LOC transaction history record with reversal metadata
     *
     * @param loanId          the loan ID
     * @param reversedAmount  the amount being reversed (repayment amount)
     * @param transactionDate the reversal date
     * @return true if LOC balance was successfully updated, false if no LOC association exists
     */
    @Transactional
    public boolean reverseLocBalanceOnRepaymentUndo(Long loanId, BigDecimal reversedAmount, LocalDate transactionDate) {
        try {
            // Check if loan is associated with a line of credit
            String sql = "SELECT line_of_credit_id FROM m_loan WHERE id = ?";
            Long lineOfCreditId = jdbcTemplate.queryForObject(sql, Long.class, loanId);

            if (lineOfCreditId == null) {
                log.debug("No LOC associated with loan {}, skipping balance reversal", loanId);
                return true; // No LOC to adjust
            }

            // Get the line of credit details
            String locSql = "SELECT id, available_balance, consumed_amount, maximum_amount FROM m_line_of_credit WHERE id = ?";
            Map<String, Object> locData = jdbcTemplate.queryForMap(locSql, lineOfCreditId);

            BigDecimal currentAvailableBalance = (BigDecimal) locData.get("available_balance");
            BigDecimal currentConsumedAmount = (BigDecimal) locData.get("consumed_amount");

            // Handle null values
            if (currentAvailableBalance == null) currentAvailableBalance = BigDecimal.ZERO;
            if (currentConsumedAmount == null) currentConsumedAmount = BigDecimal.ZERO;

            // Calculate new balances (reverse the repayment effect)
            BigDecimal newAvailableBalance = currentAvailableBalance.subtract(reversedAmount);
            BigDecimal newConsumedAmount = currentConsumedAmount.add(reversedAmount);

            // Ensure available balance doesn't go below zero
            if (newAvailableBalance.compareTo(BigDecimal.ZERO) < 0) {
                newAvailableBalance = BigDecimal.ZERO;
                // Adjust consumed amount accordingly
                newConsumedAmount = currentConsumedAmount.add(currentAvailableBalance);
            }

            // Update the LOC balances
            String updateSql = "UPDATE m_line_of_credit SET available_balance = ?, consumed_amount = ?, last_modified_on_utc = NOW() WHERE id = ?";
            int rowsUpdated = jdbcTemplate.update(updateSql, newAvailableBalance, newConsumedAmount, lineOfCreditId);

            if (rowsUpdated == 0) {
                throw new PlatformApiDataValidationException("error.msg.loc.update.failed",
                        "Failed to update line of credit balances", "lineOfCreditId", lineOfCreditId);
            }

            // Create a transaction history record
            String insertSql = "INSERT INTO m_line_of_credit_transactions " +
                    "(line_of_credit_id, transaction_type, amount, balance_before, balance_after, " +
                    "transaction_date, reference_number, description, created_on_utc, created_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            String referenceNumber = "LOAN_" + loanId + "_REPAYMENT_REVERSAL";
            String description = String.format("Loan repayment reversal - LOC balance decreased by %s for loan %s",
                    reversedAmount, loanId);

            jdbcTemplate.update(insertSql,
                    lineOfCreditId,
                    "REPAYMENT_REVERSAL",
                    reversedAmount,
                    currentAvailableBalance,
                    newAvailableBalance,
                    transactionDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toOffsetDateTime(),
                    referenceNumber,
                    description,
                    java.time.OffsetDateTime.now(),
                    null);

            return true;

        } catch (PlatformApiDataValidationException e) {
            log.error("Validation error reversing LOC balance for repayment undo - loan {}: {}", loanId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to reverse LOC balance for repayment undo - loan {}: {}", loanId, e.getMessage());
            throw new PlatformApiDataValidationException("error.msg.loc.reversal.failed",
                    "Failed to reverse line of credit balance", "loanId", loanId, e);
        }
    }

    /**
     * Processes LOC loan foreclosure interest calculations including earned interest and refund
     *
     * @param loan                          The loan being foreclosed
     * @param annualInterestRate            The annual interest rate on the line of credit
     * @param originalInterestChargeDerived The original interest charge derived from disbursement
     * @param productType                   The LOC product type
     * @param originalPrincipalDisbursed    The original principal disbursed amount
     */
    private void processLocLoanForeclosureInterest(Loan loan, BigDecimal annualInterestRate, BigDecimal originalInterestChargeDerived, String productType, BigDecimal originalPrincipalDisbursed) {
        try {
            Long loanId = loan.getId();

            // Use the original values passed from the calling method
            BigDecimal approvedPrincipal = originalPrincipalDisbursed;
            LocalDate disbursementDate = loan.getDisbursementDate();
            LocalDate foreclosureDate = DateUtils.getBusinessLocalDate();

            if (approvedPrincipal == null || disbursementDate == null) {
                return;
            }

            // Calculate interest earned using the formula: approved_principal * annualInterestRate * (days_from_disbursement) / 365
            BigDecimal interestEarned = calculateInterestEarned(approvedPrincipal, originalInterestChargeDerived, disbursementDate, foreclosureDate, annualInterestRate);

            // Calculate refund amount: original_interest_charge_derived - interest_earned
            BigDecimal refundAmount = originalInterestChargeDerived.subtract(interestEarned);

            // Get the net disbursal amount from the loan object
            BigDecimal netDisbursalAmount = loan.getNetDisbursalAmount();
            if (netDisbursalAmount == null) {
                netDisbursalAmount = approvedPrincipal;
            }

            //Update m_loan table with the calculated values
            // Note: We need to restore the original values that were set during disbursement
            // and only update the "Paid" and "Outstanding" fields for foreclosure.
            String updateLoanSql = "UPDATE m_loan SET " +
                    "principal_disbursed_derived = ?, " +      // Restore original principal disbursed
                    "interest_charged_derived = ?, " +         // Restore original interest charged
                    "total_expected_repayment_derived = ?, " + // Restore original total expected repayment
                    "principal_outstanding_derived = 0, " +
                    "interest_repaid_derived = ?, " +
                    "interest_outstanding_derived = 0, " +
                    "total_outstanding_derived = 0 " +
                    "WHERE id = ?";

            // Calculate the original total expected repayment (principal + interest)
            BigDecimal originalTotalExpectedRepayment = netDisbursalAmount.add(originalInterestChargeDerived);

            jdbcTemplate.update(updateLoanSql,
                    netDisbursalAmount,          // principal_disbursed_derived (restore original)
                    originalInterestChargeDerived, // interest_charged_derived (restore original)
                    originalTotalExpectedRepayment, // total_expected_repayment_derived (restore original)
                    interestEarned,              // interest_repaid_derived
                    loanId                       // WHERE id = ?
            );


            // Process refund to linked savings account if refund amount is positive
            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                processRefundToLinkedSavingsAccount(loan, refundAmount);
            }

        } catch (Exception e) {
            throw new PlatformApiDataValidationException("error.msg.loc.foreclosure.interest.calculation.failed",
                    "Failed to process LOC loan foreclosure interest calculation", "loanId", loan.getId(), e);
        }
    }

    /**
     * Calculates interest earned using the formula: approved_principal * interest_charge_derived * (days_from_disbursement) / 365
     *
     * @param approvedPrincipal     The approved principal amount
     * @param interestChargeDerived The interest charge derived from the database
     * @param disbursementDate      The loan disbursement date
     * @param foreclosureDate       The foreclosure date
     * @return The calculated interest earned
     */
    private BigDecimal calculateInterestEarned(BigDecimal approvedPrincipal, BigDecimal interestChargeDerived,
                                               LocalDate disbursementDate, LocalDate foreclosureDate, BigDecimal annualInterest) {
        try {
            // Validate required parameters
            if (annualInterest == null) {
                throw new IllegalArgumentException("Annual interest rate cannot be null");
            }

            // Calculate number of days from disbursement to foreclosure
            int daysFromDisbursement = DateUtils.getExactDifferenceInDays(disbursementDate, foreclosureDate);

            // Apply the formula: approved_principal * interest_charge_derived * (days_from_disbursement) / 365
            BigDecimal daysInYear = new BigDecimal("365");
            BigDecimal timeFactor = new BigDecimal(daysFromDisbursement).divide(daysInYear, 10, java.math.RoundingMode.HALF_UP);
            BigDecimal interestEarned = approvedPrincipal.multiply(annualInterest.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)).multiply(timeFactor);

            // Round to 2 decimal places for currency precision
            interestEarned = interestEarned.setScale(2, java.math.RoundingMode.HALF_UP);

            log.debug("Interest earned calculation: {} * {} * {} / 365 = {}",
                    approvedPrincipal, interestChargeDerived, daysFromDisbursement, interestEarned);

            return interestEarned;

        } catch (Exception e) {
            log.error("Error calculating interest earned: {}", e.getMessage(), e);
            throw new PlatformApiDataValidationException("error.msg.interest.earned.calculation.failed",
                    "Failed to calculate interest earned", "interestEarned", null, e);
        }
    }

    /**
     * Processes refund to the linked savings account
     *
     * @param loan         The loan
     * @param refundAmount The refund amount
     */
    private void processRefundToLinkedSavingsAccount(Loan loan, BigDecimal refundAmount) {
        try {
            Long loanId = loan.getId();

            // Get the linked savings account
            Long linkedSavingsAccountId = getLinkedSavingsAccountId(loanId);

            if (linkedSavingsAccountId == null) {
                return;
            }

            // Create a deposit transaction to the linked savings account
            createRefundDepositTransaction(linkedSavingsAccountId, refundAmount, loan.getCurrency(), loanId);

        } catch (Exception e) {
            throw new PlatformApiDataValidationException("error.msg.refund.processing.failed",
                    "Failed to process refund to linked savings account", "loanId", loan.getId(), e);
        }
    }

    /**
     * Gets the linked savings account ID for a loan
     *
     * @param loanId The loan ID
     * @return The linked savings account ID or null if not found
     */
    private Long getLinkedSavingsAccountId(Long loanId) {
        try {
            PortfolioAccountData linkedAccountData = this.accountAssociationsReadPlatformService.retriveLoanLinkedAssociation(loanId);
            return linkedAccountData != null ? linkedAccountData.getId() : null;
        } catch (Exception e) {
            log.error("Error retrieving linked savings account for loan {}: {}", loanId, e.getMessage());
            return null;
        }
    }

    /**
     * Creates a deposit transaction to the linked savings account for the refund
     *
     * @param savingsAccountId The savings account ID
     * @param amount           The refund amount
     * @param currency         The currency
     * @param loanId           The loan ID for reference
     */
    private void createRefundDepositTransaction(Long savingsAccountId, BigDecimal amount, MonetaryCurrency currency, Long loanId) {
        try {
            // Create a simple JSON string for the deposit transaction
            String jsonString = String.format(
                    "{\"transactionDate\":\"%s\",\"transactionAmount\":\"%s\",\"note\":\"Refund from LOC loan foreclosure - Loan ID: %d\",\"paymentTypeId\":1,\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\"}",
                    DateUtils.getBusinessLocalDate().toString(),
                    amount.toString(),
                    loanId
            );

            // Parse the JSON string using fromApiJsonHelper
            JsonElement parsedJson = fromApiJsonHelper.parse(jsonString);

            // Create JsonCommand with proper parsing
            JsonCommand command = JsonCommand.from(jsonString, parsedJson, fromApiJsonHelper, "savingsaccount", savingsAccountId, null, null, null, null, null, null, null, null, null, null, null, null);

            // Create the deposit transaction using the savings account service
            CommandProcessingResult result = savingsAccountWritePlatformService.deposit(savingsAccountId, command);

        } catch (Exception e) {
            throw new PlatformApiDataValidationException("error.msg.refund.transaction.creation.failed",
                    "Failed to create refund deposit transaction", "refundTransaction", null, e);
        }
    }

    /**
     * Gets the approved principal for a loan
     *
     * @param loanId The loan ID
     * @return The approved principal or null if not found
     */
    private BigDecimal getApprovedPrincipalForLoan(Long loanId) {
        try {
            String sql = "SELECT approved_principal FROM m_loan WHERE id = ?";
            List<BigDecimal> results = jdbcTemplate.queryForList(sql, BigDecimal.class, loanId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.error("Error retrieving approved principal for loan {}: {}", loanId, e.getMessage());
            return null;
        }
    }

    /**
     * Gets the interest_charge_derived for a loan
     *
     * @param loanId The loan ID
     * @return The interest_charge_derived or null if not found
     */
    private BigDecimal getInterestChargeDerived(Long loanId) {
        try {
            String sql = "SELECT interest_charged_derived FROM m_loan WHERE id = ?";
            List<BigDecimal> results = jdbcTemplate.queryForList(sql, BigDecimal.class, loanId);
            return results.isEmpty() ? null : results.get(0);
        } catch (ResourceNotFoundException e) {
            log.error("Error retrieving interest_charge_derived for loan {}: {}", loanId, e.getMessage());
            return null;
        }
    }

    /**
     * Gets the annual interest rate for a loan from its associated line of credit
     *
     * @param loanId The ID of the loan
     * @return The annual interest rate or null if not found
     */
    private BigDecimal getAnnualInterestRate(Long loanId) {
        try {
            // First get the line of credit ID associated with the loan
            String sql = "SELECT line_of_credit_id FROM m_loan WHERE id = ?";
            Long lineOfCreditId = jdbcTemplate.queryForObject(sql, Long.class, loanId);

            if (lineOfCreditId == null) {
                return null;
            }

            // Then get the annual interest rate from the line of credit
            String locSql = "SELECT annual_interest_rate FROM m_line_of_credit WHERE id = ?";
            List<BigDecimal> results = jdbcTemplate.queryForList(locSql, BigDecimal.class, lineOfCreditId);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
