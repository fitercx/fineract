package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.infrastructure.commands.utils.LoanTransactionInstallmentUtils;
import com.crediblex.fineract.portfolio.account.data.CustomAccountTransferDTO;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionType;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditBalanceUpdateService;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.cob.service.LoanAccountLockService;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
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
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.teller.data.CashierTransactionDataValidator;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.data.PortfolioAccountData;
import org.apache.fineract.portfolio.account.domain.AccountAssociationsRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferType;
import org.apache.fineract.portfolio.account.domain.StandingInstructionRepository;
import org.apache.fineract.portfolio.account.domain.StandingInstructionStatus;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarRepository;
import org.apache.fineract.portfolio.collectionsheet.command.CollectionSheetBulkDisbursalCommand;
import org.apache.fineract.portfolio.collectionsheet.command.SingleDisbursalCommand;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.GLIMAccountInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallmentRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRelationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
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
import org.apache.fineract.useradministration.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class CustomLoanWritePlatformServiceJpaRepositoryImpl extends LoanWritePlatformServiceJpaRepositoryImpl {

    private static final Logger log = LoggerFactory.getLogger(CustomLoanWritePlatformServiceJpaRepositoryImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;
    private final LineOfCreditBalanceUpdateService lineOfCreditBalanceUpdateService;
    private final StandingInstructionRepository standingInstructionRepository;

    public CustomLoanWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context,
            LoanTransactionValidator loanTransactionValidator,
            LoanUpdateCommandFromApiJsonDeserializer loanUpdateCommandFromApiJsonDeserializer, LoanRepositoryWrapper loanRepositoryWrapper,
            LoanAccountDomainService loanAccountDomainService, NoteRepository noteRepository,
            LoanTransactionRepository loanTransactionRepository, LoanTransactionRelationRepository loanTransactionRelationRepository,
            LoanAssembler loanAssembler, JournalEntryWritePlatformService journalEntryWritePlatformService,
            CalendarInstanceRepository calendarInstanceRepository, PaymentDetailWritePlatformService paymentDetailWritePlatformService,
            HolidayRepositoryWrapper holidayRepository, ConfigurationDomainService configurationDomainService,
            WorkingDaysRepositoryWrapper workingDaysRepository, AccountTransfersWritePlatformService accountTransfersWritePlatformService,
            AccountTransfersReadPlatformService accountTransfersReadPlatformService,
            AccountAssociationsReadPlatformService accountAssociationsReadPlatformService, LoanReadPlatformService loanReadPlatformService,
            FromJsonHelper fromApiJsonHelper, CalendarRepository calendarRepository,
            LoanScheduleHistoryWritePlatformService loanScheduleHistoryWritePlatformService,
            LoanApplicationValidator loanApplicationValidator, AccountAssociationsRepository accountAssociationRepository,
            AccountTransferDetailRepository accountTransferDetailRepository, BusinessEventNotifierService businessEventNotifierService,
            GuarantorDomainService guarantorDomainService, LoanUtilService loanUtilService,
            EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService,
            CodeValueRepositoryWrapper codeValueRepository, CashierTransactionDataValidator cashierTransactionDataValidator,
            GLIMAccountInfoRepository glimRepository, LoanRepository loanRepository,
            RepaymentWithPostDatedChecksAssembler repaymentWithPostDatedChecksAssembler,
            PostDatedChecksRepository postDatedChecksRepository,
            LoanRepaymentScheduleInstallmentRepository loanRepaymentScheduleInstallmentRepository,
            LoanLifecycleStateMachine loanLifecycleStateMachine, LoanAccountLockService loanAccountLockService,
            ExternalIdFactory externalIdFactory, LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService,
            ErrorHandler errorHandler, LoanDownPaymentHandlerService loanDownPaymentHandlerService,
            LoanTransactionAssembler loanTransactionAssembler, LoanAccrualsProcessingService loanAccrualsProcessingService,
            LoanOfficerValidator loanOfficerValidator, LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator,
            LoanDisbursementService loanDisbursementService, LoanScheduleService loanScheduleService,
            LoanChargeValidator loanChargeValidator, LoanOfficerService loanOfficerService,
            ReprocessLoanTransactionsService reprocessLoanTransactionsService, LoanAccountService loanAccountService,
            LoanJournalEntryPoster journalEntryPoster, LoanAdjustmentService loanAdjustmentService,
            LoanAccountingBridgeMapper loanAccountingBridgeMapper, LoanMapper loanMapper,
            LoanTransactionProcessingService loanTransactionProcessingService, FineractProperties fineractProperties,
            JdbcTemplate jdbcTemplate, LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository,
            LineOfCreditBalanceUpdateService lineOfCreditBalanceUpdateService,
            StandingInstructionRepository standingInstructionRepository) {
        super(context, loanTransactionValidator, loanUpdateCommandFromApiJsonDeserializer, loanRepositoryWrapper, loanAccountDomainService,
                noteRepository, loanTransactionRepository, loanTransactionRelationRepository, loanAssembler,
                journalEntryWritePlatformService, calendarInstanceRepository, paymentDetailWritePlatformService, holidayRepository,
                configurationDomainService, workingDaysRepository, accountTransfersWritePlatformService,
                accountTransfersReadPlatformService, accountAssociationsReadPlatformService, loanReadPlatformService, fromApiJsonHelper,
                calendarRepository, loanScheduleHistoryWritePlatformService, loanApplicationValidator, accountAssociationRepository,
                accountTransferDetailRepository, businessEventNotifierService, guarantorDomainService, loanUtilService,
                entityDatatableChecksWritePlatformService, codeValueRepository, cashierTransactionDataValidator, glimRepository,
                loanRepository, repaymentWithPostDatedChecksAssembler, postDatedChecksRepository,
                loanRepaymentScheduleInstallmentRepository, loanLifecycleStateMachine, loanAccountLockService, externalIdFactory,
                loanAccrualTransactionBusinessEventService, errorHandler, loanDownPaymentHandlerService, loanTransactionAssembler,
                loanAccrualsProcessingService, loanOfficerValidator, loanDownPaymentTransactionValidator, loanDisbursementService,
                loanScheduleService, loanChargeValidator, loanOfficerService, reprocessLoanTransactionsService, loanAccountService,
                journalEntryPoster, loanAdjustmentService, loanAccountingBridgeMapper, loanMapper, loanTransactionProcessingService,
                fineractProperties);
        this.jdbcTemplate = jdbcTemplate;
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
        this.lineOfCreditBalanceUpdateService = lineOfCreditBalanceUpdateService;
        this.standingInstructionRepository = standingInstructionRepository;
    }

    @Transactional
    @Override
    public CommandProcessingResult disburseLoan(final Long loanId, final JsonCommand command, Boolean isAccountTransfer,
            Boolean isWithoutAutoPayment) {
        loanTransactionValidator.validateDisbursement(command, isAccountTransfer, loanId);

        Loan loan = loanAssembler.assembleFrom(loanId);
        final MonetaryCurrency currency = loan.getCurrency();
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

        boolean isReceivableLineOfCredit = false;
        Optional<LoanLineOfCreditParams> lineOfCreditParams = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
        if (lineOfCreditParams.isPresent() && lineOfCreditParams.get().getLineOfCredit() != null
                && lineOfCreditParams.get().getLineOfCredit().getProductType().isReceivable()) {
            isReceivableLineOfCredit = true;
        }

        if (loan.canDisburse()) {
            // Get netDisbursalAmount from disbursal screen field.
            final BigDecimal netDisbursalAmount = command
                    .bigDecimalValueOfParameterNamed(LoanApiConstants.disbursementNetDisbursalAmountParameterName);
            if (netDisbursalAmount != null) {
                log.info("Setting net disbursement amount from command parameter: {} for loan {}", netDisbursalAmount, loanId);
                loan.setNetDisbursalAmount(netDisbursalAmount);
            }

            Money disburseAmount = loanDisbursementService.adjustDisburseAmount(loan, command, actualDisbursementDate);

            Money amountToDisburse = disburseAmount; // Use the calculated amount directly

            // --- BEGIN Receivable LOC custom capping logic (replaces summary-based approach) ---
            if (isReceivableLineOfCredit) {
                LoanLineOfCreditParams locParams = lineOfCreditParams.get();
                BigDecimal amountAfterAdvance = locParams.getAmountAfterAdvance();
                if (amountAfterAdvance != null) {
                    BigDecimal feesDueAtDisbursement = loan.getActiveCharges().stream().filter(LoanCharge::isDueAtDisbursement) // due
                                                                                                                                // at
                                                                                                                                // disbursement
                            .filter(LoanCharge::isChargePending) // not yet paid
                            .map(LoanCharge::amountOutstanding) // outstanding amount
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Compute scheduled interest (pre-disbursement schedule already exists). If schedule empty,
                    // interest=0.
                    BigDecimal scheduledInterest = loan.getRepaymentScheduleInstallments().stream()
                            .map(i -> i.getInterestCharged(currency).getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal expectedDisbursementAmount = amountAfterAdvance.subtract(scheduledInterest).subtract(feesDueAtDisbursement);

                    if (expectedDisbursementAmount.compareTo(BigDecimal.ZERO) < 0) {
                        throw new PlatformDataIntegrityException("amount.after.advance.too.low",
                                "The value of Amount After Advance (" + amountAfterAdvance
                                        + ") is too low to cover the fees at disbursement (" + feesDueAtDisbursement
                                        + ") and scheduled interest (" + scheduledInterest + "). Increase Amount After Advance value.",
                                "amountAfterAdvance", amountAfterAdvance);
                    }

                    Money expectedMoney = Money.of(loan.getCurrency(), expectedDisbursementAmount);

                    if (amountToDisburse.isGreaterThan(expectedMoney)) {
                        amountToDisburse = expectedMoney;
                    }
                } else {
                    log.debug("Receivable LOC has null amountAfterAdvance for loan {} – skipping capping logic", loan.getId());
                }
            }
            // --- END Receivable LOC custom capping logic ---

            boolean recalculateSchedule = amountBeforeAdjust.isNotEqualTo(loan.getPrincipal());
            final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

            if (loan.isTopup() && !isReceivableLineOfCredit && loan.getClientId() != null) {
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
                // Ensure we use the correct amount for LOC loans in disbursement transaction
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

            regenerateScheduleOnDisbursement(command, loan, recalculateSchedule, scheduleGeneratorDTO, nextPossibleRepaymentDate,
                    rescheduledRepaymentDate);

            boolean downPaymentEnabled = loan.repaymentScheduleDetail().isEnableDownPayment();
            if (loan.isInterestBearingAndInterestRecalculationEnabled() || downPaymentEnabled) {
                createAndSaveLoanScheduleArchive(loan, scheduleGeneratorDTO);
            }
            disburseLoan(command, isPaymentTypeApplicableForDisbursementCharge, paymentDetail, loan, currentUser, changes,
                    scheduleGeneratorDTO);

            if (!isReceivableLineOfCredit) {
                loan.adjustNetDisbursalAmount(amountToDisburse.getAmount());
            }

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
            // auto create standing instruction only if one doesn't already exist
            if (!standingInstructionExists(loanId)) {
                createStandingInstruction(loan);
            }
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

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(loan.getId())
                .withEntityExternalId(loan.getExternalId()).withSubEntityId(disbursalTransactionId)
                .withSubEntityExternalId(disbursalTransactionExternalId).withOfficeId(loan.getOfficeId()).withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId()).withLoanId(loanId).with(changes).build();
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
                boolean downPaymentEnabled = loan.repaymentScheduleDetail().isEnableDownPayment();
                if (loan.isInterestBearingAndInterestRecalculationEnabled() || downPaymentEnabled) {
                    createAndSaveLoanScheduleArchive(loan, scheduleGeneratorDTO);
                }
                disburseLoan(command, configurationDomainService.isPaymentTypeApplicableForDisbursementCharge(), paymentDetail, loan,
                        currentUser, changes, scheduleGeneratorDTO);

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
        final CustomAccountTransferDTO accountTransferDTO = new CustomAccountTransferDTO(transactionDate, amount.getAmount(),
                PortfolioAccountType.LOAN, PortfolioAccountType.SAVINGS, loan.getId(), portfolioAccountData.getId(), "Loan Disbursement",
                locale, fmt, paymentDetail, LoanTransactionType.DISBURSEMENT.getValue(), null, null, null,
                AccountTransferType.ACCOUNT_TRANSFER.getValue(), null, null, txnExternalId, loan, null, fromSavingsAccount,
                isRegularTransaction, isExceptionForBalanceCheck);
        accountTransferDTO.setNetLoanDisbursementAmount(loan.getNetDisbursalAmount());
        this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
    }

    @Override
    @Transactional
    public CommandProcessingResult forecloseLoan(Long loanId, JsonCommand command) {

        final Boolean isForcedClosure = command.booleanObjectValueOfParameterNamed("isForcedClosure");
        final Boolean isRestructured = command.booleanObjectValueOfParameterNamed("isRestructured");

        JsonElement parsedJson = command.parsedJson();
        if (parsedJson != null && parsedJson.isJsonObject()) {
            parsedJson.getAsJsonObject().remove("isForcedClosure");
            parsedJson.getAsJsonObject().remove("isRestructured");
        }

        JsonCommand cleanedCommand = JsonCommand.fromExistingCommand(command, parsedJson, null);

        CommandProcessingResult result = super.forecloseLoan(loanId, cleanedCommand);

        if (result != null && result.getResourceId() != null && result.getResourceId() > 0L) {

            final BigDecimal amount = result.getChanges().get("eventAmount") != null
                    ? new BigDecimal(result.getChanges().get("eventAmount").toString())
                    : BigDecimal.ZERO;
            final LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");

            updateLocBalance(loanId, amount, transactionDate, LineOfCreditTransactionType.FORECLOSURE, null);

            // TODO Look at loc foreclosure
            jdbcTemplate.update("UPDATE m_loan SET is_forced_closure = ?, is_restructured = ? WHERE id = ?",
                    Boolean.TRUE.equals(isForcedClosure), Boolean.TRUE.equals(isRestructured), loanId);
        }

        return result;
    }

    @Override
    @Transactional
    public CommandProcessingResult makeLoanRepayment(final LoanTransactionType repaymentTransactionType, final Long loanId,
            final JsonCommand command, final boolean isRecoveryRepayment) {
        // Call the parent implementation to handle the core repayment logic
        CommandProcessingResult result = super.makeLoanRepayment(repaymentTransactionType, loanId, command, isRecoveryRepayment);

        // Use resourceId instead of subResourceId since parent puts transaction ID in entityId (resourceId)
        if (result != null && result.hasChanges() && result.getResourceId() != null) {
            try {
                // Fetch the updated loan to get the loan transaction
                Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);

                BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed("transactionAmount");

                // Find the specific transaction that was just created using the resourceId (transaction ID)
                LoanTransaction transaction = loan.getLoanTransaction(t -> t.getId().equals(result.getResourceId()));

                updateLocBalance(loanId, transactionAmount, command.localDateValueOfParameterNamed("transactionDate"),
                        LineOfCreditTransactionType.REPAYMENT, transaction);

                if (transaction != null && transaction.getLoanTransactionToRepaymentScheduleMappings() != null) {
                    // Extract affected installments using the shared utility method
                    List<Map<String, Object>> affectedInstallments = LoanTransactionInstallmentUtils.extractAffectedInstallments(loan,
                            transaction);

                    LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");

                    // Add the affected installments to the result for webhook payload
                    Map<String, Object> additionalChanges = new HashMap<>();
                    if (result.getChanges() != null) {
                        additionalChanges.putAll(result.getChanges());
                    }

                    // Add affected installments and transaction details to the changes
                    additionalChanges.put("affectedInstallments", affectedInstallments);
                    additionalChanges.put("transactionAmount", transactionAmount);
                    additionalChanges.put("transactionDate", transactionDate);
                    additionalChanges.put("transactionId", result.getResourceId()); // Use resourceId as transaction ID

                    // Create a new result with the additional schedule information
                    return new CommandProcessingResultBuilder().withCommandId(result.getCommandId()).withEntityId(result.getResourceId())
                            .withEntityExternalId(result.getResourceExternalId()).withSubEntityId(result.getSubResourceId())
                            .withSubEntityExternalId(result.getSubResourceExternalId()).withOfficeId(result.getOfficeId())
                            .withClientId(result.getClientId()).withGroupId(result.getGroupId()).withLoanId(result.getLoanId())
                            .withLoanExternalId(result.getLoanExternalId()).with(additionalChanges).build();
                }

            } catch (Exception e) {
                // Log the error but don't fail the repayment transaction
                log.warn("Failed to fetch affected installments for webhook payload: {}", e.getMessage());
                return result;
            }
        }

        return result;
    }

    /**
     * Override the parent's undoLoanDisbursal method to include LOC balance adjustments
     */
    @Override
    @Transactional
    public CommandProcessingResult undoLoanDisbursal(final Long loanId, final JsonCommand command) {
        // Call the parent method to perform the standard undo disbursal
        CommandProcessingResult result = super.undoLoanDisbursal(loanId, command);

        if (result != null && result.hasChanges() && result.getResourceId() != null) {
            // Fetch the loan to get disbursal amount
            Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);
            BigDecimal disbursalAmount = loan.getNetDisbursalAmount();

            // Get the transaction date from the command or use current date
            LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");
            if (transactionDate == null) {
                transactionDate = DateUtils.getBusinessLocalDate();
            }

            updateLocBalance(loanId, disbursalAmount, transactionDate, LineOfCreditTransactionType.UNDO_DISBURSEMENT, null);

        }
        return result;
    }

    /**
     * Override the parent's adjustLoanTransaction method to include LOC balance adjustments when undoing/reversing loan
     * transactions (especially repayments)
     */
    @Override
    @Transactional
    public CommandProcessingResult adjustLoanTransaction(final Long loanId, final Long transactionId, final JsonCommand command) {
        // Get the transaction details before calling parent method
        LoanTransaction transactionToAdjust = this.loanTransactionRepository.findByIdAndLoanId(transactionId, loanId)
                .orElseThrow(() -> new LoanTransactionNotFoundException(transactionId, loanId));

        // Check if this is a repayment transaction being reversed
        boolean isRepaymentReversal = transactionToAdjust.isRepayment()
                && command.bigDecimalValueOfParameterNamed("transactionAmount").compareTo(BigDecimal.ZERO) == 0;

        BigDecimal repaymentAmount = null;
        if (isRepaymentReversal) {
            repaymentAmount = transactionToAdjust.getAmount();
        }

        // Call the parent method to perform the standard transaction adjustment
        CommandProcessingResult result = super.adjustLoanTransaction(loanId, transactionId, command);

        // If this was a repayment reversal and we have a repayment amount, adjust LOC balances
        if (isRepaymentReversal && repaymentAmount != null && repaymentAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Get the transaction date from the command or use current date
            LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");
            if (transactionDate == null) {
                transactionDate = DateUtils.getBusinessLocalDate();
            }

            // Reverse the LOC balance adjustments (opposite of what happens during repayment)
            updateLocBalance(loanId, repaymentAmount, transactionDate, LineOfCreditTransactionType.REVERSAL, null);

        }

        return result;
    }

    private void updateLocBalance(Long loanId, BigDecimal amount, LocalDate transactionDate, LineOfCreditTransactionType transactionType,
            LoanTransaction loanTransaction) {

        Optional<LoanLineOfCreditParams> locProductTypeOpt = loanLineOfCreditParamsRepository.findByLoanId(loanId);

        if (transactionType.isRepayment() || transactionType.isReversal() || transactionType.isRefund()) {
            if (locProductTypeOpt.isPresent() && !locProductTypeOpt.get().getLineOfCredit().getProductType().isReceivable()) {
                amount = loanTransaction.getPrincipalPortion();
            }
        }

        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0 && locProductTypeOpt.isPresent()) {
            // For repayments, we reduce the LOC balance (i.e. free up credit)
            lineOfCreditBalanceUpdateService.computeLocBalance(loanId, amount, locProductTypeOpt.get().getLineOfCredit(), transactionDate,
                    transactionType);
        }
    }

    private boolean standingInstructionExists(Long loanId) {
        return this.standingInstructionRepository.existsByAccountTransferDetails_ToLoanAccount_IdAndStatus(loanId,
                StandingInstructionStatus.ACTIVE.getValue());
    }
}
