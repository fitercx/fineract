package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.account.data.CustomAccountTransferDTO;
import com.crediblex.fineract.portfolio.loc.data.ProductType;
import com.google.common.collect.Lists;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
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
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
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
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.crediblex.fineract.infrastructure.commands.utils.LoanTransactionInstallmentUtils;
import com.crediblex.fineract.portfolio.loanaccount.service.CustomLoanInterestCalculationService;

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

    public CustomLoanWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context, LoanTransactionValidator loanTransactionValidator, LoanUpdateCommandFromApiJsonDeserializer loanUpdateCommandFromApiJsonDeserializer, LoanRepositoryWrapper loanRepositoryWrapper, LoanAccountDomainService loanAccountDomainService, NoteRepository noteRepository, LoanTransactionRepository loanTransactionRepository, LoanTransactionRelationRepository loanTransactionRelationRepository, LoanAssembler loanAssembler, JournalEntryWritePlatformService journalEntryWritePlatformService, CalendarInstanceRepository calendarInstanceRepository, PaymentDetailWritePlatformService paymentDetailWritePlatformService, HolidayRepositoryWrapper holidayRepository, ConfigurationDomainService configurationDomainService, WorkingDaysRepositoryWrapper workingDaysRepository, AccountTransfersWritePlatformService accountTransfersWritePlatformService, AccountTransfersReadPlatformService accountTransfersReadPlatformService, AccountAssociationsReadPlatformService accountAssociationsReadPlatformService, LoanReadPlatformService loanReadPlatformService, FromJsonHelper fromApiJsonHelper, CalendarRepository calendarRepository, LoanScheduleHistoryWritePlatformService loanScheduleHistoryWritePlatformService, LoanApplicationValidator loanApplicationValidator, AccountAssociationsRepository accountAssociationRepository, AccountTransferDetailRepository accountTransferDetailRepository, BusinessEventNotifierService businessEventNotifierService, GuarantorDomainService guarantorDomainService, LoanUtilService loanUtilService, EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, CodeValueRepositoryWrapper codeValueRepository, CashierTransactionDataValidator cashierTransactionDataValidator, GLIMAccountInfoRepository glimRepository, LoanRepository loanRepository, RepaymentWithPostDatedChecksAssembler repaymentWithPostDatedChecksAssembler, PostDatedChecksRepository postDatedChecksRepository, LoanRepaymentScheduleInstallmentRepository loanRepaymentScheduleInstallmentRepository, LoanLifecycleStateMachine loanLifecycleStateMachine, LoanAccountLockService loanAccountLockService, ExternalIdFactory externalIdFactory, LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService, ErrorHandler errorHandler, LoanDownPaymentHandlerService loanDownPaymentHandlerService, LoanTransactionAssembler loanTransactionAssembler, LoanAccrualsProcessingService loanAccrualsProcessingService, LoanOfficerValidator loanOfficerValidator, LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator, LoanDisbursementService loanDisbursementService, LoanScheduleService loanScheduleService, LoanChargeValidator loanChargeValidator, LoanOfficerService loanOfficerService, ReprocessLoanTransactionsService reprocessLoanTransactionsService, LoanAccountService loanAccountService, LoanJournalEntryPoster journalEntryPoster, LoanAdjustmentService loanAdjustmentService, LoanAccountingBridgeMapper loanAccountingBridgeMapper, LoanMapper loanMapper, LoanTransactionProcessingService loanTransactionProcessingService, FineractProperties fineractProperties, JdbcTemplate jdbcTemplate) {
        super(context, loanTransactionValidator, loanUpdateCommandFromApiJsonDeserializer, loanRepositoryWrapper, loanAccountDomainService, noteRepository, loanTransactionRepository, loanTransactionRelationRepository, loanAssembler, journalEntryWritePlatformService, calendarInstanceRepository, paymentDetailWritePlatformService, holidayRepository, configurationDomainService, workingDaysRepository, accountTransfersWritePlatformService, accountTransfersReadPlatformService, accountAssociationsReadPlatformService, loanReadPlatformService, fromApiJsonHelper, calendarRepository, loanScheduleHistoryWritePlatformService, loanApplicationValidator, accountAssociationRepository, accountTransferDetailRepository, businessEventNotifierService, guarantorDomainService, loanUtilService, entityDatatableChecksWritePlatformService, codeValueRepository, cashierTransactionDataValidator, glimRepository, loanRepository, repaymentWithPostDatedChecksAssembler, postDatedChecksRepository, loanRepaymentScheduleInstallmentRepository, loanLifecycleStateMachine, loanAccountLockService, externalIdFactory, loanAccrualTransactionBusinessEventService, errorHandler, loanDownPaymentHandlerService, loanTransactionAssembler, loanAccrualsProcessingService, loanOfficerValidator, loanDownPaymentTransactionValidator, loanDisbursementService, loanScheduleService, loanChargeValidator, loanOfficerService, reprocessLoanTransactionsService, loanAccountService, journalEntryPoster, loanAdjustmentService, loanAccountingBridgeMapper, loanMapper, loanTransactionProcessingService, fineractProperties);
        this.jdbcTemplate = jdbcTemplate;
    }


    @Transactional
    @Override
    public CommandProcessingResult disburseLoan(final Long loanId, final JsonCommand command, Boolean isAccountTransfer,
                                                Boolean isWithoutAutoPayment) {


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
                loan.setNetDisbursalAmount(netDisbursalAmount);
            }
            
            // Check if this is a RECEIVABLE type LOC loan and adjust disbursement amount accordingly
            String locProductType = getLocProductType(loan.getId());
            Money disburseAmount = loanDisbursementService.adjustDisburseAmount(loan, command, actualDisbursementDate);
            
            if (ProductType.RECEIVABLE.name().equals(locProductType)) {
                // For RECEIVABLE type, use net disbursed amount instead of the original disbursement amount
                BigDecimal discountedAmount = calculateDiscountedAmount(loan.getId(), disburseAmount.getAmount());
                BigDecimal expectedInterest = calculateExpectedInterest(loan.getId(), discountedAmount);
                BigDecimal netDisbursedAmount = calculateNetDisbursedAmount(loan.getId(), disburseAmount.getAmount());
                
                if (netDisbursedAmount != null && expectedInterest != null) {
                    disburseAmount = Money.of(loan.getCurrency(), netDisbursedAmount);
                    
                    // Log the calculation data for audit purposes
                    logLocCalculationData(loan.getId(), discountedAmount, expectedInterest);
                    
                    log.info("Using net disbursed amount for RECEIVABLE LOC loan {}: {} (original: {}), expected interest: {}, discounted amount: {}", 
                            loan.getId(), netDisbursedAmount, command.bigDecimalValueOfParameterNamed(LoanApiConstants.disbursementNetDisbursalAmountParameterName),
                            expectedInterest, discountedAmount);
                } else {
                    log.warn("Could not calculate net disbursed amount or expected interest for RECEIVABLE LOC loan {}, using original amount", loan.getId());
                }
            }
            
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

                final Set<LoanCharge> loanCharges = loan.getActiveCharges();
                BigDecimal chargeReducableFromDisbursement = BigDecimal.ZERO;
                for (final LoanCharge loanCharge : loanCharges) {
                    if (loanCharge.isDueAtDisbursement() && !loanCharge.getChargePaymentMode().isPaymentModeAccountTransfer()
                            && loanCharge.isChargePending()) {
                        chargeReducableFromDisbursement = chargeReducableFromDisbursement.add(loanCharge.amountOutstanding());
                    }

                }

                loan.getSummary().setTotalChargesPayableByPrincipalDeduction(chargeReducableFromDisbursement);

                disburseLoanToSavings(loan, command, amountToDisburse, paymentDetail);
                existingTransactionIds.addAll(loan.findExistingTransactionIds());
                existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
            } else {
                existingTransactionIds.addAll(loan.findExistingTransactionIds());
                existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
                disbursementTransaction = LoanTransaction.disbursement(loan, amountToDisburse, paymentDetail, actualDisbursementDate,
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

            // Compute and update Line of Credit balance upon disbursement
            computeLocBalance(loanId, amountToDisburse.getAmount(), actualDisbursementDate);


            regenerateScheduleOnDisbursement(command, loan, recalculateSchedule, scheduleGeneratorDTO, nextPossibleRepaymentDate,
                    rescheduledRepaymentDate);
            
            // Apply custom interest calculations for RECEIVABLE LOC loans after schedule generation
            try {
                String productType = getLocProductType(loan.getId());
                if (ProductType.RECEIVABLE.name().equals(productType)) {
                    BigDecimal expectedInterest = getExpectedInterestForReceivableLoan(loan.getId());
                    customLoanInterestCalculationService.adjustInterestCalculationsForReceivableLoan(loan, productType, expectedInterest);
                }
            } catch (Exception e) {
                log.warn("Failed to adjust interest calculations for RECEIVABLE LOC loan {}: {}", loan.getId(), e.getMessage());
                // Don't fail the disbursement if interest adjustment fails
            }
            
            boolean downPaymentEnabled = loan.repaymentScheduleDetail().isEnableDownPayment();
            if (loan.isInterestBearingAndInterestRecalculationEnabled() || downPaymentEnabled) {
                createAndSaveLoanScheduleArchive(loan, scheduleGeneratorDTO);
            }
            disburseLoan(command, isPaymentTypeApplicableForDisbursementCharge, paymentDetail, loan, currentUser, changes,
                    scheduleGeneratorDTO);
            loan.adjustNetDisbursalAmount(amountToDisburse.getAmount());

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
                    Money downPaymentMoney = Money.of(loan.getCurrency(),
                            MathUtil.percentageOf(amountToDisburse.getAmount(), disbursedAmountPercentageForDownPayment, 19));
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

                    disburseLoanToSavings(loan, command, disburseAmount, paymentDetail);
                    existingTransactionIds.addAll(loan.findExistingTransactionIds());
                    existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());

                } else {
                    existingTransactionIds.addAll(loan.findExistingTransactionIds());
                    existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
                    LoanTransaction disbursementTransaction = LoanTransaction.disbursement(loan, disburseAmount, paymentDetail,
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
                
                // Apply custom interest calculations for RECEIVABLE LOC loans after schedule generation
                try {
                    String productType = getLocProductType(loan.getId());
                    if (ProductType.RECEIVABLE.name().equals(productType)) {
                        BigDecimal expectedInterest = getExpectedInterestForReceivableLoan(loan.getId());
                        customLoanInterestCalculationService.adjustInterestCalculationsForReceivableLoan(loan, productType, expectedInterest);
                    }
                } catch (Exception e) {
                    log.warn("Failed to adjust interest calculations for RECEIVABLE LOC loan {}: {}", loan.getId(), e.getMessage());
                    // Don't fail the disbursement if interest adjustment fails
                }
                
                boolean downPaymentEnabled = loan.repaymentScheduleDetail().isEnableDownPayment();
                if (loan.isInterestBearingAndInterestRecalculationEnabled() || downPaymentEnabled) {
                    createAndSaveLoanScheduleArchive(loan, scheduleGeneratorDTO);
                }
                disburseLoan(command, configurationDomainService.isPaymentTypeApplicableForDisbursementCharge(), paymentDetail, loan,
                        currentUser, changes, scheduleGeneratorDTO);

                // Compute and update Line of Credit balance upon disbursement
                try {
                    computeLocBalance(loan.getId(), disburseAmount.getAmount(), actualDisbursementDate);
                } catch (Exception e) {
                    log.warn("Failed to compute LOC balance for loan {}: {}", loan.getId(), e.getMessage());
                    // Don't fail the disbursement if LOC computation fails
                }


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

        // Get the loan before foreclosure to apply custom interest calculations
        Loan loan = loanAssembler.assembleFrom(loanId);
        
        // Apply custom interest calculations for RECEIVABLE LOC loans before foreclosure
        try {
                    String productType = getLocProductType(loanId);
        if (ProductType.RECEIVABLE.name().equals(productType)) {
                BigDecimal expectedInterest = getExpectedInterestForReceivableLoan(loanId);
                customLoanInterestCalculationService.getAdjustedInterestForForeclosure(loan, command.localDateValueOfParameterNamed("transactionDate"), productType, expectedInterest);
            }
        } catch (Exception e) {
            log.warn("Failed to adjust interest calculations for RECEIVABLE LOC loan {} during foreclosure: {}", 
                    loanId, e.getMessage());
            // Don't fail the foreclosure if interest adjustment fails
        }

        CommandProcessingResult result = super.forecloseLoan(loanId, cleanedCommand);

        if (result != null && result.getResourceId() != null && result.getResourceId() > 0L) {
            jdbcTemplate.update(
                    "UPDATE m_loan SET is_forced_closure = ?, is_restructured = ? WHERE id = ?",
                    Boolean.TRUE.equals(isForcedClosure),
                    Boolean.TRUE.equals(isRestructured),
                    loanId
            );
        }

        return result;
    }

    @Override
    @Transactional
    public CommandProcessingResult makeLoanRepayment(final LoanTransactionType repaymentTransactionType, final Long loanId,
                                                     final JsonCommand command, final boolean isRecoveryRepayment) {
        // Call the parent implementation to handle the core repayment logic
        CommandProcessingResult result = super.makeLoanRepayment(repaymentTransactionType, loanId, command, isRecoveryRepayment);

        // Adjust LOC balance on repayment (general logic for all LOC loans)
        if (result != null && result.hasChanges()) {
            try {
                // Get transaction amount from the command - try multiple parameter names
                BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed("transactionAmount");
                if (transactionAmount == null) {
                    transactionAmount = command.bigDecimalValueOfParameterNamed("amount");
                }
                if (transactionAmount == null) {
                    transactionAmount = command.bigDecimalValueOfParameterNamed("principal");
                }
                
                log.info("Repayment transaction for loan {}: transactionAmount={}, result.resourceId={}", 
                        loanId, transactionAmount, result.getResourceId());
                
                if (transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) > 0) {
                    adjustLocBalanceOnRepayment(loanId, transactionAmount);
                } else {
                    log.warn("No valid transaction amount found for loan {} repayment. Command JSON: {}", 
                            loanId, command.parsedJson());
                }
            } catch (Exception e) {
                log.warn("Failed to adjust LOC balance on repayment for loan {}: {}", loanId, e.getMessage());
                // Don't fail the repayment if LOC balance adjustment fails
            }
        }

        return result;
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
                log.debug("No Line of Credit associated with loan {}", loanId);
                return null;
            }

            // Get the advance percentage from the line of credit
            String locSql = "SELECT advance_percentage FROM m_line_of_credit WHERE id = ?";
            BigDecimal advancePercentage = jdbcTemplate.queryForObject(locSql, BigDecimal.class, lineOfCreditId);

            if (advancePercentage == null) {
                log.warn("No advance percentage set for Line of Credit {}", lineOfCreditId);
                return null;
            }

            // Calculate discounted amount: principal * (advance_percentage / 100)
            // advance_percentage is stored as a percentage (e.g., 80.00 for 80%), so divide by 100
            BigDecimal advancePercentageDecimal = advancePercentage.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            BigDecimal discountedAmount = principal.multiply(advancePercentageDecimal);
            
            log.info("Calculated discounted amount for loan {}: principal={}, advance_percentage={}, discounted_amount={}", 
                     loanId, principal, advancePercentage, discountedAmount);

            return discountedAmount;

            } catch (Exception e) {
            log.error("Failed to calculate discounted amount for loan {}: {}", loanId, e.getMessage());
            throw new PlatformApiDataValidationException("error.msg.loc.discounted.amount.calculation.failed",
                    "Failed to calculate discounted amount from Line of Credit", "loanId", loanId, e);
        }
    }

    /**
     * Calculates the expected interest based on the annual interest rate, discounted amount, and tenure days from the associated Line of Credit.
     * This method:
     * 1. Retrieves the loan's associated Line of Credit
     * 2. Gets the annual interest rate and tenure days from the LOC
     * 3. Calculates the expected interest using the formula: Annual Interest Rate * Discounted Amount * (Tenure Days / 365)
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
                log.debug("No Line of Credit associated with loan {}", loanId);
                return null;
            }

            // Get the annual interest rate and tenure days from the line of credit
            String locSql = "SELECT annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = ?";
            Map<String, Object> locData = jdbcTemplate.queryForMap(locSql, lineOfCreditId);

            BigDecimal annualInterestRate = (BigDecimal) locData.get("annual_interest_rate");
            Integer tenorDays = (Integer) locData.get("tenor_days");

            if (annualInterestRate == null) {
                log.warn("No annual interest rate set for Line of Credit {}", lineOfCreditId);
                return null;
            }

            if (tenorDays == null) {
                log.warn("No tenor days set for Line of Credit {}", lineOfCreditId);
                return null;
            }

            if (discountedAmount == null || discountedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid discounted amount for loan {}: {}", loanId, discountedAmount);
                return null;
            }

            // Calculate expected interest: Annual Interest Rate * Discounted Amount * (Tenure Days / 365)
            // annual_interest_rate is stored as a percentage (e.g., 12.00 for 12%), so divide by 100
            BigDecimal annualRateDecimal = annualInterestRate.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
            BigDecimal daysInYear = new BigDecimal("365");
            BigDecimal timeFactor = new BigDecimal(tenorDays).divide(daysInYear, 10, java.math.RoundingMode.HALF_UP);
            BigDecimal expectedInterest = annualRateDecimal.multiply(discountedAmount).multiply(timeFactor);
            
            log.info("Calculated expected interest for loan {}: annual_rate={}, discounted_amount={}, tenor_days={}, expected_interest={}", 
                     loanId, annualInterestRate, discountedAmount, tenorDays, expectedInterest);

            return expectedInterest;

        } catch (Exception e) {
            log.error("Failed to calculate expected interest for loan {}: {}", loanId, e.getMessage());
            throw new PlatformApiDataValidationException("error.msg.loc.expected.interest.calculation.failed",
                    "Failed to calculate expected interest from Line of Credit", "loanId", loanId, e);
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
                log.debug("No Line of Credit associated with loan {}", loanId);
                return null;
            }

            // Get the product type from the line of credit
            String locSql = "SELECT product_type FROM m_line_of_credit WHERE id = ?";
            String productType = jdbcTemplate.queryForObject(locSql, String.class, lineOfCreditId);

            log.debug("Retrieved product type for loan {}: {}", loanId, productType);
            return productType;

        } catch (Exception e) {
            log.error("Failed to get LOC product type for loan {}: {}", loanId, e.getMessage());
            return null;
        }
    }

    /**
     * Logs LOC calculation data for audit purposes.
     * Since all data is available in existing tables, we just log the calculations.
     * 
     * @param loanId the loan ID
     * @param discountedAmount the discounted amount (principal * advance_percentage)
     * @param expectedInterest the expected interest amount
     */
    public void logLocCalculationData(Long loanId, BigDecimal discountedAmount, BigDecimal expectedInterest) {
        try {
            // Get LOC data for logging
            String locSql = "SELECT advance_percentage, annual_interest_rate, tenor_days FROM m_line_of_credit WHERE id = " +
                           "(SELECT line_of_credit_id FROM m_loan WHERE id = ?)";
            Map<String, Object> locData = jdbcTemplate.queryForMap(locSql, loanId);
            
            BigDecimal advancePercentage = (BigDecimal) locData.get("advance_percentage");
            BigDecimal annualInterestRate = (BigDecimal) locData.get("annual_interest_rate");
            Integer tenorDays = (Integer) locData.get("tenor_days");
            
            // Calculate original principal from discounted amount and advance percentage
            BigDecimal originalPrincipal = null;
            if (discountedAmount != null && advancePercentage != null && advancePercentage.compareTo(BigDecimal.ZERO) > 0) {
                originalPrincipal = discountedAmount.divide(advancePercentage, 6, java.math.RoundingMode.HALF_UP);
            }
            
            log.info("LOC calculation data for loan {}: original_principal={}, discounted_amount={}, " +
                     "expected_interest={}, advance_percentage={}, annual_interest_rate={}, tenor_days={}", 
                     loanId, originalPrincipal, discountedAmount, expectedInterest, 
                     advancePercentage, annualInterestRate, tenorDays);

        } catch (Exception e) {
            log.error("Failed to log LOC calculation data for loan {}: {}", loanId, e.getMessage());
            // Don't throw exception as this is supplementary logging
        }
    }




    /**
     * Calculates the net disbursed amount by subtracting the expected interest from the discounted amount.
     * This method:
     * 1. Calculates the discounted amount (principal * advance_percentage)
     * 2. Calculates the expected interest (annual_rate * discounted_amount * (tenor_days / 365))
     * 3. Returns the net disbursed amount (discounted_amount - expected_interest)
     *
     * @param loanId   the loan ID
     * @param principal the principal amount
     * @return the net disbursed amount, or null if calculation cannot be performed
     */
    public BigDecimal calculateNetDisbursedAmount(Long loanId, BigDecimal principal) {
        try {
            // First calculate the discounted amount
            BigDecimal discountedAmount = calculateDiscountedAmount(loanId, principal);
            if (discountedAmount == null) {
                log.debug("Cannot calculate net disbursed amount - discounted amount is null for loan {}", loanId);
                return null;
            }

            // Then calculate the expected interest
            BigDecimal expectedInterest = calculateExpectedInterest(loanId, discountedAmount);
            if (expectedInterest == null) {
                log.debug("Cannot calculate net disbursed amount - expected interest is null for loan {}", loanId);
                return null;
            }

            // Calculate net disbursed amount: Discounted Amount - Expected Interest
            BigDecimal netDisbursedAmount = discountedAmount.subtract(expectedInterest);
            
            log.info("Calculated net disbursed amount for loan {}: discounted_amount={}, expected_interest={}, net_disbursed_amount={}", 
                     loanId, discountedAmount, expectedInterest, netDisbursedAmount);

            return netDisbursedAmount;

        } catch (Exception e) {
            log.error("Failed to calculate net disbursed amount for loan {}: {}", loanId, e.getMessage());
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
     * @param disbursementAmount the disbursement amount
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
                log.error("Failed to update LOC balances for line of credit {}", lineOfCreditId);
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
     * @param loanId The ID of the loan
     * @param repaymentAmount The amount being repaid
     * @return true if adjustment was successful, false otherwise
     */
    @Transactional
    public boolean adjustLocBalanceOnRepayment(Long loanId, BigDecimal repaymentAmount) {
        try {
            log.debug("Adjusting LOC balance on repayment for loan {} with amount {}", loanId, repaymentAmount);
            
            // Validate repayment amount
            if (repaymentAmount == null || repaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid repayment amount for loan {}: {}", loanId, repaymentAmount);
                return false;
            }
            
            // Get LOC ID for the loan
            Long lineOfCreditId = jdbcTemplate.queryForObject(
                "SELECT line_of_credit_id FROM m_loan WHERE id = ?",
                Long.class,
                loanId
            );
            
            if (lineOfCreditId == null) {
                log.debug("No LOC associated with loan {}, skipping balance adjustment", loanId);
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
                log.warn("Repayment amount {} exceeds consumed amount {} for LOC {}. Setting consumed amount to zero.", 
                    repaymentAmount, currentConsumedAmount, lineOfCreditId);
                newConsumedAmount = BigDecimal.ZERO;
                // Adjust available balance accordingly
                newAvailableBalance = currentAvailableBalance.add(currentConsumedAmount);
            }
            
            // Update LOC balances
            int updateCount = jdbcTemplate.update(
                "UPDATE m_line_of_credit SET consumed_amount = ?, available_balance = ?, last_modified_date = NOW() WHERE id = ?",
                newConsumedAmount,
                newAvailableBalance,
                lineOfCreditId
            );
            
            if (updateCount == 0) {
                log.error("Failed to update LOC balances for line of credit {}", lineOfCreditId);
                return false;
            }
            
            // Create transaction record
            jdbcTemplate.update(
                "INSERT INTO m_line_of_credit_transactions " +
                "(line_of_credit_id, transaction_type, amount, balance_after_transaction, " +
                "transaction_date, description, created_date) " +
                "VALUES (?, 'REPAYMENT', ?, ?, NOW(), ?, NOW())",
                lineOfCreditId,
                repaymentAmount,
                newAvailableBalance,
                String.format("Loan repayment adjustment - Loan ID: %d, Amount: %s", loanId, repaymentAmount)
            );
            
            log.info("Successfully adjusted LOC balances for loan {}: repayment_amount={}, old_consumed={}, new_consumed={}, old_available={}, new_available={}", 
                loanId, repaymentAmount, currentConsumedAmount, newConsumedAmount, currentAvailableBalance, newAvailableBalance);
            
            // Additional debugging: Check if this was a full repayment
            if (newConsumedAmount.compareTo(BigDecimal.ZERO) == 0) {
                log.info("Full repayment detected for loan {} - LOC consumed amount is now zero", loanId);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to adjust LOC balance on repayment for loan {}: {}", loanId, e.getMessage());
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
            
            if (!ProductType.RECEIVABLE.name().equals(productType)) {
                return null; // Only applicable for RECEIVABLE loans
            }
            
            // Get the approved amount from the loan (this is the discounted amount for RECEIVABLE loans)
            BigDecimal approvedAmount = jdbcTemplate.queryForObject(
                "SELECT approved_principal FROM m_loan WHERE id = ?",
                BigDecimal.class,
                loanId
            );
            
            if (approvedAmount == null) {
                log.warn("No approved amount found for loan {}", loanId);
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
            if (ProductType.RECEIVABLE.name().equals(productType)) {
                BigDecimal expectedInterest = getExpectedInterestForReceivableLoan(updatedLoan.getId());
                customLoanInterestCalculationService.adjustInterestCalculationsForReceivableLoan(updatedLoan, productType, expectedInterest);
            }
        } catch (Exception e) {
            log.warn("Failed to adjust interest calculations for RECEIVABLE LOC loan {} during recalculation: {}", 
                    updatedLoan.getId(), e.getMessage());
        }
        
        return updatedLoan;
    }

}
