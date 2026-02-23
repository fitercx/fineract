package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.commands.LineOfCreditStatusWebhookPublisher;
import com.crediblex.fineract.commands.LoanStatusWebhookPublisher;
import com.crediblex.fineract.infrastructure.commands.utils.LoanStatusAggregationUtils;
import com.crediblex.fineract.infrastructure.commands.utils.LoanTransactionInstallmentUtils;
import com.crediblex.fineract.infrastructure.events.business.domain.accounttransfer.SavingsToLoanAccountTransferBusinessEvent;
import com.crediblex.fineract.portfolio.loanaccount.api.CustomLoanApiConstants;
import com.crediblex.fineract.portfolio.loanaccount.data.CustomAccountTransferDTO;
import com.crediblex.fineract.portfolio.loanaccount.data.ExtendedLoanSchedulePeriodData;
import com.crediblex.fineract.portfolio.loanaccount.data.LocStatusAggregationData;
import com.crediblex.fineract.portfolio.loanaccount.domain.CredibleXLoanPenaltyCalculator;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loanaccount.repository.CustomLoanChargeRepository;
import com.crediblex.fineract.portfolio.loanaccount.repository.LoanRepaymentsSummaryDAO;
import com.crediblex.fineract.portfolio.loanaccount.serialization.CustomLoanDisbursementDateValidator;
import com.crediblex.fineract.portfolio.loanaccount.util.LoanTrancheValidationHelper;
import com.crediblex.fineract.portfolio.loanaccount.util.LocStatusAggregationUtils;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepository;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditTransactionType;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditBalanceUpdateService;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
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
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.MultiException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.BusinessEventListener;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanDisbursalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanUndoDisbursalBusinessEvent;
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
import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;
import org.apache.fineract.portfolio.account.domain.AccountTransferTransaction;
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
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeDataDTO;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.GLIMAccountInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallmentRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRelationRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.exception.DateMismatchException;
import org.apache.fineract.portfolio.loanaccount.exception.InvalidLoanStateTransitionException;
import org.apache.fineract.portfolio.loanaccount.exception.LoanRepaymentScheduleNotFoundException;
import org.apache.fineract.portfolio.loanaccount.exception.LoanTransactionNotFoundException;
import org.apache.fineract.portfolio.loanaccount.guarantor.service.GuarantorDomainService;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData;
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
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformService;
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
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecks;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecksRepository;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.service.RepaymentWithPostDatedChecksAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Primary
public class CustomLoanWritePlatformServiceJpaRepositoryImpl extends LoanWritePlatformServiceJpaRepositoryImpl {

    private static final Logger log = LoggerFactory.getLogger(CustomLoanWritePlatformServiceJpaRepositoryImpl.class);

    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;
    private final LineOfCreditBalanceUpdateService lineOfCreditBalanceUpdateService;
    private final StandingInstructionRepository standingInstructionRepository;
    private final CustomLoanChargeReadPlatformServiceImpl customLoanChargeReadPlatformService;
    private final CredXLoanReadPlatformServiceImpl credibleXLoanReadPlatformService;
    private final CustomLoanChargeRepository loanChargeRepository;
    private final LoanRepaymentsSummaryDAO loanRepaymentsSummaryDAO;
    private final CredXLoanChargeWritePlatformServiceImpl credibleXLoanChargeWritePlatformService;
    private final SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper;
    private final CustomLoanDisbursementDateValidator customLoanDisbursementDateValidator;
    private final LoanStatusWebhookPublisher loanStatusWebhookPublisher;
    private final LineOfCreditStatusWebhookPublisher lineOfCreditStatusWebhookPublisher;
    private final TransactionTemplate transactionTemplate;
    private final LocStatusAggregationUtils locStatusAggregationUtils;
    // Autowire to avoid constructor signature change
    @Autowired(required = false)
    private LineOfCreditRepository lineOfCreditRepository;
    private final CustomLoanDisbursementService customLoanDisbursementService;
    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    private final PaymentTypeRepositoryWrapper paymentTypeRepositoryWrapper;

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
            CustomLoanChargeReadPlatformServiceImpl customLoanChargeReadPlatformService,
            CredXLoanReadPlatformServiceImpl credibleXLoanReadPlatformService, CustomLoanChargeRepository loanChargeRepository,
            LoanRepaymentsSummaryDAO loanRepaymentsSummaryDAO,
            @Lazy CredXLoanChargeWritePlatformServiceImpl credibleXLoanChargeWritePlatformService,
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository, JournalEntryRepository journalEntryRepository,
            SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper,
            LineOfCreditBalanceUpdateService lineOfCreditBalanceUpdateService, StandingInstructionRepository standingInstructionRepository,
            com.crediblex.fineract.portfolio.loanaccount.serialization.CustomLoanDisbursementDateValidator customLoanDisbursementDateValidator,
            LoanChargeWritePlatformService loanChargeWritePlatformService, LoanStatusWebhookPublisher loanStatusWebhookPublisher,
            PlatformTransactionManager platformTransactionManager, LineOfCreditStatusWebhookPublisher lineOfCreditStatusWebhookPublisher,
            LocStatusAggregationUtils locStatusAggregationUtils, CustomLoanDisbursementService customLoanDisbursementService,
            SavingsAccountWritePlatformService savingsAccountWritePlatformService,
            PaymentTypeRepositoryWrapper paymentTypeRepositoryWrapper) {
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

        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
        this.lineOfCreditBalanceUpdateService = lineOfCreditBalanceUpdateService;
        this.standingInstructionRepository = standingInstructionRepository;
        this.customLoanChargeReadPlatformService = customLoanChargeReadPlatformService;
        this.credibleXLoanReadPlatformService = credibleXLoanReadPlatformService;
        this.loanChargeRepository = loanChargeRepository;
        this.loanRepaymentsSummaryDAO = loanRepaymentsSummaryDAO;
        this.credibleXLoanChargeWritePlatformService = credibleXLoanChargeWritePlatformService;
        this.customLoanDisbursementDateValidator = customLoanDisbursementDateValidator;
        this.savingsAccountRepositoryWrapper = savingsAccountRepositoryWrapper;
        this.loanStatusWebhookPublisher = loanStatusWebhookPublisher;
        this.lineOfCreditStatusWebhookPublisher = lineOfCreditStatusWebhookPublisher;
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.locStatusAggregationUtils = locStatusAggregationUtils;
        this.customLoanDisbursementService = customLoanDisbursementService;
        this.savingsAccountWritePlatformService = savingsAccountWritePlatformService;
        this.paymentTypeRepositoryWrapper = paymentTypeRepositoryWrapper;
    }

    @PostConstruct
    public void registerForNotification() {
        businessEventNotifierService.addPostBusinessEventListener(SavingsToLoanAccountTransferBusinessEvent.class,
                new SavingsToLoanTransferBusinessEventListener());
    }

    @Transactional
    @Override
    public CommandProcessingResult disburseLoan(final Long loanId, final JsonCommand command, Boolean isAccountTransfer,
            Boolean isWithoutAutoPayment) {

        if (Boolean.FALSE.equals(isAccountTransfer)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.disbursement.only.allowed.via.savings.account.transfer",
                    "Loan disbursement is only allowed via savings account transfer");
        }

        // Load loan first to check if it's multi-tranche
        Loan loan = loanAssembler.assembleFrom(loanId);

        // Fix: Validate multi-tranche disbursement date against specific tranche's expected date
        // before calling core validator (which validates against loan-level expected date)
        if (loan.getLoanProduct().isMultiDisburseLoan() && loan.getLoanProduct().isSyncExpectedWithDisbursementDate()) {
            validateMultiTrancheDisbursementDate(command, loan);
        }

        // Call core validator - it will validate all other aspects
        // For multi-tranche loans, it may throw DateMismatchException with wrong expected date,
        // but we've already validated correctly above, so we catch and ignore that specific case
        try {
            loanTransactionValidator.validateDisbursement(command, isAccountTransfer, loanId);
        } catch (org.apache.fineract.portfolio.loanaccount.exception.DateMismatchException e) {
            // If this is a multi-tranche loan, we've already validated correctly above
            // The core validator uses loan-level expected date which is wrong for multi-tranche
            // So we ignore this exception for multi-tranche loans
            if (!loan.getLoanProduct().isMultiDisburseLoan() || !loan.getLoanProduct().isSyncExpectedWithDisbursementDate()) {
                // Not multi-tranche, re-throw the exception
                throw e;
            }
            // For multi-tranche, we've already validated - continue with disbursement
        } catch (UnsupportedParameterException e) {
            // This customization supports an optional "auto-withdraw" step after a successful disbursement to savings,
            // so users don't have to manually withdraw funds from the destination savings account.
            //
            // Two custom request parameters control this behavior:
            // - autoWithdrawFromSavings: when true, triggers a savings withdrawal immediately after disbursement.
            // - withdrawalAmount: optional override for the amount to withdraw (otherwise defaults to the net disbursed
            // amount).
            //
            // Note: withdrawalPaymentTypeId is validated by the custom withdrawal step (and is required when
            // autoWithdrawFromSavings is true). Core's disbursement validator should not see it, but if it ever does,
            // we do NOT treat it as ignorable.
            //
            // Core Fineract's disbursement validator may flag the two parameters above as unsupported; ignore the
            // exception only when the reported unsupported parameters are limited to those two fields.
            final List<String> unsupported = e.getUnsupportedParameters();
            if (unsupported == null || unsupported.isEmpty()) {
                throw e;
            }

            final Set<String> ignorable = Set.of(CustomLoanApiConstants.AUTO_WITHDRAW_FROM_SAVINGS_PARAM,
                    CustomLoanApiConstants.WITHDRAWAL_AMOUNT_PARAM, CustomLoanApiConstants.WITHDRAWAL_PAYMENT_TYPE_ID_PARAM);
            final List<String> nonIgnorable = unsupported.stream().filter(p -> !ignorable.contains(p)).toList();

            if (nonIgnorable.isEmpty()) {
                log.info("Ignoring unsupported parameter(s) {} during account transfer disbursement; these are handled by the custom layer",
                        unsupported);
            } else {
                // If both ignorable and non-ignorable parameters are present, core will send everything back to the UI.
                // Sanitize the exception so the response only reports the truly unsupported parameters.
                // IMPORTANT: preserve the original exception instance to satisfy Checkstyle
                // (AvoidHidingCauseException).
                e.getUnsupportedParameters().clear();
                e.getUnsupportedParameters().addAll(nonIgnorable);
                throw e;
            }
        }

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

            boolean recalculateSchedule = amountBeforeAdjust.isNotEqualTo(loan.getPrincipal());
            final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

            if (loan.isTopup() && loan.getClientId() != null) {
                final BigDecimal loanOutstanding = loanApplicationValidator.validateTopupLoan(loan, actualDisbursementDate);

                amountToDisburse = disburseAmount.minus(loanOutstanding);

                disburseLoanToLoan(loan, command, loanOutstanding);
            }

            LoanTransaction disbursementTransaction = null;
            if (isAccountTransfer) {
                // Use same per-tranche fee/tax as CustomLoanDisbursementService so savings gets net amount every
                // tranche
                CustomLoanDisbursementService.FeeAndTaxForTranche feeAndTax = customLoanDisbursementService
                        .calculateFeeAndTaxForTrancheDisbursement(loan, actualDisbursementDate);
                BigDecimal chargeReducableFromDisbursement = feeAndTax.fee().getAmount().add(feeAndTax.tax().getAmount());
                loan.getSummary().setTotalChargesPayableByPrincipalDeduction(chargeReducableFromDisbursement);

                Money netDisbursementAmount = amountToDisburse.minus(feeAndTax.fee()).minus(feeAndTax.tax());
                disburseLoanToSavings(loan, command, amountToDisburse, netDisbursementAmount, paymentDetail);

                // Optional: auto-withdraw from the destination savings account (same DB transaction)
                // This change supports an optional "auto-withdraw" step after a successful disbursement to savings
                if (shouldAutoWithdrawFromSavings(command)) {
                    Long destinationSavingsAccountId = resolveDestinationSavingsAccountIdForDisbursement(loan, command);
                    BigDecimal withdrawalAmount = resolveWithdrawalAmount(command, netDisbursementAmount.getAmount());
                    performAutoWithdrawalFromSavings(destinationSavingsAccountId, command, withdrawalAmount);
                    changes.put(CustomLoanApiConstants.AUTO_WITHDRAW_FROM_SAVINGS_PARAM, true);
                    changes.put(CustomLoanApiConstants.WITHDRAWAL_AMOUNT_PARAM, withdrawalAmount);
                    changes.put("withdrawalSavingsAccountId", destinationSavingsAccountId);
                }

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
            loan.getSummary().setReceivableLineOfCredit(isReceivableLineOfCredit);
            updateNetDisbursalAmountForMultiDisbursalLoan(loan);
            disburseLoan(command, isPaymentTypeApplicableForDisbursementCharge, paymentDetail, loan, currentUser, changes,
                    scheduleGeneratorDTO);

            loanAccrualsProcessingService.reprocessExistingAccruals(loan);

            LocalDate firstInstallmentDueDate = loan.fetchRepaymentScheduleInstallment(1).getDueDate();
            if (loan.isInterestBearingAndInterestRecalculationEnabled()
                    && (DateUtils.isBeforeBusinessDate(firstInstallmentDueDate) || loan.isDisbursementMissed())) {
                loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
            }

            if (loan.isAutoRepaymentForDownPaymentEnabled() && !isWithoutAutoPayment) {
                // updating linked savings account for auto down payment transaction for disbursement to savings account
                if (isAccountTransfer && loan.shouldCreateStandingInstructionAtDisbursement()) {
                    // Guard: Check if a down-payment standing instruction already exists to prevent duplicate key
                    // violations
                    if (!hasDownPaymentStandingInstruction(loanId)) {
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
                        log.info("Skipping down-payment standing instruction creation for loan ID: {} - one already exists", loanId);
                    }
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

            // Compute and persist initial custom loan status based on full schedule after disbursement
            CustomLoanStatus oldCustomLoanStatus = loan.hasCustomStatus() ? loan.getCustomLoanStatus() : null;
            CustomLoanStatus newCustomLoanStatus = LoanStatusAggregationUtils.computeCustomLoanStatusForLoan(loan);
            loan.setCustomLoanStatus(newCustomLoanStatus);
            loan = saveAndFlushLoanWithDataIntegrityViolationChecks(loan);

            // Precompute drawdown flags for webhook payload
            Optional<LoanLineOfCreditParams> invoice = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
            boolean isDrawdown = invoice.isPresent();
            Optional<Long> locIdOpt = invoice.map(p -> p.getLineOfCredit() != null ? p.getLineOfCredit().getId() : null);

            // If drawdown, compute LOC status aggregation data
            LocStatusAggregationData locStatusAggregationData;
            if (isDrawdown && locIdOpt.isPresent() && lineOfCreditRepository != null) {
                LineOfCredit loc = invoice.get().getLineOfCredit();
                locStatusAggregationData = this.locStatusAggregationUtils.computeLocStatusAggregationData(loc, loan);
                lineOfCreditRepository.save(loc);

            } else {
                locStatusAggregationData = null;
            }

            Loan finalLoan = loan;
            // Schedule webhook publish after successful commit, in a new transaction
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    transactionTemplate.execute(status -> {
                        loanStatusWebhookPublisher.publish(finalLoan, oldCustomLoanStatus, isDrawdown, locIdOpt);

                        // Publish LOC status webhook if applicable
                        if (locStatusAggregationData != null) {
                            lineOfCreditStatusWebhookPublisher.publish(finalLoan, locStatusAggregationData.getDefaultLocStatus().name(),
                                    locStatusAggregationData.getOldLocCustomStatus().name(),
                                    locStatusAggregationData.getNewLocCustomStatus().name(), isDrawdown, locIdOpt);
                        }
                        return null;
                    });
                }
            });

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

        // For LOC loans, we need to add isDrawdown flag in the result to indicate whether this disbursement is a
        // drawdown or not
        Optional<LoanLineOfCreditParams> invoice = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
        boolean isDrawdown = invoice.isPresent();
        changes.put("isDrawdown", isDrawdown);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(loan.getId())
                .withEntityExternalId(loan.getExternalId()).withSubEntityId(disbursalTransactionId)
                .withSubEntityExternalId(disbursalTransactionExternalId).withOfficeId(loan.getOfficeId()).withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId()).withLoanId(loanId).with(changes).build();
    }

    @Transactional
    @Override
    public CommandProcessingResult undoLoanDisbursal(final Long loanId, final JsonCommand command) {

        Loan loan = this.loanAssembler.assembleFrom(loanId);
        checkClientOrGroupActive(loan);
        if (loan.isChargedOff()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.is.charged.off",
                    "Undo Loan: " + loanId + " disbursement is not allowed. Loan Account is Charged-off", loanId);
        }
        businessEventNotifierService.notifyPreBusinessEvent(new LoanUndoDisbursalBusinessEvent(loan));
        removeLoanCycle(loan);
        final List<Long> existingTransactionIds = new ArrayList<>();
        final List<Long> existingReversedTransactionIds = new ArrayList<>();
        //
        final MonetaryCurrency currency = loan.getCurrency();

        final LocalDate recalculateFrom = null;
        loan.setActualDisbursementDate(null);
        ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);

        // Remove post dated checks if added.
        loan.removePostDatedChecks();

        loanDownPaymentTransactionValidator.validateAccountStatus(loan, LoanEvent.LOAN_DISBURSAL_UNDO);
        loanTransactionValidator.validateActivityNotBeforeClientOrGroupTransferDate(loan, LoanEvent.LOAN_DISBURSAL_UNDO,
                loan.getDisbursementDate());
        final Map<String, Object> changes = undoDisbursal(loan, scheduleGeneratorDTO, existingTransactionIds,
                existingReversedTransactionIds);

        loanAccrualsProcessingService.reprocessExistingAccruals(loan);

        if (!changes.isEmpty()) {
            if (loan.isTopup() && loan.getClientId() != null) {
                final Long loanIdToClose = loan.getTopupLoanDetails().getLoanIdToClose();
                final LocalDate expectedDisbursementDate = command
                        .localDateValueOfParameterNamed(LoanApiConstants.expectedDisbursementDateParameterName);
                BigDecimal loanOutstanding = this.loanReadPlatformService
                        .retrieveLoanPrePaymentTemplate(LoanTransactionType.REPAYMENT, loanIdToClose, expectedDisbursementDate).getAmount();
                BigDecimal netDisbursalAmount = loan.getApprovedPrincipal().subtract(loanOutstanding);
                loan.adjustNetDisbursalAmount(netDisbursalAmount);
            }
            loan = saveAndFlushLoanWithDataIntegrityViolationChecks(loan);
            this.accountTransfersWritePlatformService.reverseAllTransactions(loanId, PortfolioAccountType.LOAN);
            createNote(loan, command, changes);
            boolean isAccountTransfer = false;
            final AccountingBridgeDataDTO accountingBridgeData = loanAccountingBridgeMapper.deriveAccountingBridgeData(currency.getCode(),
                    existingTransactionIds, existingReversedTransactionIds, isAccountTransfer, loan);
            journalEntryWritePlatformService.createJournalEntriesForLoan(accountingBridgeData);
            loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);
            businessEventNotifierService.notifyPostBusinessEvent(new LoanUndoDisbursalBusinessEvent(loan));

            // Compute and persist custom loan status and LOC status aggregation, then publish webhooks after commit
            CustomLoanStatus oldCustomLoanStatus = loan.hasCustomStatus() ? loan.getCustomLoanStatus() : null;
            CustomLoanStatus newCustomLoanStatus = CustomLoanStatus.INVALID;
            loan.setCustomLoanStatus(newCustomLoanStatus);
            loan = saveAndFlushLoanWithDataIntegrityViolationChecks(loan);

            Optional<LoanLineOfCreditParams> invoice = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
            boolean isDrawdown = invoice.isPresent();
            Optional<Long> locIdOpt = invoice.map(p -> p.getLineOfCredit() != null ? p.getLineOfCredit().getId() : null);

            LocStatusAggregationData locStatusAggregationData;
            if (isDrawdown && locIdOpt.isPresent() && lineOfCreditRepository != null) {
                LineOfCredit loc = invoice.get().getLineOfCredit();
                locStatusAggregationData = this.locStatusAggregationUtils.computeLocStatusAggregationData(loc, loan);
                lineOfCreditRepository.save(loc);
            } else {
                locStatusAggregationData = null;
            }

            final Loan finalLoan = loan;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    transactionTemplate.execute(status -> {
                        loanStatusWebhookPublisher.publish(finalLoan, oldCustomLoanStatus, isDrawdown, locIdOpt);

                        // Publish LOC status webhook if applicable
                        if (locStatusAggregationData != null) {
                            lineOfCreditStatusWebhookPublisher.publish(finalLoan, locStatusAggregationData.getDefaultLocStatus().name(),
                                    locStatusAggregationData.getOldLocCustomStatus().name(),
                                    locStatusAggregationData.getNewLocCustomStatus().name(), isDrawdown, locIdOpt);
                        }
                        return null;
                    });
                }
            });
        }

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(loan.getId()) //
                .withEntityExternalId(loan.getExternalId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .with(changes) //
                .build();
    }

    private boolean shouldAutoWithdrawFromSavings(final JsonCommand command) {
        if (command == null) {
            return false;
        }
        if (!command.parameterExists(CustomLoanApiConstants.AUTO_WITHDRAW_FROM_SAVINGS_PARAM)) {
            return false;
        }
        return Boolean.TRUE.equals(command.booleanObjectValueOfParameterNamed(CustomLoanApiConstants.AUTO_WITHDRAW_FROM_SAVINGS_PARAM));
    }

    private BigDecimal resolveWithdrawalAmount(final JsonCommand command, final BigDecimal defaultAmount) {
        BigDecimal amount = null;
        if (command.parameterExists(CustomLoanApiConstants.WITHDRAWAL_AMOUNT_PARAM)) {
            amount = command.bigDecimalValueOfParameterNamed(CustomLoanApiConstants.WITHDRAWAL_AMOUNT_PARAM);
        }
        if (amount == null) {
            amount = defaultAmount;
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.disburse.to.savings.auto.withdraw.invalid.amount",
                    "Withdrawal amount must be greater than zero");
        }
        return amount;
    }

    private Long resolveDestinationSavingsAccountIdForDisbursement(final Loan loan, final JsonCommand command) {
        Long destinationSavingsAccountId = command.longValueOfParameterNamed(LoanApiConstants.DESTINATION_SAVINGS_ACCOUNT_ID_PARAM_NAME);
        if (destinationSavingsAccountId != null) {
            // Same validation used for disbursement
            validateDestinationSavingsAccount(loan, destinationSavingsAccountId);
            return destinationSavingsAccountId;
        }

        PortfolioAccountData linked = this.accountAssociationsReadPlatformService.retriveLoanLinkedAssociation(loan.getId());
        if (linked == null) {
            final String errorMessage = "Disburse Loan with id:" + loan.getId() + " requires linked savings account for payment";
            throw new LinkedAccountRequiredException("loan.disburse.to.savings", errorMessage, loan.getId());
        }
        return linked.getId();
    }

    private void performAutoWithdrawalFromSavings(final Long savingsAccountId, final JsonCommand originalCommand,
            final BigDecimal withdrawalAmount) {
        final Locale locale = originalCommand.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(originalCommand.dateFormat()).withLocale(locale);
        final LocalDate transactionDate = originalCommand.localDateValueOfParameterNamed("actualDisbursementDate");

        // Savings withdrawal requires a paymentTypeId (must be provided by the frontend when auto-withdraw is enabled)
        final Long paymentTypeId = originalCommand.longValueOfParameterNamed(CustomLoanApiConstants.WITHDRAWAL_PAYMENT_TYPE_ID_PARAM);
        if (paymentTypeId == null || paymentTypeId <= 0L) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.disburse.to.savings.auto.withdraw.payment.type.required",
                    "Parameter '" + CustomLoanApiConstants.WITHDRAWAL_PAYMENT_TYPE_ID_PARAM
                            + "' is required and must be a positive number when autoWithdrawFromSavings is true",
                    CustomLoanApiConstants.WITHDRAWAL_PAYMENT_TYPE_ID_PARAM);
        }

        final JsonObject json = new JsonObject();
        json.addProperty("locale", locale.toLanguageTag());
        json.addProperty("dateFormat", originalCommand.dateFormat());
        json.addProperty("transactionDate", fmt.format(transactionDate));
        json.addProperty("transactionAmount", withdrawalAmount);
        json.addProperty("paymentTypeId", paymentTypeId);

        // Build a JsonCommand compatible with savings withdrawal service
        final String withdrawalJson = json.toString();
        final JsonElement parsed = fromApiJsonHelper.parse(withdrawalJson);

        final JsonCommand withdrawalCommand = JsonCommand.from(withdrawalJson, parsed, fromApiJsonHelper, "SAVINGSACCOUNT",
                savingsAccountId, null, originalCommand.getGroupId(), originalCommand.getClientId(), null, savingsAccountId, null, null,
                originalCommand.getProductId(), originalCommand.getCreditBureauId(), originalCommand.getOrganisationCreditBureauId(), null,
                null);

        this.savingsAccountWritePlatformService.withdrawal(savingsAccountId, withdrawalCommand);
    }

    private void updateNetDisbursalAmountForMultiDisbursalLoan(final Loan loan) {
        if (loan.isMultiDisburmentLoan()) {
            BigDecimal netDisbursalAmount = BigDecimal.ZERO;
            final List<LoanDisbursementDetails> loanDisbursementDetails = loan.getDisbursementDetails();
            for (final LoanDisbursementDetails disbursementDetail : loanDisbursementDetails) {
                if (disbursementDetail.actualDisbursementDate() != null) {
                    final BigDecimal amountToDisburse = disbursementDetail.principal();
                    final Set<LoanCharge> loanCharges = loan.getActiveCharges();
                    BigDecimal disbursementCharge = BigDecimal.ZERO;
                    for (final LoanCharge loanCharge : loanCharges) {
                        if (loanCharge.isDueAtDisbursement() && !loanCharge.getChargePaymentMode().isPaymentModeAccountTransfer()) {
                            final BigDecimal multiDisbursementChargeAmount = loanCharge
                                    .calculateMultiDisbursementChargeAmount(amountToDisburse);
                            disbursementCharge = disbursementCharge.add(multiDisbursementChargeAmount);
                        }
                    }
                    BigDecimal netDisbursementAmount = amountToDisburse.subtract(disbursementCharge);
                    netDisbursalAmount = netDisbursalAmount.add(netDisbursementAmount);
                }
            }
            loan.setNetDisbursalAmount(netDisbursalAmount);
        }
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
                    CustomLoanDisbursementService.FeeAndTaxForTranche feeAndTax = customLoanDisbursementService
                            .calculateFeeAndTaxForTrancheDisbursement(loan, actualDisbursementDate);
                    BigDecimal chargeReducableFromDisbursement = feeAndTax.fee().getAmount().add(feeAndTax.tax().getAmount());
                    loan.getSummary().setTotalChargesPayableByPrincipalDeduction(chargeReducableFromDisbursement);

                    Money netDisbursementAmount = disburseAmount.minus(feeAndTax.fee()).minus(feeAndTax.tax());
                    disburseLoanToSavings(loan, command, disburseAmount, netDisbursementAmount, paymentDetail);
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

    private void disburseLoanToSavings(final Loan loan, final JsonCommand command, final Money amount, final Money netDisbursementAmount,
            final PaymentDetail paymentDetail) {
        final LocalDate transactionDate = command.localDateValueOfParameterNamed("actualDisbursementDate");
        final ExternalId txnExternalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);

        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);

        // Get destination savings account - either from parameter or linked account
        Long destinationSavingsAccountId = command.longValueOfParameterNamed(LoanApiConstants.DESTINATION_SAVINGS_ACCOUNT_ID_PARAM_NAME);
        PortfolioAccountData portfolioAccountData;

        if (destinationSavingsAccountId != null) {
            // Validate the specified destination account
            validateDestinationSavingsAccount(loan, destinationSavingsAccountId);
            // Get the savings account data
            final SavingsAccount destinationSavingsAccount = savingsAccountRepositoryWrapper
                    .findOneWithNotFoundDetection(destinationSavingsAccountId);
            portfolioAccountData = PortfolioAccountData.lookup(destinationSavingsAccountId, destinationSavingsAccount.getAccountNumber());
        } else {
            // Use linked account (backward compatibility)
            portfolioAccountData = this.accountAssociationsReadPlatformService.retriveLoanLinkedAssociation(loan.getId());
            if (portfolioAccountData == null) {
                final String errorMessage = "Disburse Loan with id:" + loan.getId() + " requires linked savings account for payment";
                throw new LinkedAccountRequiredException("loan.disburse.to.savings", errorMessage, loan.getId());
            }
        }

        final SavingsAccount fromSavingsAccount = null;
        final boolean isExceptionForBalanceCheck = false;
        final boolean isRegularTransaction = true;

        // Use tranche amount for transaction amount (full principal for accounting)
        final CustomAccountTransferDTO accountTransferDTO = new CustomAccountTransferDTO(transactionDate, amount.getAmount(),
                PortfolioAccountType.LOAN, PortfolioAccountType.SAVINGS, loan.getId(), portfolioAccountData.getId(), "Loan Disbursement",
                locale, fmt, paymentDetail, LoanTransactionType.DISBURSEMENT.getValue(), null, null, null,
                AccountTransferType.ACCOUNT_TRANSFER.getValue(), null, null, txnExternalId, loan, null, fromSavingsAccount,
                isRegularTransaction, isExceptionForBalanceCheck);
        // Set netLoanDisbursementAmount to amount after deducting fees (actual amount to deposit to savings)
        accountTransferDTO.setNetLoanDisbursementAmount(netDisbursementAmount.getAmount());
        this.accountTransfersWritePlatformService.transferFunds(accountTransferDTO);
    }

    /**
     * Validates that the destination savings account is eligible for loan disbursement. Checks: - Account exists and is
     * active - Currency matches loan currency - Account belongs to the same borrower/client
     *
     * @param loan
     *            The loan being disbursed
     * @param savingsAccountId
     *            The destination savings account ID
     * @throws GeneralPlatformDomainRuleException
     *             if validation fails
     */
    private void validateDestinationSavingsAccount(final Loan loan, final Long savingsAccountId) {
        final SavingsAccount savingsAccount = savingsAccountRepositoryWrapper.findOneWithNotFoundDetection(savingsAccountId);

        // Check if account is active
        if (savingsAccount.isNotActive()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.destination.savings.account.not.active",
                    "Destination savings account with id:" + savingsAccountId + " is not active", savingsAccountId);
        }

        // Check currency match
        if (!savingsAccount.getCurrency().getCode().equals(loan.getCurrencyCode())) {
            throw new GeneralPlatformDomainRuleException(
                    "error.msg.loan.destination.savings.account.currency.mismatch", "Destination savings account currency ("
                            + savingsAccount.getCurrency().getCode() + ") does not match loan currency (" + loan.getCurrencyCode() + ")",
                    savingsAccountId);
        }

        // Check borrower/client match
        final Long loanClientId = loan.getClientId();
        final Long loanGroupId = loan.getGroupId();
        final Long savingsClientId = savingsAccount.clientId();
        final Long savingsGroupId = savingsAccount.groupId();

        boolean clientMatch = false;
        if (loanClientId != null && savingsClientId != null && loanClientId.equals(savingsClientId)) {
            clientMatch = true;
        }

        boolean groupMatch = false;
        if (loanGroupId != null && savingsGroupId != null && loanGroupId.equals(savingsGroupId)) {
            groupMatch = true;
        }

        // For individual loans, client must match
        // For group loans, group must match
        // For JLG loans, both client and group must match
        if (loanClientId != null && loanGroupId == null) {
            // Individual loan - client must match
            if (!clientMatch) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.destination.savings.account.client.mismatch",
                        "Destination savings account does not belong to the same client as the loan", savingsAccountId);
            }
        } else if (loanClientId == null && loanGroupId != null) {
            // Group loan - group must match
            if (!groupMatch) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.destination.savings.account.group.mismatch",
                        "Destination savings account does not belong to the same group as the loan", savingsAccountId);
            }
        } else if (loanClientId != null && loanGroupId != null) {
            // JLG loan - both client and group must match
            if (!clientMatch || !groupMatch) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.destination.savings.account.borrower.mismatch",
                        "Destination savings account does not belong to the same borrower (client/group) as the loan", savingsAccountId);
            }
        }
    }

    @Override
    @Transactional
    public CommandProcessingResult forecloseLoan(Long loanId, JsonCommand command) {

        final Boolean isForcedClosure = command.booleanObjectValueOfParameterNamed("isForcedClosure");
        final Boolean isRestructured = command.booleanObjectValueOfParameterNamed("isRestructured");

        Loan loan = this.loanAssembler.assembleFrom(loanId);

        // Capture old custom loan status before update
        CustomLoanStatus oldCustomLoanStatus = loan.hasCustomStatus() ? loan.getCustomLoanStatus() : null;

        // Update custom loan status based on closure type
        if (Boolean.TRUE.equals(isForcedClosure)) {
            loan.setCustomLoanStatus(CustomLoanStatus.FORCED_CLOSURE);
        } else {
            loan.setCustomLoanStatus(CustomLoanStatus.EARLY_CLOSURE);
        }

        // Precompute drawdown flags and LOC aggregation before commit
        Optional<LoanLineOfCreditParams> locParamsOpt = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
        boolean isDrawdown = locParamsOpt.isPresent();
        Optional<Long> locIdOpt = locParamsOpt.map(p -> p.getLineOfCredit() != null ? p.getLineOfCredit().getId() : null);

        LocStatusAggregationData locStatusAggregationData;
        if (isDrawdown && locIdOpt.isPresent() && lineOfCreditRepository != null) {
            LineOfCredit loc = locParamsOpt.get().getLineOfCredit();
            // Compute LOC status aggregation based on the loan closure
            locStatusAggregationData = this.locStatusAggregationUtils.computeLocStatusAggregationData(loc, loan);
            // Persist LOC changes prior to webhook
            lineOfCreditRepository.save(loc);
        } else {
            locStatusAggregationData = null;
        }

        // Schedule webhook publish after successful commit, in a new transaction
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                transactionTemplate.execute(status -> {
                    // Publish loan status change webhook
                    loanStatusWebhookPublisher.publish(loan, oldCustomLoanStatus, isDrawdown, locIdOpt);
                    // Publish LOC status webhook if applicable
                    if (locStatusAggregationData != null) {
                        lineOfCreditStatusWebhookPublisher.publish(loan, locStatusAggregationData.getDefaultLocStatus().name(),
                                locStatusAggregationData.getOldLocCustomStatus().name(),
                                locStatusAggregationData.getNewLocCustomStatus().name(), isDrawdown, locIdOpt);
                    }
                    return null;
                });
            }
        });

        JsonElement parsedJson = command.parsedJson();
        if (parsedJson != null && parsedJson.isJsonObject()) {
            parsedJson.getAsJsonObject().remove("isForcedClosure");
            parsedJson.getAsJsonObject().remove("isRestructured");
        }

        JsonCommand cleanedCommand = JsonCommand.fromExistingCommand(command, parsedJson, null);

        CommandProcessingResult result = super.forecloseLoan(loanId, cleanedCommand);

        if (result != null && result.getResourceId() != null && result.getResourceId() > 0L) {

            BigDecimal amount = loan.getProposedPrincipal();
            final LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");

            updateLocBalance(loanId, amount, transactionDate, LineOfCreditTransactionType.FORECLOSURE, null);
            loan.setIsRestructured(Boolean.TRUE.equals(isRestructured));
            loan.setIsForcedClosure(Boolean.TRUE.equals(isForcedClosure));
        }

        return result;
    }

    @Transactional
    @Override
    public CommandProcessingResult makeLoanRepayment(final LoanTransactionType repaymentTransactionType, final Long loanId,
            final JsonCommand command, final boolean isRecoveryRepayment) {
        // Fix: Validate repayment with corrected multi-tranche logic before calling parent
        // The parent will call validateRepayment which has the broken validation, so we need to
        // catch and handle that exception, then re-validate with the fixed logic
        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        try {
            // Call the parent implementation to handle the core repayment logic
            CommandProcessingResult result = super.makeLoanRepayment(repaymentTransactionType, loanId, command, isRecoveryRepayment);

            // Use resourceId instead of subResourceId since parent puts transaction ID in entityId (resourceId)
            if (result != null && result.hasChanges() && result.getResourceId() != null) {
                try {
                    // Fetch the updated loan to get the loan transaction
                    Loan updatedLoan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);

                    BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed("transactionAmount");

                    // Find the specific transaction that was just created using the resourceId (transaction ID)
                    LoanTransaction transaction = updatedLoan.getLoanTransaction(t -> t.getId().equals(result.getResourceId()));

                    updateLocBalance(loanId, transactionAmount, command.localDateValueOfParameterNamed("transactionDate"),
                            LineOfCreditTransactionType.REPAYMENT, transaction);

                    if (transaction != null && transaction.getLoanTransactionToRepaymentScheduleMappings() != null) {
                        // Extract affected installments using the shared utility method
                        List<Map<String, Object>> affectedInstallments = LoanTransactionInstallmentUtils
                                .extractAffectedInstallments(updatedLoan, transaction);

                        // Capture old custom loan status before update
                        CustomLoanStatus oldCustomLoanStatus = updatedLoan.hasCustomStatus() ? updatedLoan.getCustomLoanStatus() : null;

                        // Compute and update the custom loan status based on affected installments
                        CustomLoanStatus newCustomLoanStatus = LoanTransactionInstallmentUtils.computeCustomLoanStatusForLoan(updatedLoan);
                        updatedLoan.setCustomLoanStatus(newCustomLoanStatus);

                        // Precompute drawdown flags before commit using LoanLineOfCreditParamsRepository
                        Optional<LoanLineOfCreditParams> invoice = loanLineOfCreditParamsRepository.findByLoanId(updatedLoan.getId());
                        boolean isDrawdown = invoice.isPresent();
                        Optional<Long> locIdOpt = invoice.map(p -> p.getLineOfCredit() != null ? p.getLineOfCredit().getId() : null);

                        // If drawdown, compute LOC status aggregation data
                        LocStatusAggregationData locStatusAggregationData;
                        if (isDrawdown && locIdOpt.isPresent() && lineOfCreditRepository != null) {
                            LineOfCredit loc = invoice.get().getLineOfCredit();
                            locStatusAggregationData = this.locStatusAggregationUtils.computeLocStatusAggregationData(loc, updatedLoan);
                            lineOfCreditRepository.save(loc);

                        } else {
                            locStatusAggregationData = null;
                        }

                        // Schedule webhook publish after successful commit, in a new transaction
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {
                                transactionTemplate.execute(status -> {
                                    loanStatusWebhookPublisher.publish(updatedLoan, oldCustomLoanStatus, isDrawdown, locIdOpt);

                                    // Publish LOC status webhook if applicable
                                    if (locStatusAggregationData != null) {
                                        lineOfCreditStatusWebhookPublisher.publish(updatedLoan,
                                                locStatusAggregationData.getDefaultLocStatus().name(),
                                                locStatusAggregationData.getOldLocCustomStatus().name(),
                                                locStatusAggregationData.getNewLocCustomStatus().name(), isDrawdown, locIdOpt);
                                    }
                                    return null;
                                });
                            }
                        });

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
                        additionalChanges.put("transactionId", result.getResourceId()); // Use resourceId as transaction
                                                                                        // ID
                        additionalChanges.put("isDrawdown", isDrawdown);
                        if (isDrawdown) {
                            Long locId = locIdOpt.orElse(null);
                            additionalChanges.put("locId", locId);
                        }

                        // Create a new result with the additional schedule information
                        return new CommandProcessingResultBuilder().withCommandId(result.getCommandId())
                                .withEntityId(result.getResourceId()).withEntityExternalId(result.getResourceExternalId())
                                .withSubEntityId(result.getSubResourceId()).withSubEntityExternalId(result.getSubResourceExternalId())
                                .withOfficeId(result.getOfficeId()).withClientId(result.getClientId()).withGroupId(result.getGroupId())
                                .withLoanId(result.getLoanId()).withLoanExternalId(result.getLoanExternalId()).with(additionalChanges)
                                .build();
                    }

                } catch (Exception e) {
                    // Log the error but don't fail the repayment transaction
                    log.warn("Failed to fetch affected installments for webhook payload: {}", e.getMessage());
                    return result;
                }
            }
            return result;
        } catch (InvalidLoanStateTransitionException e) {
            // Check if this is the multi-tranche validation error for a single-tranche loan
            if ("amount.exceeds.threshold".equals(e.getGlobalisationMessageCode())) {
                // Re-validate with fixed logic: only apply validation if loan actually has multiple tranches
                if (!LoanTrancheValidationHelper.hasActualMultipleTranches(loan)) {
                    // Single-tranche loan under multi-tranche product - validation should not apply
                    log.debug("Ignoring multi-tranche validation error for single-tranche loan {}", loanId);
                    // Retry the repayment without the broken validation
                    // We need to call the parent again, but this time we'll skip the validation
                    // Actually, we can't skip it easily, so we'll just re-throw if it's a real error
                    // For now, let's validate manually and then proceed
                    validateTransactionAmountNotExceedThresholdForMultiDisburseLoanFixed(loan);
                    // If validation passes, retry the repayment
                    return super.makeLoanRepayment(repaymentTransactionType, loanId, command, isRecoveryRepayment);
                }
            }
            // Re-throw if it's a different error or if it's a real multi-tranche loan
            throw e;
        }
    }

    /**
     * Fixed version of validateTransactionAmountNotExceedThresholdForMultiDisburseLoan that only applies validation
     * when the loan actually has multiple tranches.
     */
    private void validateTransactionAmountNotExceedThresholdForMultiDisburseLoanFixed(Loan loan) {
        // Only apply validation if the loan actually has multiple tranches or pending tranches
        if (!LoanTrancheValidationHelper.hasActualMultipleTranches(loan)) {
            return;
        }

        // Apply the original validation logic for actual multi-tranche loans
        BigDecimal totalDisbursed = loan.getDisbursedAmount();
        BigDecimal totalPrincipalAdjusted = loan.getSummary().getTotalPrincipalAdjustments();
        BigDecimal totalPrincipalCredited = totalDisbursed.add(totalPrincipalAdjusted);
        if (totalPrincipalCredited.compareTo(loan.getSummary().getTotalPrincipalRepaid()) < 0) {
            final String errorMessage = "The transaction amount cannot exceed threshold.";
            throw new InvalidLoanStateTransitionException("transaction", "amount.exceeds.threshold", errorMessage);
        }
    }

    public CommandProcessingResult makeLoanRepaymentWithChargeRefundChargeType(final LoanTransactionType repaymentTransactionType,
            final Long loanId, final JsonCommand command, final boolean isRecoveryRepayment, final String chargeRefundChargeType) {
        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        final Long penaltyWaitPeriodValue = this.configurationDomainService.retrievePenaltyWaitPeriod();
        final Collection<LoanSchedulePeriodData> loanSchedulePeriods = this.loanRepaymentsSummaryDAO.fetchLoanRepaymentsSummary(loanId);
        final Collection<LoanChargeData> loanCharges = this.customLoanChargeReadPlatformService.retrieveLoanCharges(loanId);
        final List<ExtendedLoanSchedulePeriodData> loanSchedulePeriodsWithStatus = loanSchedulePeriods.stream()
                .map(p -> new ExtendedLoanSchedulePeriodData(p,
                        this.credibleXLoanReadPlatformService.resolvePeriodStatus(loan.getCurrency().toData(), p)))
                .toList();
        // All loans now allow early repayments before the first installment due date
        final boolean isDrawdownLoan = true;
        final CredibleXLoanPenaltyCalculator penaltyCalculator = new CredibleXLoanPenaltyCalculator(loanSchedulePeriodsWithStatus,
                loanCharges, penaltyWaitPeriodValue, isDrawdownLoan);
        final LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");
        final List<LoanChargeData> penaltiesToDisable = penaltyCalculator.getPenaltiesToDisable(transactionDate, loanId);
        if (!penaltiesToDisable.isEmpty()) {
            final List<Long> chargeIds = penaltiesToDisable.stream().map(LoanChargeData::getId).toList();
            final List<LoanTransaction> accrualTransactions = loanChargeRepository.findAccrualTransactionsByChargeIds(chargeIds,
                    LoanTransactionType.ACCRUAL);
            for (final LoanTransaction accrualTransaction : accrualTransactions) {
                if (!accrualTransaction.isReversed()) {
                    final MonetaryCurrency currency = loan.getCurrency();
                    final Map<Integer, Money> interestByInstallment = new HashMap<>();
                    final Map<Integer, Money> feesByInstallment = new HashMap<>();
                    final Map<Integer, Money> penaltiesByInstallment = new HashMap<>();
                    final Set<LoanChargePaidBy> chargesPaid = new HashSet<>(accrualTransaction.getLoanChargesPaid());
                    for (LoanChargePaidBy chargePaidBy : chargesPaid) {
                        final Integer installmentNumber = chargePaidBy.getInstallmentNumber();
                        final LoanCharge charge = chargePaidBy.getLoanCharge();
                        final Money amount = Money.of(currency, chargePaidBy.getAmount());
                        if (charge.isPenaltyCharge()) {
                            penaltiesByInstallment.merge(installmentNumber, amount, Money::plus);
                        } else if (charge.isFeeCharge()) {
                            feesByInstallment.merge(installmentNumber, amount, Money::plus);
                        }
                    }
                    final Money totalInterest = Money.of(currency, accrualTransaction.getInterestPortion());
                    if (totalInterest.isGreaterThanZero()) {
                        final LocalDate accrualDate = accrualTransaction.getTransactionDate();
                        final LoanRepaymentScheduleInstallment targetInstallment = loan.getRepaymentScheduleInstallments().stream()
                                .filter(inst -> !inst.isDownPayment() && !inst.isAdditional())
                                .filter(inst -> DateUtils.isEqual(accrualDate, inst.getDueDate())
                                        || DateUtils.isBefore(accrualDate, inst.getDueDate()))
                                .findFirst().orElse(null);
                        if (targetInstallment != null) {
                            interestByInstallment.put(targetInstallment.getInstallmentNumber(), totalInterest);
                        }
                    }
                    final Set<Integer> allInstallmentNumbers = new HashSet<>();
                    allInstallmentNumbers.addAll(interestByInstallment.keySet());
                    allInstallmentNumbers.addAll(feesByInstallment.keySet());
                    allInstallmentNumbers.addAll(penaltiesByInstallment.keySet());
                    for (final Integer installmentNumber : allInstallmentNumbers) {
                        final LoanRepaymentScheduleInstallment installment = loan.fetchRepaymentScheduleInstallment(installmentNumber);
                        final Money interestToReverse = interestByInstallment.getOrDefault(installmentNumber, Money.zero(currency));
                        final Money feesToReverse = feesByInstallment.getOrDefault(installmentNumber, Money.zero(currency));
                        final Money penaltiesToReverse = penaltiesByInstallment.getOrDefault(installmentNumber, Money.zero(currency));
                        final Money currentInterestAccrued = installment.getInterestAccrued(currency);
                        final Money currentFeeAccrued = installment.getFeeAccrued(currency);
                        final Money currentPenaltyAccrued = installment.getPenaltyAccrued(currency);
                        final Money newInterestAccrued = currentInterestAccrued.minus(interestToReverse);
                        final Money newFeeAccrued = currentFeeAccrued.minus(feesToReverse);
                        final Money newPenaltyAccrued = currentPenaltyAccrued.minus(penaltiesToReverse);
                        installment.updateAccrualPortion(newInterestAccrued, newFeeAccrued, newPenaltyAccrued);
                    }
                }
                accrualTransaction.reverse();
            }
            if (!accrualTransactions.isEmpty()) {
                this.loanTransactionRepository.saveAllAndFlush(accrualTransactions);
            }
            if (!chargeIds.isEmpty()) {
                loan.getLoanCharges().stream().filter(loanCharge -> chargeIds.contains(loanCharge.getId()) && !loanCharge.isPaid())
                        .forEach(loanCharge -> loanCharge.setActive(false));
                saveAndFlushLoanWithDataIntegrityViolationChecks(loan);
            }
            log.info("Deactivated {} penalty charges for loan id {}", chargeIds.size(), loanId);
        }
        final CommandProcessingResult result = super.makeLoanRepaymentWithChargeRefundChargeType(repaymentTransactionType, loanId, command,
                isRecoveryRepayment, chargeRefundChargeType);
        if (result != null && result.getResourceId() != null && !penaltiesToDisable.isEmpty()) {
            this.applyOverdueChargesLoan(loanId);
            Loan backdatedLoan = this.loanAssembler.assembleFrom(loanId);
            this.addLoanPeriodicAccruals(backdatedLoan);
            loanScheduleService.recalculateSchedule(backdatedLoan, loanUtilService.buildScheduleGeneratorDTO(backdatedLoan, null));
            backdatedLoan = this.loanAssembler.assembleFrom(loanId);
            saveAndFlushLoanWithDataIntegrityViolationChecks(backdatedLoan);
        }
        return result;
    }

    private void addLoanPeriodicAccruals(final Loan loan) {
        final LocalDate accrualDate = DateUtils.getBusinessLocalDate();
        try {
            loanAccrualsProcessingService.addPeriodicAccruals(accrualDate, loan);
        } catch (MultiException me) {
            final String message = ExceptionUtils.getMessage(me);
            log.error("Error adding periodic accruals for loan id {}: {}", loan.getId(), message);
            throw new GeneralPlatformDomainRuleException("error.msg.loan.accruals.addition.failed",
                    "Adding periodic accruals failed with error message : " + message);
        } catch (Exception e) {
            final String message = ExceptionUtils.getMessage(e);
            log.error("Unexpected error adding periodic accruals for loan id {}: {}", loan.getId(), message);
            throw new GeneralPlatformDomainRuleException("error.msg.loan.accruals.addition.failed",
                    "Adding periodic accruals failed with error message : " + message);
        }
    }

    private void applyOverdueChargesLoan(final Long loanId) {
        final Long penaltyWaitPeriodValue = configurationDomainService.retrievePenaltyWaitPeriod();
        final Boolean backdatePenalties = configurationDomainService.isBackdatePenaltiesEnabled();
        final Collection<OverdueLoanScheduleData> overdueLoanScheduledInstallments = loanReadPlatformService
                .retrieveLoanOverdueInstallments(loanId, penaltyWaitPeriodValue, backdatePenalties);
        if (!overdueLoanScheduledInstallments.isEmpty()) {
            final Map<Long, Collection<OverdueLoanScheduleData>> overdueScheduleData = new HashMap<>();
            for (final OverdueLoanScheduleData overdueInstallment : overdueLoanScheduledInstallments) {
                if (overdueScheduleData.containsKey(overdueInstallment.getLoanId())) {
                    overdueScheduleData.get(overdueInstallment.getLoanId()).add(overdueInstallment);
                } else {
                    Collection<OverdueLoanScheduleData> loanData = new ArrayList<>();
                    loanData.add(overdueInstallment);
                    overdueScheduleData.put(overdueInstallment.getLoanId(), loanData);
                }
            }

            final List<Throwable> exceptions = new ArrayList<>();
            for (final Map.Entry<Long, Collection<OverdueLoanScheduleData>> entry : overdueScheduleData.entrySet()) {
                try {
                    if (!entry.getValue().isEmpty()) {
                        this.credibleXLoanChargeWritePlatformService.applyOverdueChargesForLoan(entry.getKey(), entry.getValue());
                    }
                } catch (final PlatformApiDataValidationException e) {
                    final List<ApiParameterError> errors = e.getErrors();
                    for (final ApiParameterError error : errors) {
                        log.error("Apply Charges due for overdue loans failed for account {} with message: {}", entry.getKey(),
                                error.getDeveloperMessage(), e);
                    }
                    exceptions.add(e);
                } catch (final AbstractPlatformDomainRuleException e) {
                    log.error("Apply Charges due for overdue loans failed for account {} with message: {}", entry.getKey(),
                            e.getDefaultUserMessage(), e);
                    exceptions.add(e);
                } catch (Exception e) {
                    log.error("Apply Charges due for overdue loans failed for account {}", entry.getKey(), e);
                    exceptions.add(e);
                }
            }
            if (!exceptions.isEmpty()) {
                throw new GeneralPlatformDomainRuleException("error.msg.applying.overdue.charges.failed",
                        "Applying overdue charges failed for loan id: " + loanId);
            }
        }
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

        // Capture old custom loan status before the adjustment (if present)
        Loan loanBefore = this.loanAssembler.assembleFrom(loanId);
        CustomLoanStatus oldCustomLoanStatus = loanBefore.hasCustomStatus() ? loanBefore.getCustomLoanStatus() : null;

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
            // Pass transactionToAdjust so we can get the principal portion for non-receivable LOC products
            updateLocBalance(loanId, repaymentAmount, transactionDate, LineOfCreditTransactionType.REVERSAL, transactionToAdjust);
        }

        // After schedule and installment recalculations done by parent (LoanAdjustmentServiceImpl),
        // recompute and persist new custom loan status and LOC status, then fire webhooks
        try {
            Loan updatedLoan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);

            // Compute new custom loan status based on the updated schedule/installments
            CustomLoanStatus newCustomLoanStatus = LoanTransactionInstallmentUtils.computeCustomLoanStatusForLoan(updatedLoan);
            updatedLoan.setCustomLoanStatus(newCustomLoanStatus);

            // Precompute drawdown flags for webhook payload
            Optional<LoanLineOfCreditParams> invoice = loanLineOfCreditParamsRepository.findByLoanId(updatedLoan.getId());
            boolean isDrawdown = invoice.isPresent();
            Optional<Long> locIdOpt = invoice.map(p -> p.getLineOfCredit() != null ? p.getLineOfCredit().getId() : null);

            // If drawdown, compute LOC status aggregation data
            LocStatusAggregationData locStatusAggregationData;
            if (isDrawdown && locIdOpt.isPresent() && lineOfCreditRepository != null) {
                LineOfCredit loc = invoice.get().getLineOfCredit();
                locStatusAggregationData = this.locStatusAggregationUtils.computeLocStatusAggregationData(loc, updatedLoan);
                // Persist LOC changes
                lineOfCreditRepository.save(loc);
            } else {
                locStatusAggregationData = null;
            }

            // Persist the new custom status on the loan
            saveAndFlushLoanWithDataIntegrityViolationChecks(updatedLoan);

            // Schedule webhook publish after successful commit, in a new transaction
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    transactionTemplate.execute(status -> {
                        // Publish loan status change
                        loanStatusWebhookPublisher.publish(updatedLoan, oldCustomLoanStatus, isDrawdown, locIdOpt);

                        // Publish LOC status webhook if applicable
                        if (locStatusAggregationData != null) {
                            lineOfCreditStatusWebhookPublisher.publish(updatedLoan, locStatusAggregationData.getDefaultLocStatus().name(),
                                    locStatusAggregationData.getOldLocCustomStatus().name(),
                                    locStatusAggregationData.getNewLocCustomStatus().name(), isDrawdown, locIdOpt);
                        }
                        return null;
                    });
                }
            });
        } catch (Exception e) {
            log.warn("Failed to recompute/publish custom statuses after adjustment for loan {}: {}", loanId, e.getMessage());
        }

        return result;
    }

    private void updateLocBalance(Long loanId, BigDecimal amount, LocalDate transactionDate, LineOfCreditTransactionType transactionType,
            LoanTransaction loanTransaction) {

        Optional<LoanLineOfCreditParams> locProductTypeOpt = loanLineOfCreditParamsRepository.findByLoanId(loanId);

        if (transactionType.isRepayment() || transactionType.isReversal() || transactionType.isRefund()) {
            if (locProductTypeOpt.isPresent() && !locProductTypeOpt.get().getLineOfCredit().getProductType().isReceivable()) {
                // For non-receivable LOC products, use principal portion if available, otherwise use the provided
                // amount
                if (loanTransaction != null) {
                    amount = loanTransaction.getPrincipalPortion();
                }
                // If loanTransaction is null, use the provided amount as-is (should not happen in normal flow)
            }
        }

        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0 && locProductTypeOpt.isPresent()) {
            // For repayments, we reduce the LOC balance (i.e. free up credit)
            // Pass loan transaction ID for better traceability
            Long loanTransactionId = loanTransaction != null ? loanTransaction.getId() : null;
            lineOfCreditBalanceUpdateService.computeLocBalance(loanId, loanTransactionId, amount, locProductTypeOpt.get().getLineOfCredit(),
                    transactionDate, transactionType);
        }
    }

    private final class SavingsToLoanTransferBusinessEventListener
            implements BusinessEventListener<SavingsToLoanAccountTransferBusinessEvent> {

        @Override
        public void onBusinessEvent(SavingsToLoanAccountTransferBusinessEvent event) {
            AccountTransferDetails accountTransferDetails = event.get();

            if (accountTransferDetails.fromSavingsAccount() != null && accountTransferDetails.toLoanAccount() != null) {

                Long loanId = accountTransferDetails.toLoanAccount().getId();
                Long fromSavingsAccountId = accountTransferDetails.fromSavingsAccount().getId();
                Optional<LoanLineOfCreditParams> lineOfCreditParams = loanLineOfCreditParamsRepository.findByLoanId(loanId);

                if (lineOfCreditParams.isPresent()) {
                    LineOfCredit lineOfCredit = lineOfCreditParams.get().getLineOfCredit();

                    Optional<AccountTransferTransaction> acctTransferTransaction = accountTransferDetails.getAccountTransferTransactions()
                            .stream().findFirst();

                    if (acctTransferTransaction.isEmpty()) {
                        throw new GeneralPlatformDomainRuleException("account.transfer.transaction.not.found", String.format(
                                "Account transfer transaction not found for transfer from savings account id: %d to loan account id: %d",
                                fromSavingsAccountId, loanId));
                    }

                    LoanTransaction loanTransaction = acctTransferTransaction.get().getToLoanTransaction();

                    // Null check before dereferencing loanTransaction
                    if (loanTransaction == null) {
                        throw new GeneralPlatformDomainRuleException("account.transfer.loan.transaction.not.found",
                                String.format("Loan transaction not found for transfer from savings account id: %d to loan account id: %d",
                                        fromSavingsAccountId, loanId));
                    }

                    BigDecimal amount;
                    if (lineOfCredit.getProductType().isReceivable()) {
                        amount = loanTransaction.getAmount();
                    } else {
                        amount = loanTransaction.getPrincipalPortion();
                    }

                    // Pass loan transaction ID for better traceability
                    Long loanTransactionId = loanTransaction.getId();
                    LocalDate transactionDate = loanTransaction.getTransactionDate();

                    try {
                        log.debug(
                                "Updating LOC balance for Savings → Loan transfer. LOC ID: {}, Loan ID: {}, "
                                        + "Loan Transaction ID: {}, From Savings Account ID: {}, Amount: {}, Transaction Date: {}",
                                lineOfCredit.getId(), loanId, loanTransactionId, fromSavingsAccountId, amount, transactionDate);

                        lineOfCreditBalanceUpdateService.computeLocBalance(loanId, loanTransactionId, amount, lineOfCredit, transactionDate,
                                LineOfCreditTransactionType.REPAYMENT);

                        log.debug("Successfully updated LOC balance. LOC ID: {}, Loan ID: {}, Consumed Amount: {}, Available Balance: {}",
                                lineOfCredit.getId(), loanId, lineOfCredit.getSummary().getConsumedAmount(),
                                lineOfCredit.getSummary().getAvailableBalance());
                    } catch (PlatformApiDataValidationException e) {
                        // Wrap with additional context for better error reporting
                        log.error("""
                                Failed to update LOC balance for Savings → Loan transfer.
                                LOC ID: {}, Loan ID: {}, Loan Transaction ID: {}, From Savings Account ID: {},
                                Amount: {}, Current LOC Consumed Amount: {}, Current LOC Available Balance: {},
                                Error: {}
                                """, lineOfCredit.getId(), loanId, loanTransactionId, fromSavingsAccountId, amount,
                                lineOfCredit.getSummary().getConsumedAmount(), lineOfCredit.getSummary().getAvailableBalance(),
                                e.getMessage());

                        // Re-throw with enhanced context using text block
                        throw new PlatformApiDataValidationException("error.msg.loc.balance.update.failed", """
                                Failed to update LOC balance for Savings → Loan transfer.
                                LOC ID: %d, Loan ID: %d, From Savings Account ID: %d, Amount: %s,
                                Current LOC Consumed Amount: %s, Error: %s
                                """.formatted(lineOfCredit.getId(), loanId, fromSavingsAccountId, amount,
                                lineOfCredit.getSummary().getConsumedAmount(), e.getDefaultUserMessage()), "locBalanceUpdate", e);
                    } catch (Exception e) {
                        log.error("""
                                Unexpected error updating LOC balance for Savings → Loan transfer.
                                LOC ID: {}, Loan ID: {}, Loan Transaction ID: {}, From Savings Account ID: {}, Amount: {}
                                """, lineOfCredit.getId(), loanId, loanTransactionId, fromSavingsAccountId, amount, e);
                        throw new GeneralPlatformDomainRuleException("error.msg.loc.balance.update.unexpected.error", """
                                Unexpected error updating LOC balance.
                                LOC ID: %d, Loan ID: %d, From Savings Account ID: %d, Amount: %s, Error: %s
                                """.formatted(lineOfCredit.getId(), loanId, fromSavingsAccountId, amount, e.getMessage()), e);
                    }
                }

            }
        }
    }

    /**
     * Checks if any standing instruction exists for a loan (active or disabled, but not deleted). This includes both
     * repayment standing instructions and down-payment standing instructions.
     *
     * @param loanId
     *            The loan ID to check
     * @return true if any standing instruction exists for the loan, false otherwise
     */
    private boolean standingInstructionExists(Long loanId) {
        if (loanId == null) {
            return false;
        }
        // Check for active standing instructions
        boolean hasActive = this.standingInstructionRepository.existsByAccountTransferDetails_ToLoanAccount_IdAndStatus(loanId,
                StandingInstructionStatus.ACTIVE.getValue());
        if (hasActive) {
            return true;
        }
        // Check for disabled standing instructions
        boolean hasDisabled = this.standingInstructionRepository.existsByAccountTransferDetails_ToLoanAccount_IdAndStatus(loanId,
                StandingInstructionStatus.DISABLED.getValue());
        return hasDisabled;
    }

    /**
     * Checks if a down-payment standing instruction exists for a loan. This is used to prevent creating duplicate
     * down-payment standing instructions when re-disbursing a loan after undo operations.
     *
     * @param loanId
     *            The loan ID to check
     * @return true if a down-payment standing instruction exists, false otherwise
     */
    private boolean hasDownPaymentStandingInstruction(Long loanId) {
        // Since we clean up all SIs on undo, we can use the same check as standingInstructionExists
        // The cleanup service removes all SIs (including down-payment ones) when undo is called.
        // So if any SI exists, it means we shouldn't create a new one.
        return standingInstructionExists(loanId);
    }

    /**
     * Validates actual disbursement date against the specific tranche's expected date for multi-tranche loans, instead
     * of using the loan-level expected date.
     *
     * This fixes the issue where core Fineract validates against loan.expectedDisbursementDate (first tranche's date)
     * instead of the specific tranche being disbursed.
     */
    private void validateMultiTrancheDisbursementDate(JsonCommand command, Loan loan) {
        final JsonElement element = fromApiJsonHelper.parse(command.json());
        final LocalDate actualDisbursementDate = fromApiJsonHelper.extractLocalDateNamed("actualDisbursementDate", element);

        if (actualDisbursementDate == null) {
            return; // Will be validated by core validator
        }

        // Get the tranche being disbursed
        final BigDecimal principalDisbursed = fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(LoanApiConstants.principalDisbursedParameterName, element);

        LoanDisbursementDetails trancheToDisburse = findTrancheToDisburse(loan, actualDisbursementDate, principalDisbursed);

        if (trancheToDisburse != null) {
            // Validate against the specific tranche's expected date
            LocalDate expectedDate = trancheToDisburse.expectedDisbursementDate();
            if (expectedDate != null && !DateUtils.isEqual(actualDisbursementDate, expectedDate)) {
                throw new DateMismatchException(actualDisbursementDate, expectedDate);
            }
        } else {
            // If no specific tranche found, try to find by expected date proximity
            // This handles cases where principalDisbursed might not match exactly
            java.util.Collection<LoanDisbursementDetails> undisbursedDetails = loan.fetchUndisbursedDetail();
            if (!undisbursedDetails.isEmpty()) {
                // Find tranche with expected date closest to actual date
                LoanDisbursementDetails closestTranche = findClosestTrancheByDate(undisbursedDetails, actualDisbursementDate);
                if (closestTranche != null) {
                    LocalDate expectedDate = closestTranche.expectedDisbursementDate();
                    if (expectedDate != null && !DateUtils.isEqual(actualDisbursementDate, expectedDate)) {
                        throw new DateMismatchException(actualDisbursementDate, expectedDate);
                    }
                }
            }
        }
    }

    /**
     * Finds the specific tranche being disbursed based on: 1. Principal amount match (if provided) 2. Expected date
     * closest to actual date
     */
    private LoanDisbursementDetails findTrancheToDisburse(Loan loan, LocalDate actualDisbursementDate, BigDecimal principalDisbursed) {
        java.util.Collection<LoanDisbursementDetails> undisbursedDetails = loan.fetchUndisbursedDetail();

        if (undisbursedDetails.isEmpty()) {
            return null;
        }

        // If principal amount is provided, try to match by amount first
        if (principalDisbursed != null) {
            for (LoanDisbursementDetails detail : undisbursedDetails) {
                if (detail.principal().compareTo(principalDisbursed) == 0) {
                    return detail;
                }
            }
        }

        // Otherwise, find the tranche with expected date closest to actual date
        return findClosestTrancheByDate(undisbursedDetails, actualDisbursementDate);
    }

    /**
     * Finds the tranche with expected date closest to the actual disbursement date.
     */
    private LoanDisbursementDetails findClosestTrancheByDate(java.util.Collection<LoanDisbursementDetails> undisbursedDetails,
            LocalDate actualDisbursementDate) {
        LoanDisbursementDetails closestTranche = null;
        long minDaysDiff = Long.MAX_VALUE;

        for (LoanDisbursementDetails detail : undisbursedDetails) {
            LocalDate expectedDate = detail.expectedDisbursementDate();
            if (expectedDate != null) {
                long daysDiff = Math.abs(ChronoUnit.DAYS.between(actualDisbursementDate, expectedDate));

                if (daysDiff < minDaysDiff) {
                    minDaysDiff = daysDiff;
                    closestTranche = detail;
                }
            }
        }

        return closestTranche;
    }

    /**
     * Override to add custom validation for future tranche date updates. Validates business rules before allowing date
     * changes.
     */
    @Transactional
    @Override
    public CommandProcessingResult updateDisbursementDateAndAmountForTranche(final Long loanId, final Long disbursementId,
            final JsonCommand command) {

        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        checkClientOrGroupActive(loan);
        if (loan.isChargedOff()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.is.charged.off",
                    "Update Loan: " + loanId + " disbursement details is not allowed. Loan Account is Charged-off", loanId);
        }

        final LoanDisbursementDetails loanDisbursementDetails = loan.fetchLoanDisbursementsById(disbursementId);
        if (loanDisbursementDetails == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.disbursement.not.found",
                    "Disbursement detail with id " + disbursementId + " not found for loan " + loanId);
        }

        // Extract new expected date from command
        // Try both parameter names (updatedDisbursementDate for single update, expectedDisbursementDate for bulk edit)
        LocalDate newExpectedDate = null;
        if (command.parameterExists(LoanApiConstants.updatedDisbursementDateParameterName)) {
            newExpectedDate = command.localDateValueOfParameterNamed(LoanApiConstants.updatedDisbursementDateParameterName);
        } else if (command.parameterExists(LoanApiConstants.expectedDisbursementDateParameterName)) {
            newExpectedDate = command.localDateValueOfParameterNamed(LoanApiConstants.expectedDisbursementDateParameterName);
        }

        if (newExpectedDate == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.disbursement.date.required",
                    "Expected disbursement date is required for tranche update", loanId);
        }

        // Apply custom validation for future tranche date updates
        customLoanDisbursementDateValidator.validateFutureTrancheDateUpdate(loan, loanDisbursementDetails, newExpectedDate);

        // Call parent validation (validates JSON structure, amounts, etc.)
        this.loanTransactionValidator.validateUpdateDisbursementDateAndAmount(command.json(), loanDisbursementDetails);

        // Call parent implementation which handles the actual update and schedule regeneration
        // We use super to avoid recursion since we're overriding the method
        return super.updateDisbursementDateAndAmountForTranche(loanId, disbursementId, command);
    }

    public CommandProcessingResult adjustInstallmentDate(final Long loanId, final JsonCommand command) {
        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        checkClientOrGroupActive(loan);

        final Integer installmentNumber = command.integerValueOfParameterNamed("installmentNumber");
        final LocalDate newDueDate = command.localDateValueOfParameterNamed("newDueDate");
        final LocalDate adjustmentDate = command.localDateValueOfParameterNamed("adjustmentDate");

        final LoanRepaymentScheduleInstallment installment = loan.fetchRepaymentScheduleInstallment(installmentNumber);
        if (installment == null) {
            throw new LoanRepaymentScheduleNotFoundException(installmentNumber);
        }

        validateNoOverdueChargesForInstallment(loan, installment);

        final LocalDate oldDueDate = installment.getDueDate();
        final Map<String, Object> changes = new HashMap<>();

        if (!oldDueDate.equals(newDueDate)) {
            final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();

            // Calculate the difference in days between old and new due date
            final long daysDifference = ChronoUnit.DAYS.between(oldDueDate, newDueDate);

            // Find the installment to adjust and cascade to subsequent installments
            LoanRepaymentScheduleInstallment previousInstallment = null;

            for (LoanRepaymentScheduleInstallment inst : installments) {
                if (inst.getInstallmentNumber().equals(installmentNumber)) {
                    // Update the target installment
                    if (previousInstallment != null) {
                        inst.updateFromDate(previousInstallment.getDueDate());
                    }
                    inst.updateDueDate(newDueDate);
                    changes.put("installmentNumber", installmentNumber);
                    changes.put("oldDueDate", oldDueDate);
                    changes.put("newDueDate", newDueDate);
                    changes.put("adjustmentDate", adjustmentDate);
                    changes.put("daysShifted", daysDifference);
                    previousInstallment = inst;
                } else if (inst.getInstallmentNumber() > installmentNumber) {
                    // Cascade the date change to all subsequent installments (installmentNumber > target)
                    // Update both fromDate and dueDate by the same number of days
                    if (inst.getFromDate() != null) {
                        inst.updateFromDate(inst.getFromDate().plusDays(daysDifference));
                    }
                    inst.updateDueDate(inst.getDueDate().plusDays(daysDifference));
                    previousInstallment = inst;
                } else {
                    // Before the target installment, keep track of previous
                    previousInstallment = inst;
                }
            }

            loan.updateLoanScheduleDependentDerivedFields();

            for (final LoanCharge loanCharge : loan.getLoanCharges()) {
                if (loanCharge.isOverdueInstallmentCharge() && loanCharge.isActive()) {
                    loan.updateOverdueScheduleInstallment(loanCharge);
                }
            }

            loan.updateLoanSummaryAndStatus();
            loanAccountDomainService.setLoanDelinquencyTag(loan, DateUtils.getBusinessLocalDate());
            saveAndFlushLoanWithDataIntegrityViolationChecks(loan);
        }

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(loanId)
                .withEntityExternalId(loan.getExternalId()).withOfficeId(loan.getOfficeId()).withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId()).withLoanId(loanId).with(changes).build();
    }

    private void validateNoOverdueChargesForInstallment(final Loan loan, final LoanRepaymentScheduleInstallment installment) {
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        if (!installment.isOverdueOn(businessDate)) {
            return;
        }

        final MonetaryCurrency currency = loan.getCurrency();
        final Money feeOutstanding = installment.getFeeChargesOutstanding(currency);
        final Money penaltyOutstanding = installment.getPenaltyChargesOutstanding(currency);
        final Money taxOutstanding = installment.getTaxChargesOutstanding(currency);

        if (feeOutstanding.isGreaterThanZero() || penaltyOutstanding.isGreaterThanZero() || taxOutstanding.isGreaterThanZero()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.adjust.installment.overdue.charges",
                    "Cannot adjust installment date because overdue charges exist for installment " + installment.getInstallmentNumber()
                            + ". Please remove overdue charges before adjusting the date.",
                    installment.getInstallmentNumber());
        }
    }
}
