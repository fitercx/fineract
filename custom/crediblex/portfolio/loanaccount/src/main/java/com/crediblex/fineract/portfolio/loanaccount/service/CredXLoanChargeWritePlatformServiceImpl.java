package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.commands.LineOfCreditStatusWebhookPublisher;
import com.crediblex.fineract.commands.LoanStatusWebhookPublisher;
import com.crediblex.fineract.infrastructure.commands.utils.LoanTransactionInstallmentUtils;
import com.crediblex.fineract.portfolio.loanaccount.data.CredXLoanOverdueDTO;
import com.crediblex.fineract.portfolio.loanaccount.data.LocStatusAggregationData;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loanaccount.repository.CustomLoanChargeRepository;
import com.crediblex.fineract.portfolio.loanaccount.util.LoanChargeSettlementUtils;
import com.crediblex.fineract.portfolio.loanaccount.util.LocStatusAggregationUtils;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanApplyOverdueChargeBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanBalanceChangedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.charge.LoanUpdateChargeBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.charge.LoanWaiveChargeBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanChargeAdjustmentPostBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.account.data.PortfolioAccountData;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBePayedException;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBeWaivedException;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByData;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOverdueInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleProcessingWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRelation;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRelationTypeEnum;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.exception.LoanChargeAdjustmentException;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.ScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeApiJsonValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualTransactionBusinessEventService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanArrearsAgingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformServiceImpl;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.loanaccount.service.adjustment.LoanAdjustmentService;
import org.apache.fineract.portfolio.loanproduct.data.LoanOverdueDTO;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionSummaryWrapper;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@Primary
public class CredXLoanChargeWritePlatformServiceImpl extends LoanChargeWritePlatformServiceImpl
        implements CredXLoanChargeWritePlatformService {

    private final LoanChargeApiJsonValidator loanChargeApiJsonValidator;
    private final ExternalIdFactory externalIdFactory;
    private final LoanAssembler loanAssembler;
    private final ChargeRepositoryWrapper chargeRepository;
    private final LoanChargeAssembler loanChargeAssembler;
    private final LoanWritePlatformService loanWritePlatformService;
    private final LoanScheduleService loanScheduleService;
    private final PaymentDetailWritePlatformService paymentDetailWritePlatformService;
    private final LoanAccountDomainService loanAccountDomainService;
    private final BusinessEventNotifierService businessEventNotifierService;
    private final NoteRepository noteRepository;
    private final LoanChargeRepository loanChargeRepository;
    private final LoanAccountService loanAccountService;
    private final LoanChargeValidator loanChargeValidator;
    private final LoanLifecycleStateMachine defaultLoanLifecycleStateMachine;
    private final LoanAccrualsProcessingService loanAccrualsProcessingService;
    private final LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService;
    private final ConfigurationDomainService configurationDomainService;
    private final CustomLoanChargeRepository customLoanChargeRepository;
    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;
    private final LineOfCreditRepository lineOfCreditRepository;
    private final LoanStatusWebhookPublisher loanStatusWebhookPublisher;
    private final LineOfCreditStatusWebhookPublisher lineOfCreditStatusWebhookPublisher;
    private final LocStatusAggregationUtils locStatusAggregationUtils;
    private final TransactionTemplate transactionTemplate;
    private final ReprocessLoanTransactionsService reprocessLoanTransactionsService;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final AccountAssociationsReadPlatformService accountAssociationsReadPlatformService;
    private final AccountTransfersWritePlatformService accountTransfersWritePlatformService;
    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    private final PaymentTypeReadPlatformService paymentTypeReadPlatformService;
    private final FromJsonHelper fromJsonHelper;
    private final GLAccountRepository glAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final OfficeRepositoryWrapper officeRepositoryWrapper;
    private final SavingsAccountTransactionRepository savingsAccountTransactionRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper;
    private final LoanArrearsAgingService loanArrearsAgingService;
    private final LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator;

    public CredXLoanChargeWritePlatformServiceImpl(LoanChargeApiJsonValidator loanChargeApiJsonValidator, LoanAssembler loanAssembler,
            ChargeRepositoryWrapper chargeRepository, BusinessEventNotifierService businessEventNotifierService,
            LoanTransactionRepository loanTransactionRepository, AccountTransfersWritePlatformService accountTransfersWritePlatformService,
            LoanRepositoryWrapper loanRepositoryWrapper, JournalEntryWritePlatformService journalEntryWritePlatformService,
            LoanAccountDomainService loanAccountDomainService, @Qualifier("loanChargeRepository") LoanChargeRepository loanChargeRepository,
            @Lazy LoanWritePlatformService loanWritePlatformService, LoanUtilService loanUtilService,
            LoanChargeReadPlatformService loanChargeReadPlatformService, LoanLifecycleStateMachine defaultLoanLifecycleStateMachine,
            AccountAssociationsReadPlatformService accountAssociationsReadPlatformService, FromJsonHelper fromApiJsonHelper,
            ConfigurationDomainService configurationDomainService,
            LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
            ExternalIdFactory externalIdFactory, AccountTransferDetailRepository accountTransferDetailRepository,
            LoanChargeAssembler loanChargeAssembler, PaymentDetailWritePlatformService paymentDetailWritePlatformService,
            NoteRepository noteRepository, LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService,
            LoanAccrualsProcessingService loanAccrualsProcessingService,
            LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator, LoanChargeValidator loanChargeValidator,
            LoanScheduleService loanScheduleService, ReprocessLoanTransactionsService reprocessLoanTransactionsService,
            LoanAccountService loanAccountService, LoanAdjustmentService loanAdjustmentService,
            LoanAccountingBridgeMapper loanAccountingBridgeMapper, LoanChargeValidator loanChargeValidator1,
            LoanLifecycleStateMachine defaultLoanLifecycleStateMachine1, LoanAccrualsProcessingService loanAccrualsProcessingService1,
            LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService1,
            CustomLoanChargeRepository customLoanChargeRepository,
            // New injections for custom loan/loc status/webhook handling
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository, LineOfCreditRepository lineOfCreditRepository,
            LoanStatusWebhookPublisher loanStatusWebhookPublisher, LineOfCreditStatusWebhookPublisher lineOfCreditStatusWebhookPublisher,
            LocStatusAggregationUtils locStatusAggregationUtils, PlatformTransactionManager platformTransactionManager,
            GLAccountRepository glAccountRepository, JournalEntryRepository journalEntryRepository,
            OfficeRepositoryWrapper officeRepositoryWrapper, SavingsAccountWritePlatformService savingsAccountWritePlatformService,
            PaymentTypeReadPlatformService paymentTypeReadPlatformService,
            SavingsAccountTransactionRepository savingsAccountTransactionRepository, SavingsAccountRepository savingsAccountRepository,
            SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper,
            LoanArrearsAgingService loanArrearsAgingService) {

        super(loanChargeApiJsonValidator, loanAssembler, chargeRepository, businessEventNotifierService, loanTransactionRepository,
                accountTransfersWritePlatformService, loanRepositoryWrapper, journalEntryWritePlatformService, loanAccountDomainService,
                loanChargeRepository, loanWritePlatformService, loanUtilService, loanChargeReadPlatformService,
                defaultLoanLifecycleStateMachine, accountAssociationsReadPlatformService, fromApiJsonHelper, configurationDomainService,
                loanRepaymentScheduleTransactionProcessorFactory, externalIdFactory, accountTransferDetailRepository, loanChargeAssembler,
                paymentDetailWritePlatformService, noteRepository, loanAccrualTransactionBusinessEventService,
                loanAccrualsProcessingService, loanDownPaymentTransactionValidator, loanChargeValidator, loanScheduleService,
                reprocessLoanTransactionsService, loanAccountService, loanAdjustmentService, loanAccountingBridgeMapper);

        this.loanChargeApiJsonValidator = loanChargeApiJsonValidator;
        this.externalIdFactory = externalIdFactory;
        this.loanAssembler = loanAssembler;
        this.chargeRepository = chargeRepository;
        this.loanChargeAssembler = loanChargeAssembler;
        this.loanWritePlatformService = loanWritePlatformService;
        this.loanScheduleService = loanScheduleService;
        this.paymentDetailWritePlatformService = paymentDetailWritePlatformService;
        this.loanAccountDomainService = loanAccountDomainService;
        this.businessEventNotifierService = businessEventNotifierService;
        this.noteRepository = noteRepository;
        this.loanChargeRepository = loanChargeRepository;
        this.loanAccountService = loanAccountService;
        this.loanChargeValidator = loanChargeValidator1;
        this.defaultLoanLifecycleStateMachine = defaultLoanLifecycleStateMachine1;
        this.loanAccrualsProcessingService = loanAccrualsProcessingService1;
        this.loanAccrualTransactionBusinessEventService = loanAccrualTransactionBusinessEventService1;
        this.configurationDomainService = configurationDomainService;
        this.customLoanChargeRepository = customLoanChargeRepository;
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
        this.lineOfCreditRepository = lineOfCreditRepository;
        this.loanStatusWebhookPublisher = loanStatusWebhookPublisher;
        this.lineOfCreditStatusWebhookPublisher = lineOfCreditStatusWebhookPublisher;
        this.locStatusAggregationUtils = locStatusAggregationUtils;
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.reprocessLoanTransactionsService = reprocessLoanTransactionsService;
        this.loanRepositoryWrapper = loanRepositoryWrapper;
        this.accountAssociationsReadPlatformService = accountAssociationsReadPlatformService;
        this.accountTransfersWritePlatformService = accountTransfersWritePlatformService;
        this.savingsAccountWritePlatformService = savingsAccountWritePlatformService;
        this.paymentTypeReadPlatformService = paymentTypeReadPlatformService;
        this.savingsAccountTransactionRepository = savingsAccountTransactionRepository;
        this.savingsAccountRepository = savingsAccountRepository;
        this.savingsAccountTransactionSummaryWrapper = savingsAccountTransactionSummaryWrapper;
        this.fromJsonHelper = fromApiJsonHelper;
        this.glAccountRepository = glAccountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.officeRepositoryWrapper = officeRepositoryWrapper;
        this.loanArrearsAgingService = loanArrearsAgingService;
        this.loanDownPaymentTransactionValidator = loanDownPaymentTransactionValidator;
    }

    @Override
    @Transactional
    public CommandProcessingResult adjustmentForLoanCharge(Long loanId, Long loanChargeId, JsonCommand command) {
        this.loanChargeApiJsonValidator.validateLoanChargeAdjustmentRequest(loanId, loanChargeId, command.json());

        final LoanCharge loanCharge = retrieveLoanChargeBy(loanId, loanChargeId);
        final LocalDate transactionDate = DateUtils.getBusinessLocalDate();
        final BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed("amount");
        final ExternalId externalId = externalIdFactory.createFromCommand(command, "externalId");
        final String locale = command.locale();

        Map<String, Object> changes = new HashMap<>();
        changes.put("externalId", externalId);
        changes.put("amount", transactionAmount);
        changes.put("transactionDate", transactionDate);
        changes.put("locale", locale);

        loanChargeAdjustmentEntranceValidationSafe(loanCharge, transactionAmount);
        final Loan loan = loanAssembler.assembleFrom(loanId);

        final CommandProcessingResultBuilder commandProcessingResultBuilder = new CommandProcessingResultBuilder();
        PaymentDetail paymentDetail = this.paymentDetailWritePlatformService.createPaymentDetail(command, changes);
        if (paymentDetail != null) {
            paymentDetail = this.paymentDetailWritePlatformService.persistPaymentDetail(paymentDetail);
        }

        LoanTransaction loanTransaction = applyChargeAdjustment(loan, loanCharge, transactionAmount, transactionDate, externalId,
                paymentDetail);

        Money currentPaid = loanCharge.getAmountPaid(loan.getCurrency());
        Money totalAdjustments = Money.zero(loan.getCurrency());

        for (LoanTransaction transaction : loan.getLoanTransactions()) {
            if (transaction.isChargeAdjustment() && !transaction.isReversed()) {
                for (LoanTransactionRelation relation : transaction.getLoanTransactionRelations()) {
                    if (relation.getRelationType() == LoanTransactionRelationTypeEnum.CHARGE_ADJUSTMENT
                            && relation.getToCharge().equals(loanCharge)) {
                        totalAdjustments = totalAdjustments.plus(transaction.getAmount(loan.getCurrency()));
                    }
                }
            }
        }

        Money adjustment = totalAdjustments.minus(currentPaid);
        loanCharge.updatePaidAmountBy(adjustment, null, null);
        this.loanChargeRepository.save(loanCharge);

        this.loanAccountService.saveLoanTransactionWithDataIntegrityViolationChecks(loanTransaction);
        this.loanAccountService.saveAndFlushLoanWithDataIntegrityViolationChecks(loan);

        loanAccountDomainService.updateAndSaveLoanCollateralTransactionsForIndividualAccounts(loan, loanTransaction);

        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            final Note note = Note.loanNote(loan, noteText);
            changes.put("note", noteText);
            this.noteRepository.save(note);
        }

        businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));
        businessEventNotifierService.notifyPostBusinessEvent(new LoanChargeAdjustmentPostBusinessEvent(loanTransaction));

        return commandProcessingResultBuilder.withCommandId(command.commandId()).withLoanId(loanId).withEntityId(loanChargeId)
                .withEntityExternalId(loanCharge.getExternalId()).withSubEntityId(loanTransaction.getId())
                .withSubEntityExternalId(loanTransaction.getExternalId()).with(changes).build();
    }

    private void loanChargeAdjustmentEntranceValidationSafe(final LoanCharge loanCharge, final BigDecimal transactionAmount) {
        final Loan loan = loanCharge.getLoan();
        if (!(loan.isOpen() || loan.getStatus().isClosedObligationsMet() || loan.getStatus().isOverpaid())) {
            final String errorCode = "loan.charge.adjustment.invalid.status";
            throw new LoanChargeAdjustmentException(errorCode,
                    "Adjustment is not supported for the status of " + loan.getStatus().toString());
        }

        // A charge with nothing outstanding (already fully waived and/or fully paid) has nothing left to
        // adjust. Without this guard, calculateAvailableAmountForChargeAdjustmentSafe still reported the
        // charge's full original amount (minus prior adjustment transactions only) as "available", letting
        // an already-waived charge be adjusted for real, GL-posting income with zero trace on the charge
        // itself (amountOutstanding stays 0 because updatePaidAmountBy caps the paid delta at outstanding).
        if (loanCharge.isWaived() || loanCharge.isPaid()) {
            final String errorCode = "loan.charge.adjustment.invalid.status";
            throw new LoanChargeAdjustmentException(errorCode,
                    "Charge with id:" + loanCharge.getId() + " has nothing outstanding to adjust (already waived or paid).");
        }

        if (transactionAmount.compareTo(loanCharge.amount()) > 0) {
            final String errorCode = "loan.charge.adjustment.invalid.amount";
            throw new LoanChargeAdjustmentException(errorCode,
                    "Transaction amount cannot be higher than the charge amount: " + loanCharge.amount());
        }

        BigDecimal availableAmountForAdjustment = calculateAvailableAmountForChargeAdjustmentSafe(loanCharge);
        if (transactionAmount.compareTo(availableAmountForAdjustment) > 0) {
            final String errorCode = "loan.charge.adjustment.invalid.amount";
            throw new LoanChargeAdjustmentException(errorCode,
                    "Transaction amount cannot be higher than the available charge amount for adjustment: " + availableAmountForAdjustment);
        }
        checkClientOrGroupActive(loan);
        loanDownPaymentTransactionValidator.validateAccountStatus(loan, LoanEvent.LOAN_CHARGE_ADJUSTMENT);
    }

    private BigDecimal calculateAvailableAmountForChargeAdjustmentSafe(final LoanCharge loanCharge) {
        BigDecimal availableAmountForAdjustment = loanCharge.amount();
        for (LoanTransaction loanTransaction : loanCharge.getLoan().getLoanTransactions()) {
            if (loanTransaction.isNotReversed() && loanTransaction.getTypeOf().isChargeAdjustment()) {
                for (LoanTransactionRelation loanTransactionRelation : loanTransaction.getLoanTransactionRelations()) {
                    if (loanTransactionRelation.getToCharge() != null && loanCharge.equals(loanTransactionRelation.getToCharge())) {
                        availableAmountForAdjustment = availableAmountForAdjustment.subtract(loanTransaction.getAmount());
                    }
                }
            }
        }
        // Never report more available than what is genuinely still outstanding on the charge today. The
        // loop above only accounts for prior Charge Adjustment transactions; it does not know about amounts
        // already waived or paid through other paths (waive, normal repayment), which is what let adjustments
        // slip through on already-fully-waived charges.
        final BigDecimal amountOutstanding = loanCharge.getAmountOutstanding(loanCharge.getLoan().getCurrency()).getAmount();
        if (amountOutstanding.compareTo(availableAmountForAdjustment) < 0) {
            availableAmountForAdjustment = amountOutstanding;
        }
        return availableAmountForAdjustment;
    }

    @Transactional
    @Override
    public CommandProcessingResult waiveLoanCharge(final Long loanId, final Long loanChargeId, final JsonCommand command) {

        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        checkClientOrGroupActive(loan);
        this.loanChargeApiJsonValidator.validateInstallmentChargeTransaction(command.json());
        final ExternalId externalId = externalIdFactory.createFromCommand(command, LoanApiConstants.externalIdParameterName);
        final LoanCharge loanCharge = retrieveLoanChargeBy(loanId, loanChargeId);

        // Charges may be waived only when the loan associated with them are
        // active
        if (!loan.getStatus().isActive()) {
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.LOAN_INACTIVE,
                    loanCharge.getId());
        }

        // validate loan charge is not already paid or waived
        if (loanCharge.isWaived()) {
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.ALREADY_WAIVED,
                    loanCharge.getId());
        } else if (loanCharge.isPaid()) {
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.ALREADY_PAID,
                    loanCharge.getId());
        }
        businessEventNotifierService.notifyPreBusinessEvent(new LoanWaiveChargeBusinessEvent(loanCharge));
        Integer loanInstallmentNumber = null;
        if (loanCharge.isInstalmentFee()) {
            LoanInstallmentCharge chargePerInstallment = null;
            if (!StringUtils.isBlank(command.json())) {
                final LocalDate dueDate = command.localDateValueOfParameterNamed("dueDate");
                final Integer installmentNumber = command.integerValueOfParameterNamed("installmentNumber");
                if (dueDate != null) {
                    chargePerInstallment = loanCharge.getInstallmentLoanCharge(dueDate);
                } else if (installmentNumber != null) {
                    chargePerInstallment = loanCharge.getInstallmentLoanCharge(installmentNumber);
                }
            }
            if (chargePerInstallment == null) {
                chargePerInstallment = loanCharge.getUnpaidInstallmentLoanCharge();
            }
            if (chargePerInstallment == null) {
                throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.ALREADY_PAID,
                        loanCharge.getId());
            }
            if (chargePerInstallment.isWaived()) {
                throw new LoanChargeCannotBePayedException(LoanChargeCannotBePayedException.LoanChargeCannotBePayedReason.ALREADY_WAIVED,
                        loanCharge.getId());
            } else if (chargePerInstallment.isPaid()) {
                throw new LoanChargeCannotBePayedException(LoanChargeCannotBePayedException.LoanChargeCannotBePayedReason.ALREADY_PAID,
                        loanCharge.getId());
            }
            loanInstallmentNumber = chargePerInstallment.getRepaymentInstallment().getInstallmentNumber();
        }

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(LoanApiConstants.externalIdParameterName, externalId);

        final List<Long> existingTransactionIds = new ArrayList<>();
        final List<Long> existingReversedTransactionIds = new ArrayList<>();
        LocalDate recalculateFrom = null;
        ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom);

        Money accruedCharge = Money.zero(loan.getCurrency());
        if (loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()) {
            Collection<LoanChargePaidByData> chargePaidByCollection = this.loanChargeReadPlatformService
                    .retrieveLoanChargesPaidBy(loanCharge.getId(), LoanTransactionType.ACCRUAL, loanInstallmentNumber);
            for (LoanChargePaidByData chargePaidByData : chargePaidByCollection) {
                accruedCharge = accruedCharge.plus(chargePaidByData.getAmount());
            }
        }

        loanChargeValidator.validateLoanIsNotClosed(loan, loanCharge);

        // Custom waiver logic to fix the bug where amount_paid_derived is not preserved
        final LoanTransaction waiveTransaction = customWaiveLoanCharge(loan, loanCharge, defaultLoanLifecycleStateMachine, changes,
                existingTransactionIds, existingReversedTransactionIds, loanInstallmentNumber, scheduleGeneratorDTO, accruedCharge,
                externalId);

        // Safety net: keep repayment schedule charge portions aligned with active charges after single-charge waiver.
        if (hasRepaymentScheduleChargeMismatch(loan)) {
            log.warn("Detected charge mismatch after waiving charge {} on loan {}. Running full installment charge recalculation.",
                    loanChargeId, loanId);
            recalculateInstallmentChargesFromActiveLoanCharges(loan);
        }

        if (loan.isInterestBearingAndInterestRecalculationEnabled()
                && DateUtils.isBefore(loanCharge.getDueLocalDate(), DateUtils.getBusinessLocalDate())) {
            loanAccrualsProcessingService.reprocessExistingAccruals(loan);
            loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);

        }

        this.loanTransactionRepository.saveAndFlush(waiveTransaction);
        this.loanRepositoryWrapper.save(loan);

        postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds);
        this.loanAccountDomainService.setLoanDelinquencyTag(loan, DateUtils.getBusinessLocalDate());
        loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);
        businessEventNotifierService.notifyPostBusinessEvent(new LoanWaiveChargeBusinessEvent(loanCharge));
        businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));
        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(loanChargeId) //
                .withEntityExternalId(loanCharge.getExternalId()) //
                .withSubEntityId(waiveTransaction.getId()) //
                .withSubEntityExternalId(waiveTransaction.getExternalId()) //
                .withOfficeId(loan.getOfficeId()) //
                .withClientId(loan.getClientId()) //
                .withGroupId(loan.getGroupId()) //
                .withLoanId(loanId) //
                .with(changes) //
                .build();
    }

    /**
     * Custom implementation of waiveLoanCharge that fixes the bug where amount_paid_derived is not preserved when
     * waiving a charge that has been partially paid through adjustments.
     */
    private LoanTransaction customWaiveLoanCharge(final Loan loan, final LoanCharge loanCharge,
            final LoanLifecycleStateMachine loanLifecycleStateMachine, final Map<String, Object> changes,
            final List<Long> existingTransactionIds, final List<Long> existingReversedTransactionIds, final Integer loanInstallmentNumber,
            final ScheduleGeneratorDTO scheduleGeneratorDTO, final Money accruedCharge, final ExternalId externalId) {

        // Get the current outstanding amount to be waived
        Money amountOutstanding = loanCharge.getAmountOutstanding(loan.getCurrency());

        // HARD GUARD: a charge with nothing outstanding has nothing to waive. Without this, a repeat waive (e.g. a
        // double-click in the UI, possible because isWaived() requires the taxesWaived flag that older waives never
        // set) would overwrite the previously waived amount with the now-zero outstanding amount, silently losing it.
        if (!loanCharge.isInstalmentFee() && !amountOutstanding.isGreaterThanZero()) {
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.ALREADY_WAIVED,
                    loanCharge.getId());
        }

        // Custom waiver logic that preserves the amountPaid
        if (loanCharge.isInstalmentFee()) {
            // For installment fees, handle the waiver manually
            final LoanInstallmentCharge chargePerInstallment = loanCharge.getInstallmentLoanCharge(loanInstallmentNumber);
            if (chargePerInstallment == null) {
                throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.ALREADY_PAID,
                        loanCharge.getId());
            }
            final Money installmentAmountWaived = chargePerInstallment.waive(loan.getCurrency());

            // Update the parent charge's waived amount by adding the installment waived amount
            if (loanCharge.getAmountWaived(loan.getCurrency()).getAmount() == null) {
                loanCharge.setAmountWaived(BigDecimal.ZERO);
            }
            BigDecimal currentWaived = loanCharge.getAmountWaived(loan.getCurrency()).getAmount();
            loanCharge.setAmountWaived(currentWaived.add(installmentAmountWaived.getAmount()));

            // Update outstanding amount
            BigDecimal currentOutstanding = loanCharge.getAmountOutstanding(loan.getCurrency()).getAmount();
            loanCharge.setOutstandingAmount(currentOutstanding.subtract(installmentAmountWaived.getAmount()));

            // Use updatePaidAmountBy with zero to trigger the waived flag setting logic
            // This will call the logic that sets this.waived = true when waivedAmount.isGreaterThanZero()
            loanCharge.updatePaidAmountBy(Money.zero(loan.getCurrency()), null, null);

        } else {
            // For non-installment fees, manually set the values to preserve amountPaid.
            // ADD the outstanding amount to any previously waived amount (never overwrite - overwriting loses prior
            // waivers if this method is ever reached twice for the same charge).
            final BigDecimal previouslyWaived = loanCharge.getAmountWaived(loan.getCurrency()).getAmount() != null
                    ? loanCharge.getAmountWaived(loan.getCurrency()).getAmount()
                    : BigDecimal.ZERO;
            loanCharge.setAmountWaived(previouslyWaived.add(amountOutstanding.getAmount()));
            loanCharge.setOutstandingAmount(BigDecimal.ZERO);

            // Use updatePaidAmountBy with zero to trigger the waived flag setting logic
            // This will call the logic that sets this.waived = true when waivedAmount.isGreaterThanZero()
            loanCharge.updatePaidAmountBy(Money.zero(loan.getCurrency()), null, null);
            // updatePaidAmountBy only sets the 'waived' flag; isWaived() additionally requires 'taxesWaived'. Set both
            // so the already-waived validation actually blocks repeat waives and the API reports waived=true to the UI.
            loanCharge.markAsFullyWaived();
        }

        Money amountWaived = loanCharge.getAmountWaived(loan.getCurrency());
        changes.put("amount", amountWaived.getAmount());

        Money unrecognizedIncome = amountWaived.zero();
        Money chargeComponent = amountWaived;
        if (loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()) {
            Money receivableCharge;
            if (loanInstallmentNumber != null) {
                receivableCharge = accruedCharge
                        .minus(loanCharge.getInstallmentLoanCharge(loanInstallmentNumber).getAmountPaid(loan.getCurrency()));
            } else {
                receivableCharge = accruedCharge.minus(loanCharge.getAmountPaid(loan.getCurrency()));
            }
            if (receivableCharge.isLessThanZero()) {
                receivableCharge = amountWaived.zero();
            }
            if (amountWaived.isGreaterThan(receivableCharge)) {
                chargeComponent = receivableCharge;
                unrecognizedIncome = amountWaived.minus(receivableCharge);
            }
        }
        Money feeChargesWaived = chargeComponent;
        Money penaltyChargesWaived = Money.zero(loan.getCurrency());
        if (loanCharge.isPenaltyCharge()) {
            penaltyChargesWaived = chargeComponent;
            feeChargesWaived = Money.zero(loan.getCurrency());
        }

        LocalDate transactionDate = loan.getDisbursementDate();
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        if (loanCharge.isDueDateCharge()) {
            if (DateUtils.isAfter(loanCharge.getDueLocalDate(), businessDate)) {
                transactionDate = businessDate;
            } else {
                transactionDate = loanCharge.getDueLocalDate();
            }
        } else if (loanCharge.isInstalmentFee()) {
            LocalDate repaymentDueDate = loanCharge.getInstallmentLoanCharge(loanInstallmentNumber).getRepaymentInstallment().getDueDate();
            if (DateUtils.isAfter(repaymentDueDate, businessDate)) {
                transactionDate = businessDate;
            } else {
                transactionDate = repaymentDueDate;
            }
        }

        scheduleGeneratorDTO.setRecalculateFrom(transactionDate);

        loan.updateSummaryWithTotalFeeChargesDueAtDisbursement(loan.deriveSumTotalOfChargesDueAtDisbursement());

        existingTransactionIds.addAll(loan.findExistingTransactionIds());
        existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());

        final LoanTransaction waiveLoanChargeTransaction = LoanTransaction.waiveLoanCharge(loan, loan.getOffice(), amountWaived,
                transactionDate, feeChargesWaived, penaltyChargesWaived, unrecognizedIncome, externalId);
        // IMPORTANT: use chargeComponent (== feeChargesWaived/penaltyChargesWaived), not the full amountWaived, here.
        // The transaction's feeChargesPortion/penaltyChargesPortion only reflect the "recognized" component
        // (amountWaived minus unrecognizedIncome, see updateChargesComponents above); if the charge is waived before
        // it has been fully accrued (periodic accrual accounting + a charge waived same-day, before the nightly
        // accrual job runs — e.g. our backdated-settlement LPI auto-waiver), amountWaived > chargeComponent. Using
        // the full amountWaived here would make sum(loanChargesPaid.amount) != transaction.feeChargesPortion, which
        // AccountingProcessorHelper.createJournalEntriesForLoanCharges rejects with "Meltdown in advanced
        // accounting...sum of all charges is not equal to the fee charge for a transaction".
        final LoanChargePaidBy loanChargePaidBy = new LoanChargePaidBy(waiveLoanChargeTransaction, loanCharge, chargeComponent.getAmount(),
                loanInstallmentNumber);
        waiveLoanChargeTransaction.getLoanChargesPaid().add(loanChargePaidBy);
        loan.addLoanTransaction(waiveLoanChargeTransaction);

        // Reprocess loan schedule/transactions so waived component stays on the correct installment.
        // This mirrors core behavior and prevents overdue charge portions from drifting to another EMI.
        if (!loanCharge.isDueAtDisbursement() && loanCharge.isPaidOrPartiallyPaid(loan.getCurrency())) {
            reprocessLoanTransactionsService.reprocessTransactions(loan);
        } else {
            final LoanRepaymentScheduleProcessingWrapper wrapper = new LoanRepaymentScheduleProcessingWrapper();
            wrapper.reprocess(loan.getCurrency(), loan.getDisbursementDate(), loan.getRepaymentScheduleInstallments(),
                    loan.getActiveCharges());
        }

        loan.updateLoanSummaryDerivedFields();
        loan.doPostLoanTransactionChecks(waiveLoanChargeTransaction.getTransactionDate(), loanLifecycleStateMachine);
        LoanChargeSettlementUtils.closeIfFullySettled(loan, waiveLoanChargeTransaction.getTransactionDate(), loanLifecycleStateMachine);

        return waiveLoanChargeTransaction;
    }

    @Override
    @Transactional
    public CommandProcessingResult deactivateOverdueLoanCharge(Long loanId, JsonCommand command) {
        LocalDate fromDueDate = command.dateValueOfParameterNamed("dueDate");
        LocalDate toDueDate = command.dateValueOfParameterNamed("toDueDate");
        boolean removeCompleteEmiOverdue = command.hasParameter("removeCompleteEmiOverdue")
                && command.booleanPrimitiveValueOfParameterNamed("removeCompleteEmiOverdue");
        if (fromDueDate == null && toDueDate != null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.charge.deactivate.overdue.invalid.date.filter",
                    "Start date (dueDate) is required when end date (toDueDate) is provided.");
        }
        if (fromDueDate != null && toDueDate != null && toDueDate.isBefore(fromDueDate)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.charge.deactivate.overdue.invalid.date.range",
                    "End date (toDueDate) cannot be before start date (dueDate).");
        }
        // Determine removal mode:
        // - removeCompleteEmiOverdue=true with no dates = Remove all overdue charges for all EMIs that have overdue
        // charges
        // - fromDueDate=null and removeCompleteEmiOverdue=false = Remove ALL overdue charges
        // - fromDueDate provided = Date-based removal
        boolean isRemoveAll = (fromDueDate == null && !removeCompleteEmiOverdue);
        boolean isRemoveEmiOnly = (fromDueDate == null && removeCompleteEmiOverdue);

        List<LoanCharge> loanCharges;
        Integer overdueChargeTimeValue = ChargeTimeType.OVERDUE_INSTALLMENT.getValue();
        if (isRemoveAll) {
            // Remove all: get all active overdue charges
            loanCharges = customLoanChargeRepository.findAllActiveOverdueChargesByLoanId(loanId, overdueChargeTimeValue);
        } else if (isRemoveEmiOnly) {
            // Remove complete EMI overdue charges: get all active overdue charges (we'll filter by EMI later)
            loanCharges = customLoanChargeRepository.findAllActiveOverdueChargesByLoanId(loanId, overdueChargeTimeValue);
        } else if (toDueDate != null) {
            // Date range: get charges within the range
            loanCharges = customLoanChargeRepository.findByLoanIdAndDueDateRange(loanId, fromDueDate, toDueDate, overdueChargeTimeValue);
        } else {
            // Single date: get charges for exact date (toDueDate will be set to same as fromDueDate by UI)
            loanCharges = customLoanChargeRepository.findByLoanIdAndDueDateRange(loanId, fromDueDate, fromDueDate, overdueChargeTimeValue);
        }

        // Build schedule installments for robust overdue-charge -> EMI mapping.
        // Some overdue charges may not carry LoanOverdueInstallmentCharge relation, so we must fallback to due-date
        // window mapping.
        Loan loanForMapping = loanAssembler.assembleFrom(loanId);
        List<LoanRepaymentScheduleInstallment> scheduleInstallmentsForMapping = new ArrayList<>(
                loanForMapping.getRepaymentScheduleInstallments());
        scheduleInstallmentsForMapping.sort((left, right) -> {
            if (left == null || right == null) {
                return 0;
            }
            if (left.getInstallmentNumber() != null && right.getInstallmentNumber() != null) {
                return left.getInstallmentNumber().compareTo(right.getInstallmentNumber());
            }
            if (left.getDueDate() != null && right.getDueDate() != null) {
                return left.getDueDate().compareTo(right.getDueDate());
            }
            return 0;
        });

        // EMI-only mode: remove all overdue charges for selected EMIs (or all EMIs if none selected)
        // NOTE: Date-based removal should NOT expand to all EMI charges - only remove charges for the specific date(s)
        if (isRemoveEmiOnly && !loanCharges.isEmpty()) {
            // Check if specific EMI numbers are provided
            Set<Integer> selectedEmiNumbers = new HashSet<>();
            if (command.hasParameter("selectedEmiNumbers")) {
                JsonArray emiNumbersArray = command.arrayOfParameterNamed("selectedEmiNumbers");
                if (emiNumbersArray != null && emiNumbersArray.size() > 0) {
                    for (JsonElement element : emiNumbersArray) {
                        if (element != null && !element.isJsonNull()) {
                            selectedEmiNumbers.add(element.getAsInt());
                        }
                    }
                    log.info("EMI-mode removal with specific EMI numbers: {}", selectedEmiNumbers);
                }
            }

            // Get all active overdue charges
            List<LoanCharge> allActiveOverdueCharges = customLoanChargeRepository.findAllActiveOverdueChargesByLoanId(loanId,
                    overdueChargeTimeValue);
            List<LoanCharge> expandedLoanCharges = new ArrayList<>();
            Set<Integer> impactedInstallmentNumbers = new HashSet<>();

            // If no specific EMIs are selected, derive impacted EMIs from the initial candidate set.
            if (selectedEmiNumbers.isEmpty()) {
                for (LoanCharge initialCharge : loanCharges) {
                    Integer resolvedInstallmentNumber = resolveInstallmentNumberForOverdueCharge(initialCharge,
                            scheduleInstallmentsForMapping);
                    if (resolvedInstallmentNumber != null) {
                        impactedInstallmentNumbers.add(resolvedInstallmentNumber);
                    }
                }
                log.info("EMI-mode without explicit selectedEmiNumbers. Derived impacted installment numbers: {}",
                        impactedInstallmentNumbers);
            }

            for (LoanCharge charge : allActiveOverdueCharges) {
                if (!charge.isOverdueInstallmentCharge()) {
                    continue;
                }
                Integer resolvedInstallmentNumber = resolveInstallmentNumberForOverdueCharge(charge, scheduleInstallmentsForMapping);
                if (resolvedInstallmentNumber == null) {
                    log.debug("Skipping overdue charge {} in EMI-mode because no installment could be resolved (dueDate: {})",
                            charge.getId(), charge.getDueDate());
                    continue;
                }

                boolean includeCharge = false;

                if (!selectedEmiNumbers.isEmpty()) {
                    // If specific EMIs are selected, only include charges for those EMIs
                    includeCharge = selectedEmiNumbers.contains(resolvedInstallmentNumber);
                } else {
                    // If no specific EMIs selected, include all charges from derived impacted EMIs
                    includeCharge = impactedInstallmentNumbers.contains(resolvedInstallmentNumber);
                }

                if (includeCharge) {
                    expandedLoanCharges.add(charge);
                }
            }

            // Helpful diagnostics for production debugging in case UI-selected EMI doesn't map to any active charge.
            if (!selectedEmiNumbers.isEmpty() && expandedLoanCharges.isEmpty()) {
                Map<Integer, Integer> resolvedCountByInstallment = new HashMap<>();
                for (LoanCharge charge : allActiveOverdueCharges) {
                    if (!charge.isOverdueInstallmentCharge()) {
                        continue;
                    }
                    Integer resolved = resolveInstallmentNumberForOverdueCharge(charge, scheduleInstallmentsForMapping);
                    if (resolved != null) {
                        resolvedCountByInstallment.merge(resolved, 1, Integer::sum);
                    }
                }
                log.warn(
                        "Selected EMI numbers {} mapped to zero active overdue charges for loan {}. "
                                + "Resolved active overdue charge counts by installment: {}",
                        selectedEmiNumbers, loanId, resolvedCountByInstallment);
            }

            String emiSelectionLabel = selectedEmiNumbers.isEmpty() ? "ALL with overdue charges" : selectedEmiNumbers.toString();
            log.info("EMI-mode bulk removal enabled for loan {}. Expanded charge set from {} to {} charges for EMI(s): {}", loanId,
                    loanCharges.size(), expandedLoanCharges.size(), emiSelectionLabel);
            loanCharges = expandedLoanCharges;
        }
        // NOTE: Date-based removal (when removeCompleteEmiOverdue is false) should NOT expand - only remove charges for
        // the specific date(s)

        // CRITICAL: Collect affected installment numbers BEFORE deactivating charges
        // This ensures we can properly identify which installments need recalculation
        Set<Integer> affectedInstallmentNumbers = new HashSet<>();
        for (LoanCharge charge : loanCharges) {
            Integer resolvedInstallmentNumber = resolveInstallmentNumberForOverdueCharge(charge, scheduleInstallmentsForMapping);
            if (resolvedInstallmentNumber != null) {
                affectedInstallmentNumbers.add(resolvedInstallmentNumber);
            }
        }
        log.info("Charges to be deactivated will affect installments: {}", affectedInstallmentNumbers);

        // Track which charges were actually deactivated (collect their IDs for accounting reversal)
        // CRITICAL: Reload charges from repository to ensure we have managed entities
        List<LoanCharge> chargesToDeactivate = new ArrayList<>();
        for (LoanCharge charge : loanCharges) {
            if (charge.getId() != null) {
                loanChargeRepository.findById(charge.getId()).ifPresent(chargesToDeactivate::add);
            }
        }

        int totalChargesFound = loanCharges.size();
        int managedChargesFound = chargesToDeactivate.size();
        List<Long> deactivatedChargeIds = new ArrayList<>();
        List<Long> failedChargeIds = new ArrayList<>();

        log.info("Attempting to deactivate {} charges for loan {} (found {} managed entities)", totalChargesFound, loanId,
                managedChargesFound);

        for (LoanCharge charge : chargesToDeactivate) {
            Long chargeId = charge.getId();
            boolean wasActive = charge.isActive();
            boolean isOverdue = charge.getChargeTimeType() != null && charge.getChargeTimeType().isOverdueInstallment();

            Object installmentNum = charge.getOverdueInstallmentCharge() != null
                    && charge.getOverdueInstallmentCharge().getInstallment() != null
                            ? charge.getOverdueInstallmentCharge().getInstallment().getInstallmentNumber()
                            : "N/A";
            log.debug("Processing charge {}: active={}, isOverdue={}, installment={}", chargeId, wasActive, isOverdue, installmentNum);

            if (inactivateOverdueLoanCharge(charge)) {
                deactivatedChargeIds.add(chargeId);
                log.info("Successfully deactivated charge {} (was active: {})", chargeId, wasActive);
            } else {
                failedChargeIds.add(chargeId);
                log.warn("Failed to deactivate charge {} (was active: {}, isOverdue: {})", chargeId, wasActive, isOverdue);
            }
        }
        long deactivatedCount = deactivatedChargeIds.size();

        log.info("Found {} overdue charges for loan {}, successfully deactivated {} (isRemoveAll: {}). Failed: {}", totalChargesFound,
                loanId, deactivatedCount, isRemoveAll, failedChargeIds);

        // Reload loan to get updated state
        Loan loan = loanAssembler.assembleFrom(loanId);

        // Only update loan if we actually deactivated any charges
        if (deactivatedCount > 0) {
            try {
                // Find and reverse accrual transactions linked to the deactivated charges
                List<Long> reversedTransactionIds = reverseAccrualTransactionsForCharges(loan, deactivatedChargeIds);

                // ✅ FIX: When "Remove All" is selected, recalculate ALL installments to clear all charges
                // Otherwise, only recalculate affected installments
                if (isRemoveAll) {
                    log.info("Remove All selected - recalculating ALL installments to clear all charges");
                    recalculateInstallmentChargesFromActiveLoanCharges(loan);
                } else {
                    // ✅ Targeted recalculation - only recalculate affected installments
                    // Use the installment numbers collected BEFORE deactivation
                    Set<LoanRepaymentScheduleInstallment> affectedInstallments = new HashSet<>();

                    // Find installments from the loan's schedule using the collected installment numbers
                    for (LoanRepaymentScheduleInstallment scheduleInstallment : loan.getRepaymentScheduleInstallments()) {
                        if (scheduleInstallment.getInstallmentNumber() != null
                                && affectedInstallmentNumbers.contains(scheduleInstallment.getInstallmentNumber())) {
                            affectedInstallments.add(scheduleInstallment);
                            log.info("Found affected installment {} (due: {}) for recalculation",
                                    scheduleInstallment.getInstallmentNumber(), scheduleInstallment.getDueDate());
                        }
                    }

                    // Recalculate charges for ONLY the affected installments
                    if (!affectedInstallments.isEmpty()) {
                        log.info("Recalculating charges for {} affected installments after bulk removal: {}", affectedInstallments.size(),
                                affectedInstallmentNumbers);
                        for (LoanRepaymentScheduleInstallment affectedInstallment : affectedInstallments) {
                            recalculateInstallmentChargesForSpecificInstallment(loan, affectedInstallment);
                            log.info("Recalculated charges for installment {} (due: {})", affectedInstallment.getInstallmentNumber(),
                                    affectedInstallment.getDueDate());
                        }
                    } else {
                        // Fallback: If no installments found, do full recalculation (safety net)
                        log.warn("No affected installments found for installment numbers {}, falling back to full recalculation",
                                affectedInstallmentNumbers);
                        recalculateInstallmentChargesFromActiveLoanCharges(loan);
                    }
                }

                // Safety net: ensure repayment schedule charge portions are fully in sync with active charges.
                // If any mismatch remains after targeted updates, do a full schedule charge recalculation.
                if (hasRepaymentScheduleChargeMismatch(loan)) {
                    log.warn("Detected charge mismatch between repayment schedule and active charges for loan {}. "
                            + "Running full installment charge recalculation.", loanId);
                    recalculateInstallmentChargesFromActiveLoanCharges(loan);
                }

                // Update loan schedule and summary WITHOUT reprocessing transactions (to avoid date validation)
                loan.updateLoanScheduleDependentDerivedFields();
                loan.updateLoanSummaryAndStatus();
                loanAccountDomainService.setLoanDelinquencyTag(loan, DateUtils.getBusinessLocalDate());
                loanRepositoryWrapper.saveAndFlush(loan);
                // Ensure delinquency tags and m_loan_arrears_aging are refreshed for date-based, EMI-only and
                // remove-all modes.
                loanArrearsAgingService.updateLoanArrearsAgeingDetails(loan);
                businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));

                // Post journal entries to reverse accounting entries for the reversed accrual transactions
                // Only post entries for fully reversed transactions (not partially updated ones)
                if (!reversedTransactionIds.isEmpty()) {
                    // Get existing transaction IDs to ensure we only process the newly reversed transactions
                    // This prevents processing existing transactions that might have mismatches
                    List<Long> existingTransactionIds = new ArrayList<>(loan.findExistingTransactionIds());
                    // Exclude the transactions we just reversed from existing list
                    existingTransactionIds.removeAll(reversedTransactionIds);
                    postJournalEntries(loan, existingTransactionIds, reversedTransactionIds);
                    loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, reversedTransactionIds);
                    log.info("Reversed {} accrual transactions and posted journal entries for loan {}", reversedTransactionIds.size(),
                            loanId);
                }

                log.info("Successfully updated loan {} after removing {} charges", loanId, deactivatedCount);
            } catch (Exception e) {
                log.error("Error updating loan {} after charge removal", loanId, e);
                // Continue anyway - charges are already deactivated, this is just a totals update
            }
        }

        final Map<String, Object> changes = new HashMap<>();
        changes.put("totalChargesFound", totalChargesFound);
        changes.put("chargesDeactivated", deactivatedCount);
        changes.put("chargesSkipped", totalChargesFound - deactivatedCount);

        final CommandProcessingResultBuilder commandProcessingResultBuilder = new CommandProcessingResultBuilder();
        return commandProcessingResultBuilder.withLoanId(loanId) //
                .withEntityId(loanId) //
                .withEntityExternalId(loan.getExternalId()) //
                .with(changes) //
                .build();
    }

    /**
     * Bulk-waives outstanding overdue installment charges (LPI). Selection semantics mirror
     * {@link #deactivateOverdueLoanCharge(Long, JsonCommand)} and the payload sent by the bulk dialog:
     * <ul>
     * <li>no filters - waive every active overdue charge with an outstanding amount</li>
     * <li>{@code removeCompleteEmiOverdue=true} (+ optional {@code selectedEmiNumbers}) - waive the overdue charges of
     * the selected EMIs</li>
     * <li>{@code dueDate} (+ optional {@code toDueDate}) - waive overdue charges within the due-date window</li>
     * </ul>
     * Each charge goes through the SAME per-charge waiver core as the single "Waive Charge" action
     * ({@code customWaiveLoanCharge}): the outstanding portion is waived (paid portions preserved - a half-paid LPI
     * stays half-paid and can still be reversed via REVERSEPAID), a waive transaction with journal entries is posted
     * per charge, and installment due dates are never touched. This replaces the destructive bulk removal
     * (deactivation) flow in the UI.
     */
    @Override
    @Transactional
    public CommandProcessingResult bulkWaiveOverdueLoanCharges(Long loanId, JsonCommand command) {
        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        checkClientOrGroupActive(loan);
        if (!loan.getStatus().isActive()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.charge.bulk.waive.loan.not.active",
                    "Overdue charges can only be waived while the loan is active.", loanId);
        }

        final LocalDate fromDueDate = command.dateValueOfParameterNamed("dueDate");
        final LocalDate toDueDate = command.dateValueOfParameterNamed("toDueDate");
        final boolean emiMode = command.hasParameter("removeCompleteEmiOverdue")
                && command.booleanPrimitiveValueOfParameterNamed("removeCompleteEmiOverdue");
        if (fromDueDate == null && toDueDate != null) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.charge.bulk.waive.invalid.date.filter",
                    "Start date (dueDate) is required when end date (toDueDate) is provided.");
        }
        if (fromDueDate != null && toDueDate != null && toDueDate.isBefore(fromDueDate)) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.charge.bulk.waive.invalid.date.range",
                    "End date (toDueDate) cannot be before start date (dueDate).");
        }

        final Integer overdueChargeTimeValue = ChargeTimeType.OVERDUE_INSTALLMENT.getValue();
        List<LoanCharge> candidates;
        if (fromDueDate == null) {
            candidates = customLoanChargeRepository.findAllActiveOverdueChargesByLoanId(loanId, overdueChargeTimeValue);
        } else {
            final LocalDate effectiveTo = toDueDate != null ? toDueDate : fromDueDate;
            candidates = customLoanChargeRepository.findByLoanIdAndDueDateRange(loanId, fromDueDate, effectiveTo, overdueChargeTimeValue);
        }

        // EMI mode: restrict to charges that resolve to the selected EMIs (or, when none provided, all EMIs that
        // currently have overdue charges - same semantics as the deactivation flow).
        if (emiMode && !candidates.isEmpty()) {
            final List<LoanRepaymentScheduleInstallment> sortedInstallments = new ArrayList<>(loan.getRepaymentScheduleInstallments());
            sortedInstallments.sort(Comparator.comparing(LoanRepaymentScheduleInstallment::getInstallmentNumber,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            final Set<Integer> selectedEmiNumbers = new HashSet<>();
            if (command.hasParameter("selectedEmiNumbers")) {
                final JsonArray emiNumbersArray = command.arrayOfParameterNamed("selectedEmiNumbers");
                if (emiNumbersArray != null) {
                    for (JsonElement element : emiNumbersArray) {
                        if (element != null && !element.isJsonNull()) {
                            selectedEmiNumbers.add(element.getAsInt());
                        }
                    }
                }
            }

            final List<LoanCharge> filtered = new ArrayList<>();
            for (LoanCharge charge : candidates) {
                final Integer resolvedInstallmentNumber = resolveInstallmentNumberForOverdueCharge(charge, sortedInstallments);
                if (resolvedInstallmentNumber == null) {
                    continue;
                }
                if (selectedEmiNumbers.isEmpty() || selectedEmiNumbers.contains(resolvedInstallmentNumber)) {
                    filtered.add(charge);
                }
            }
            candidates = filtered;
        }

        final MonetaryCurrency currency = loan.getCurrency();
        final ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, null);

        int totalChargesFound = candidates.size();
        int chargesWaived = 0;
        BigDecimal totalAmountWaived = BigDecimal.ZERO;
        final List<Long> waiveTransactionIds = new ArrayList<>();

        for (LoanCharge candidate : candidates) {
            // Re-resolve as managed entity and re-check eligibility: active, not already fully waived/paid, and with
            // an outstanding amount to waive. Fully paid charges are skipped (they are handled by REVERSEPAID).
            final LoanCharge loanCharge = candidate.getId() != null ? loanChargeRepository.findById(candidate.getId()).orElse(null) : null;
            if (loanCharge == null || !loanCharge.isActive() || loanCharge.isWaived() || loanCharge.isPaid()
                    || !loanCharge.getAmountOutstanding(currency).isGreaterThanZero()) {
                continue;
            }

            businessEventNotifierService.notifyPreBusinessEvent(new LoanWaiveChargeBusinessEvent(loanCharge));

            Money accruedCharge = Money.zero(currency);
            if (loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()) {
                Collection<LoanChargePaidByData> chargePaidByCollection = this.loanChargeReadPlatformService
                        .retrieveLoanChargesPaidBy(loanCharge.getId(), LoanTransactionType.ACCRUAL, null);
                for (LoanChargePaidByData chargePaidByData : chargePaidByCollection) {
                    accruedCharge = accruedCharge.plus(chargePaidByData.getAmount());
                }
            }

            final Map<String, Object> chargeChanges = new LinkedHashMap<>();
            final List<Long> existingTransactionIds = new ArrayList<>();
            final List<Long> existingReversedTransactionIds = new ArrayList<>();
            final Money outstandingBeforeWaive = loanCharge.getAmountOutstanding(currency);

            final LoanTransaction waiveTransaction = customWaiveLoanCharge(loan, loanCharge, defaultLoanLifecycleStateMachine,
                    chargeChanges, existingTransactionIds, existingReversedTransactionIds, null, scheduleGeneratorDTO, accruedCharge,
                    externalIdFactory.create());

            // Persist per charge (mirrors the single-waive flow) so journal entries are posted for exactly this
            // waive transaction and later iterations correctly treat it as an existing transaction.
            this.loanTransactionRepository.saveAndFlush(waiveTransaction);
            postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds);
            loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);
            businessEventNotifierService.notifyPostBusinessEvent(new LoanWaiveChargeBusinessEvent(loanCharge));

            waiveTransactionIds.add(waiveTransaction.getId());
            totalAmountWaived = totalAmountWaived.add(outstandingBeforeWaive.getAmount());
            chargesWaived++;
        }

        if (chargesWaived > 0) {
            // Safety net: keep repayment schedule charge portions aligned with active charges after bulk waiver.
            if (hasRepaymentScheduleChargeMismatch(loan)) {
                log.warn("Detected charge mismatch after bulk waiving {} charges on loan {}. Running full installment charge "
                        + "recalculation.", chargesWaived, loanId);
                recalculateInstallmentChargesFromActiveLoanCharges(loan);
            }
            loan.updateLoanScheduleDependentDerivedFields();
            loan.updateLoanSummaryAndStatus();
            this.loanRepositoryWrapper.saveAndFlush(loan);
            this.loanAccountDomainService.setLoanDelinquencyTag(loan, DateUtils.getBusinessLocalDate());
            loanArrearsAgingService.updateLoanArrearsAgeingDetails(loan);
            businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));
        }

        log.info("Bulk waive completed for loan {}: {} of {} candidate overdue charges waived, total amount {} (transactions: {})", loanId,
                chargesWaived, totalChargesFound, totalAmountWaived, waiveTransactionIds.size());

        final Map<String, Object> changes = new HashMap<>();
        changes.put("totalChargesFound", totalChargesFound);
        changes.put("chargesWaived", chargesWaived);
        changes.put("chargesSkipped", totalChargesFound - chargesWaived);
        changes.put("totalAmountWaived", totalAmountWaived);

        return new CommandProcessingResultBuilder().withLoanId(loanId) //
                .withEntityId(loanId) //
                .withEntityExternalId(loan.getExternalId()) //
                .with(changes) //
                .build();
    }

    @Override
    @Transactional
    public Map<String, Object> waiveOverdueChargesAccruedAfterSettlementDate(final Long loanId, final LocalDate settlementDate) {
        if (settlementDate == null) {
            return emptyWaiveSummary();
        }
        // Window strictly AFTER the actual payment day (money received Friday, settled Monday -> Sat/Sun/Mon LPI).
        return waiveOverdueChargesInWindow(loanId, settlementDate.plusDays(1));
    }

    @Override
    @Transactional
    public Map<String, Object> waiveOverdueChargesOnOrAfterDate(final Long loanId, final LocalDate valueDate) {
        if (valueDate == null) {
            return emptyWaiveSummary();
        }
        // Window inclusive of the value date so a backdated repayment settles exactly: paid LPI = charges strictly
        // before the value date (penalties preview), waived LPI = charges on/after the value date up to today.
        return waiveOverdueChargesInWindow(loanId, valueDate);
    }

    private Map<String, Object> emptyWaiveSummary() {
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("chargesWaived", 0);
        summary.put("totalAmountWaived", BigDecimal.ZERO);
        summary.put("daysCovered", 0L);
        return summary;
    }

    private Map<String, Object> waiveOverdueChargesInWindow(final Long loanId, final LocalDate fromDate) {
        final Map<String, Object> summary = emptyWaiveSummary();

        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        // Only relevant for a backdated settlement: nothing to waive when the window starts today or in the future.
        if (fromDate == null || fromDate.isAfter(businessDate)) {
            return summary;
        }

        final LocalDate toDate = businessDate;
        summary.put("fromDate", fromDate);
        summary.put("toDate", toDate);

        final Integer overdueChargeTimeValue = ChargeTimeType.OVERDUE_INSTALLMENT.getValue();
        final List<LoanCharge> candidates = customLoanChargeRepository.findByLoanIdAndDueDateRange(loanId, fromDate, toDate,
                overdueChargeTimeValue);
        if (candidates.isEmpty()) {
            return summary;
        }

        final Loan loan = this.loanAssembler.assembleFrom(loanId);
        final MonetaryCurrency currency = loan.getCurrency();
        final ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, null);

        int chargesWaived = 0;
        BigDecimal totalAmountWaived = BigDecimal.ZERO;
        final Set<LocalDate> daysWaived = new HashSet<>();

        for (LoanCharge candidate : candidates) {
            final LoanCharge loanCharge = candidate.getId() != null ? loanChargeRepository.findById(candidate.getId()).orElse(null) : null;
            if (loanCharge == null || !loanCharge.isActive() || loanCharge.isWaived() || loanCharge.isPaid()
                    || !loanCharge.getAmountOutstanding(currency).isGreaterThanZero()) {
                continue;
            }

            businessEventNotifierService.notifyPreBusinessEvent(new LoanWaiveChargeBusinessEvent(loanCharge));

            Money accruedCharge = Money.zero(currency);
            if (loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()) {
                Collection<LoanChargePaidByData> chargePaidByCollection = this.loanChargeReadPlatformService
                        .retrieveLoanChargesPaidBy(loanCharge.getId(), LoanTransactionType.ACCRUAL, null);
                for (LoanChargePaidByData chargePaidByData : chargePaidByCollection) {
                    accruedCharge = accruedCharge.plus(chargePaidByData.getAmount());
                }
            }

            final Money outstandingBeforeWaive = loanCharge.getAmountOutstanding(currency);
            final LoanTransaction waiveTransaction = customWaiveLoanCharge(loan, loanCharge, defaultLoanLifecycleStateMachine,
                    new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), null, scheduleGeneratorDTO, accruedCharge,
                    externalIdFactory.create());

            this.loanTransactionRepository.saveAndFlush(waiveTransaction);
            businessEventNotifierService.notifyPostBusinessEvent(new LoanWaiveChargeBusinessEvent(loanCharge));

            totalAmountWaived = totalAmountWaived.add(outstandingBeforeWaive.getAmount());
            if (loanCharge.getDueLocalDate() != null) {
                daysWaived.add(loanCharge.getDueLocalDate());
            }
            chargesWaived++;
        }

        if (chargesWaived > 0) {
            // Post the journal entries for all waive transactions in a single pass instead of once per charge. The
            // resulting accounting state is identical, but the operation stays linear (avoids re-posting the whole
            // loan's journal entries N times) when a backdated settlement waives many daily LPI charges.
            final List<Long> existingTransactionIds = new ArrayList<>();
            postJournalEntries(loan, existingTransactionIds, new ArrayList<>());
            loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);
            if (hasRepaymentScheduleChargeMismatch(loan)) {
                recalculateInstallmentChargesFromActiveLoanCharges(loan);
            }
            loan.updateLoanScheduleDependentDerivedFields();
            loan.updateLoanSummaryAndStatus();
            this.loanRepositoryWrapper.saveAndFlush(loan);
            this.loanAccountDomainService.setLoanDelinquencyTag(loan, DateUtils.getBusinessLocalDate());
            loanArrearsAgingService.updateLoanArrearsAgeingDetails(loan);
            businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));
        }

        log.info("Backdated-settlement LPI waive for loan {}: window=[{}, {}], chargesWaived={}, totalAmount={}", loanId, fromDate, toDate,
                chargesWaived, totalAmountWaived);

        summary.put("chargesWaived", chargesWaived);
        summary.put("totalAmountWaived", totalAmountWaived);
        summary.put("daysCovered", (long) daysWaived.size());
        return summary;
    }

    /**
     * Reverses a paid loan charge by: 1. Creating a new CHARGE_ADJUSTMENT transaction to reverse the charge payment 2.
     * Crediting the refund amount back to the linked savings account (if any) 3. Posting GL entries to reverse the fee
     * income 4. Marking the charge as inactive 5. Creating audit trail
     */
    @Override
    @Transactional
    public CommandProcessingResult reversePaidLoanCharge(Long loanId, Long loanChargeId, JsonCommand command) {
        log.info("Reversing paid charge {} for loan {}", loanChargeId, loanId);

        // Get the loan and charge
        Loan loan = loanAssembler.assembleFrom(loanId);
        LoanCharge loanCharge = retrieveLoanChargeBy(loanId, loanChargeId);

        final MonetaryCurrency currency = loan.getCurrency();

        // Validation: Ensure the charge has a paid component to reverse.
        // A charge may be settled by both paid and waived amounts; reverse only the paid component.
        BigDecimal totalAmountPaid = loanCharge.getAmountPaid(currency).getAmount();
        if (totalAmountPaid.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Charge {} has no amount paid", loanChargeId);
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.ALREADY_WAIVED,
                    loanCharge.getId());
        }

        // Validation: Ensure the charge is active
        if (!loanCharge.isActive()) {
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.LOAN_INACTIVE,
                    loanCharge.getId());
        }

        // Check if charge is already reversed (has existing CHARGE_ADJUSTMENT transaction)
        if (hasExistingChargeReversal(loan, loanChargeId)) {
            log.warn("Charge {} for loan {} has already been reversed. Skipping duplicate reversal.", loanChargeId, loanId);
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.ALREADY_WAIVED,
                    loanCharge.getId());
        }

        log.info("Total amount paid for charge {}: {}", loanChargeId, totalAmountPaid);

        final LocalDate reversalDate = DateUtils.getBusinessLocalDate();

        // Log loan status and overpaid balance BEFORE reversal
        loan.updateLoanSummaryAndStatus();
        final BigDecimal overpaidBefore = loan.getTotalOverpaid() != null ? loan.getTotalOverpaid() : BigDecimal.ZERO;
        final String statusBefore = loan.getStatus() != null ? loan.getStatus().getCode() : "null";
        log.info("BEFORE reversal - Loan {} status: {}, totalOverpaid: {}, charge {} paid amount: {}", loanId, statusBefore, overpaidBefore,
                loanChargeId, totalAmountPaid);

        LoanRepaymentScheduleInstallment affectedInstallment = null;
        if (loanCharge.isOverdueInstallmentCharge() && loanCharge.getOverdueInstallmentCharge() != null) {
            affectedInstallment = loanCharge.getOverdueInstallmentCharge().getInstallment();
        }

        // Mark the charge as INACTIVE and reset paid amounts.
        // This will naturally reduce the loan's overpaid balance when we update the loan summary,
        // since the charge is no longer considered "paid".
        loanCharge.setActive(false);
        loanCharge.resetPaidAmount(currency);
        loanCharge.setOutstandingAmount(BigDecimal.ZERO);
        loanChargeRepository.saveAndFlush(loanCharge);
        log.info("Marked charge {} as inactive and reset paid amounts (amountPaid: {}, amountOutstanding: {})", loanChargeId,
                loanCharge.getAmountPaid(currency), loanCharge.getAmountOutstanding(currency));

        // Create a CHARGE_ADJUSTMENT transaction on the loan side for audit trail.
        // This transaction will be visible in the loan transactions list to prove the charge was reversed.
        // IMPORTANT: This transaction should NOT have journal entries - journal entries will only be created
        // when the savings deposit is created.
        final ExternalId externalId = externalIdFactory.create();
        LoanTransaction chargeAdjustmentTransaction = LoanTransaction.chargeAdjustment(loan, BigDecimal.ZERO, // Zero
                                                                                                              // amount
                                                                                                              // to
                                                                                                              // avoid
                                                                                                              // schedule
                                                                                                              // impact
                reversalDate, externalId, null // No payment detail
        );

        // Set the fee and penalty portions (negative to indicate reversal)
        BigDecimal feeAmount = BigDecimal.ZERO;
        BigDecimal penaltyAmount = BigDecimal.ZERO;
        if (loanCharge.isPenaltyCharge()) {
            penaltyAmount = totalAmountPaid.negate(); // Negative to reverse
        } else {
            feeAmount = totalAmountPaid.negate(); // Negative to reverse
        }

        chargeAdjustmentTransaction.updateComponents(Money.zero(currency), // principal
                Money.zero(currency), // interest
                Money.of(currency, feeAmount), // fees (negative)
                Money.of(currency, penaltyAmount) // penalties (negative)
        );

        // Link the charge to the transaction so it can be identified as reversed
        final LoanChargePaidBy chargePaidBy = new LoanChargePaidBy(chargeAdjustmentTransaction, loanCharge, totalAmountPaid, null);
        chargeAdjustmentTransaction.getLoanChargesPaid().add(chargePaidBy);
        loanCharge.getLoanChargePaidBySet().add(chargePaidBy);
        final LoanTransactionRelation chargeAdjustmentRelation = LoanTransactionRelation.linkToCharge(chargeAdjustmentTransaction,
                loanCharge, LoanTransactionRelationTypeEnum.CHARGE_ADJUSTMENT);
        chargeAdjustmentTransaction.getLoanTransactionRelations().add(chargeAdjustmentRelation);

        // Add the transaction to the loan (for audit trail, visible in transactions list)
        loan.addLoanTransaction(chargeAdjustmentTransaction);
        // Save the transaction first, then the loan (to avoid duplicate saves)
        this.loanTransactionRepository.saveAndFlush(chargeAdjustmentTransaction);
        loanRepositoryWrapper.saveAndFlush(loan);
        log.info("Created CHARGE_ADJUSTMENT transaction {} on loan {} for charge reversal (audit trail only, no journal entries)",
                chargeAdjustmentTransaction.getId(), loanId);

        // Update schedule/summary from charges only (like our bulk overdue deactivation flow).
        // IMPORTANT: Only recalculate the specific installment that was affected by the reversed charge,
        // not all installments, to prevent removing charges from other periods.
        if (affectedInstallment != null) {
            // recalculateInstallmentChargesForSpecificInstallment() below only rebuilds the CHARGED / WAIVED /
            // WRITTEN-OFF totals (installment.updateChargePortion(...) never touches penaltyChargesPaid /
            // feeChargesPaid) - it deliberately leaves the installment's own "paid" aggregate alone since that is
            // normally only ever mutated by the transaction processors as money is applied/unapplied. But the
            // CHARGE_ADJUSTMENT transaction created above is posted with a ZERO amount (by design, to avoid
            // double-touching the schedule through normal transaction processing) and therefore does NOT run
            // through any transaction processor's unpay path either. Without this explicit call, the installment
            // would still show the just-reversed charge's amount as "paid" forever (penaltyChargesPaid stays
            // stale), which then makes getPenaltyChargesOutstanding() UNDER-report what is actually still owed on
            // that installment by exactly the reversed amount (charged total drops correctly, but so does neither
            // paid nor - overall - outstanding, i.e. outstanding = charged - waived - writtenOff - STALE paid).
            // Explicitly "unpay" exactly the amount this one charge contributed so the installment's paid aggregate
            // reflects only the OTHER, still-genuinely-paid charges on it. See BUG_REPORT.md Finding #2.
            if (loanCharge.isPenaltyCharge()) {
                affectedInstallment.unpayPenaltyChargesComponent(reversalDate, Money.of(currency, totalAmountPaid));
            } else {
                affectedInstallment.unpayFeeChargesComponent(reversalDate, Money.of(currency, totalAmountPaid));
            }
            recalculateInstallmentChargesForSpecificInstallment(loan, affectedInstallment);
            log.info("Recalculated charges only for installment {} (due: {}) affected by reversed charge {}",
                    affectedInstallment.getInstallmentNumber(), affectedInstallment.getDueDate(), loanChargeId);
        } else {
            log.warn("Reversed charge {} has no linked installment; recalculating all installments", loanChargeId);
            recalculateInstallmentChargesFromActiveLoanCharges(loan);
        }

        loan.updateLoanScheduleDependentDerivedFields();
        loan.updateLoanSummaryAndStatus();
        loanRepositoryWrapper.saveAndFlush(loan);

        // Log loan status and overpaid balance AFTER reversal
        final BigDecimal overpaidAfter = loan.getTotalOverpaid() != null ? loan.getTotalOverpaid() : BigDecimal.ZERO;
        final String statusAfter = loan.getStatus() != null ? loan.getStatus().getCode() : "null";
        final BigDecimal overpaidReduction = overpaidBefore.subtract(overpaidAfter);
        log.info("AFTER reversal - Loan {} status: {}, totalOverpaid: {} (reduced by {}), expected reduction: {}", loanId, statusAfter,
                overpaidAfter, overpaidReduction, totalAmountPaid);

        if (overpaidReduction.compareTo(totalAmountPaid) != 0) {
            log.warn("Overpaid reduction ({}) does not match reversed charge amount ({}). Loan may still be overpaid.", overpaidReduction,
                    totalAmountPaid);
        } else {
            log.info("Overpaid balance correctly reduced by {} (matches reversed charge amount)", overpaidReduction);
        }

        // DO NOT create GL entries here - they will be created only when savings deposit is created

        // Credit the reversed amount to the linked savings account via a simple deposit transaction.
        // This creates a DEPOSIT transaction on the savings account (not an account transfer),
        // which will automatically have journal entries created by the accounting processor.
        Long savingsDepositTransactionId = null;
        PortfolioAccountData linkedSavingsAccount = null;
        try {
            linkedSavingsAccount = accountAssociationsReadPlatformService.retriveLoanLinkedAssociation(loanId);

            if (linkedSavingsAccount != null && linkedSavingsAccount.getId() != null) {
                log.info("Found linked savings account {} for loan {}, creating deposit transaction for amount {}",
                        linkedSavingsAccount.getId(), loanId, totalAmountPaid);

                // Resolve payment type from the original transaction that paid the charge
                Long paymentTypeId = resolvePaymentTypeIdForCharge(loan, loanCharge);

                // Create deposit command for savings account
                // Note: transactionType is not a valid parameter for savings deposits API
                // We will update the transaction type after creation
                final Map<String, Object> depositData = new HashMap<>();
                depositData.put("transactionDate", reversalDate.format(DateTimeFormatter.ISO_DATE));
                depositData.put("transactionAmount", totalAmountPaid);
                depositData.put("note", "Refund for reversed charge: " + loanCharge.name() + " (Loan Charge ID: " + loanChargeId + ")");
                if (paymentTypeId != null) {
                    depositData.put("paymentTypeId", paymentTypeId);
                }
                depositData.put("locale", "en");
                depositData.put("dateFormat", "yyyy-MM-dd");

                final String json = fromJsonHelper.toJson(depositData);
                final com.google.gson.JsonElement parsedCommand = fromJsonHelper.parse(json);
                log.debug("Deposit command JSON: {}", json);

                final JsonCommand depositCommand = JsonCommand.fromExistingCommand(command.commandId(), json, parsedCommand, fromJsonHelper,
                        "savingsaccounts", null, null, null, null, linkedSavingsAccount.getId(), null, null, null, null, null, null, null,
                        null);

                try {
                    final CommandProcessingResult depositResult = savingsAccountWritePlatformService.deposit(linkedSavingsAccount.getId(),
                            depositCommand);
                    if (depositResult != null) {
                        savingsDepositTransactionId = depositResult.getResourceId();
                        log.info("Created savings deposit transaction {} for account {} with amount {}", savingsDepositTransactionId,
                                linkedSavingsAccount.getId(), totalAmountPaid);
                    } else {
                        log.error("Deposit operation returned null result for account {} with amount {}", linkedSavingsAccount.getId(),
                                totalAmountPaid);
                    }
                } catch (Exception depositException) {
                    log.error("Exception during deposit creation for account {}", linkedSavingsAccount.getId(), depositException);
                    throw depositException;
                }

                // Update the transaction type to CHARGE_REVERSAL and add a note for charge reversal detection
                // This allows the accounting processor to identify this transaction and use GL 300015 instead of 100062
                try {
                    if (savingsDepositTransactionId != null && linkedSavingsAccount != null && linkedSavingsAccount.getId() != null) {
                        SavingsAccountTransaction savingsTransaction = savingsAccountTransactionRepository
                                .findById(savingsDepositTransactionId).orElse(null);
                        if (savingsTransaction != null) {
                            // Update transaction type to CHARGE_REVERSAL using reflection (typeOf is private)
                            try {
                                Field typeOfField = SavingsAccountTransaction.class.getDeclaredField("typeOf");
                                typeOfField.setAccessible(true);
                                typeOfField.set(savingsTransaction, SavingsAccountTransactionType.CHARGE_REVERSAL.getValue());
                                savingsAccountTransactionRepository.saveAndFlush(savingsTransaction);
                                log.info("Updated savings transaction {} type to CHARGE_REVERSAL (23)", savingsDepositTransactionId);
                            } catch (Exception reflectionException) {
                                log.warn("Failed to update transaction type via reflection: {}. Will rely on note-based detection.",
                                        reflectionException.getMessage());
                            }

                            SavingsAccount savingsAccount = savingsTransaction.getSavingsAccount();
                            if (savingsAccount != null) {
                                final String chargeReversalNote = "Refund for reversed charge: " + loanCharge.name() + " (Loan Charge ID: "
                                        + loanChargeId + ")";
                                final Note savingsTransactionNote = Note.savingsTransactionNote(savingsAccount, savingsTransaction,
                                        chargeReversalNote);
                                this.noteRepository.save(savingsTransactionNote);
                                log.info("Created note on savings transaction {} for charge reversal detection: {}",
                                        savingsDepositTransactionId, chargeReversalNote);

                                // After flipping the type to CHARGE_REVERSAL, the deposit() call above already ran
                                // updateSummary counting this as a DEPOSIT. We now force a full summary
                                // recalculation so totalDeposits correctly reflects the CHARGE_REVERSAL type.
                                // Without this, the next deposit() on this account would recompute from scratch
                                // and previously-flipped CHARGE_REVERSAL transactions from prior calls would be
                                // excluded from totalDeposits (since calculateTotalDeposits only counted DEPOSIT
                                // type at the time of those prior calls). With the CHARGE_REVERSAL now counted
                                // by calculateTotalDeposits, this recalculation ensures immediate consistency.
                                try {
                                    SavingsAccount reloadedAccount = savingsAccountRepository.findById(savingsAccount.getId()).orElse(null);
                                    if (reloadedAccount != null) {
                                        reloadedAccount.getSummary().updateSummary(reloadedAccount.getCurrency(),
                                                savingsAccountTransactionSummaryWrapper, reloadedAccount.getTransactions());
                                        savingsAccountRepository.saveAndFlush(reloadedAccount);
                                        log.info(
                                                "Recalculated savings account {} summary after CHARGE_REVERSAL type update: accountBalance={}",
                                                reloadedAccount.getId(), reloadedAccount.getSummary().getAccountBalance());
                                    }
                                } catch (Exception summaryException) {
                                    log.warn("Failed to recalculate savings account summary after CHARGE_REVERSAL type update: {}",
                                            summaryException.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception noteException) {
                    log.warn("Failed to update transaction type or create note on savings transaction: {}", noteException.getMessage());
                    // Don't fail the entire operation if note creation fails
                }

                // Note: GL entries are automatically created by the savings deposit transaction.
                // For RBF products, the deposit creates:
                // - DR: 100062 (Client Receivable Clearing Acc / SAVINGS_REFERENCE)
                // - CR: 210003 (Working Capital Loan / SAVINGS_CONTROL)
                // Or for charge reversals:
                // - DR: 300015 (Over Due Interest - LPI - RBF)
                // - CR: 210003 (Working Capital Loan / SAVINGS_CONTROL)
                // The accounting processor detects charge reversals via notes and uses GL 300015.
                log.info("GL entries for charge reversal will be automatically created by savings deposit transaction {}",
                        savingsDepositTransactionId);
            } else {
                log.warn(
                        "No linked savings account found for loan {}. Cannot create deposit. Charge reversal completed but funds remain in loan.",
                        loanId);
            }
        } catch (Exception e) {
            log.error("Failed to create savings deposit for loan {}", loanId, e);
            // Don't fail the entire operation if deposit fails - the charge reversal is still valid
            // Admin can manually deposit funds if needed
        }

        // Add user-provided audit note if any
        final String noteText = command.stringValueOfParameterNamed("note");
        if (StringUtils.isNotBlank(noteText)) {
            final Note note = Note.loanNote(loan, noteText);
            this.noteRepository.save(note);
        }

        // Create system audit note for the reversal
        String auditNote;
        if (savingsDepositTransactionId != null) {
            auditNote = String.format(
                    "Reversed paid charge '%s' (ID: %d) with amount %s. Charge marked as inactive and unpaid. GL entries created to reverse fee/penalty income. Savings deposit transaction %d created. Loan balance updated (overpaid amount reduced).",
                    loanCharge.name(), loanChargeId, totalAmountPaid, savingsDepositTransactionId);
        } else {
            auditNote = String.format(
                    "Reversed paid charge '%s' (ID: %d) with amount %s. Charge marked as inactive and unpaid. GL entries created to reverse fee/penalty income. Warning: Savings deposit not created - manual intervention may be required.",
                    loanCharge.name(), loanChargeId, totalAmountPaid);
        }
        final Note auditNoteEntity = Note.loanNote(loan, auditNote);
        this.noteRepository.save(auditNoteEntity);

        // Business events
        businessEventNotifierService.notifyPostBusinessEvent(new LoanUpdateChargeBusinessEvent(loanCharge));
        businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));

        final Map<String, Object> changes = new HashMap<>();
        changes.put("chargeId", loanChargeId);
        changes.put("amountReversed", totalAmountPaid);
        if (savingsDepositTransactionId != null) {
            changes.put("savingsDepositTransactionId", savingsDepositTransactionId);
        }
        // Include savings account number if available (reuse the linkedSavingsAccount retrieved earlier)
        if (linkedSavingsAccount != null && linkedSavingsAccount.getAccountNo() != null) {
            changes.put("savingsAccountNo", linkedSavingsAccount.getAccountNo());
            log.info("Including savings account number {} in response for loan {}", linkedSavingsAccount.getAccountNo(), loanId);
        } else {
            log.warn("No linked savings account number available to include in response for loan {}", loanId);
        }
        changes.put("note", auditNote);

        final CommandProcessingResultBuilder commandProcessingResultBuilder = new CommandProcessingResultBuilder();
        CommandProcessingResultBuilder resultBuilder = commandProcessingResultBuilder.withCommandId(command.commandId()) //
                .withLoanId(loanId) //
                .withEntityId(loanChargeId) //
                .withEntityExternalId(loan.getExternalId()) //
                .with(changes);

        // Include CHARGE_ADJUSTMENT transaction ID as sub-entity (for audit trail)
        if (chargeAdjustmentTransaction != null) {
            resultBuilder = resultBuilder.withSubEntityId(chargeAdjustmentTransaction.getId());
        }

        return resultBuilder.build();
    }

    /**
     * Attempts to inactivate an overdue loan charge. Returns true if successful, false if skipped. This method is
     * lenient and will skip charges that are not active or not overdue installment charges instead of throwing
     * exceptions.
     */
    private boolean inactivateOverdueLoanCharge(LoanCharge loanCharge) {
        if (loanCharge == null) {
            log.warn("Cannot deactivate null charge");
            return false;
        }

        Long chargeId = loanCharge.getId();
        if (chargeId == null) {
            log.warn("Cannot deactivate charge with null ID");
            return false;
        }

        // Skip if not an overdue installment charge
        if (loanCharge.getChargeTimeType() == null || !loanCharge.getChargeTimeType().isOverdueInstallment()) {
            log.warn("Skipping charge {} - not an overdue installment charge (chargeTimeType: {})", chargeId,
                    loanCharge.getChargeTimeType() != null ? loanCharge.getChargeTimeType().getValue() : "null");
            return false;
        }

        // Skip if already inactive
        if (!loanCharge.isActive()) {
            log.debug("Skipping charge {} - already inactive", chargeId);
            return false;
        }

        try {
            // The charge should already be a managed entity (we reload it before calling this method)
            // But verify it's active before deactivating
            if (!loanCharge.isActive()) {
                log.debug("Charge {} is already inactive, skipping", chargeId);
                return false;
            }

            // Deactivate the charge
            loanCharge.setActive(false);
            loanChargeRepository.saveAndFlush(loanCharge);

            // Verify the deactivation persisted
            LoanCharge verifyCharge = loanChargeRepository.findById(chargeId).orElse(null);
            if (verifyCharge != null && verifyCharge.isActive()) {
                log.error("Charge {} deactivation did not persist! Still active after saveAndFlush", chargeId);
                return false;
            }

            businessEventNotifierService.notifyPostBusinessEvent(new LoanUpdateChargeBusinessEvent(loanCharge));

            log.info("Successfully deactivated overdue charge {} (verified inactive: {})", chargeId,
                    verifyCharge != null ? !verifyCharge.isActive() : "N/A");
            return true;
        } catch (Exception e) {
            log.error("Error deactivating charge {}", chargeId, e);
            return false;
        }
    }

    /**
     * Finds and reverses accrual transactions linked to the deactivated charges. Updates accrual portions on
     * installments and returns the list of reversed transaction IDs for journal entry posting.
     */
    private List<Long> reverseAccrualTransactionsForCharges(Loan loan, List<Long> deactivatedChargeIds) {
        List<Long> reversedTransactionIds = new ArrayList<>();
        MonetaryCurrency currency = loan.getCurrency();

        // Find accrual transactions linked to the deactivated charges
        List<LoanTransaction> accrualTransactions = customLoanChargeRepository.findAccrualTransactionsByChargeIds(deactivatedChargeIds,
                LoanTransactionType.ACCRUAL);

        if (accrualTransactions.isEmpty()) {
            log.debug("No accrual transactions found for deactivated charges {}", deactivatedChargeIds);
            return reversedTransactionIds;
        }

        log.info("Found {} accrual transactions to reverse for loan {}", accrualTransactions.size(), loan.getId());

        // Reverse each accrual transaction and update accrual portions
        for (LoanTransaction accrualTransaction : accrualTransactions) {
            if (accrualTransaction.isReversed()) {
                continue; // Skip already reversed transactions
            }

            // Extract amounts per installment from the accrual transaction
            Map<Integer, Money> feesByInstallment = new HashMap<>();
            Map<Integer, Money> penaltiesByInstallment = new HashMap<>();
            Set<LoanChargePaidBy> chargesPaid = accrualTransaction.getLoanChargesPaid();

            // Skip transactions with no LoanChargePaidBy entries (shouldn't happen, but safety check)
            if (chargesPaid == null || chargesPaid.isEmpty()) {
                log.warn("Skipping transaction {} - it has no LoanChargePaidBy entries", accrualTransaction.getId());
                continue;
            }

            // Check if ALL charges in this transaction are being deactivated
            boolean allChargesDeactivated = true;
            for (LoanChargePaidBy chargePaidBy : chargesPaid) {
                if (chargePaidBy.getLoanCharge() == null || chargePaidBy.getAmount() == null) {
                    log.warn("Skipping transaction {} - it has null LoanCharge or amount in LoanChargePaidBy", accrualTransaction.getId());
                    allChargesDeactivated = false;
                    break;
                }
                if (!deactivatedChargeIds.contains(chargePaidBy.getLoanCharge().getId())) {
                    allChargesDeactivated = false;
                    break;
                }
            }

            // Only reverse transactions where ALL charges are being deactivated
            // For transactions with mixed charges, skip reversal and let charge recalculation handle it
            if (!allChargesDeactivated) {
                log.info(
                        "Skipping reversal of transaction {} - it has charges that are not being deactivated. Accrual portions will be updated via recalculation.",
                        accrualTransaction.getId());
                continue; // Skip this transaction entirely
            }

            // Process charges that are being deactivated (for accrual portion updates)
            for (LoanChargePaidBy chargePaidBy : chargesPaid) {
                // All charges should be deactivated at this point, but double-check
                if (!deactivatedChargeIds.contains(chargePaidBy.getLoanCharge().getId())) {
                    continue;
                }

                Integer installmentNumber = chargePaidBy.getInstallmentNumber();
                LoanCharge charge = chargePaidBy.getLoanCharge();
                Money amount = Money.of(currency, chargePaidBy.getAmount());

                if (charge.isPenaltyCharge()) {
                    penaltiesByInstallment.merge(installmentNumber, amount, Money::plus);
                } else if (charge.isFeeCharge()) {
                    feesByInstallment.merge(installmentNumber, amount, Money::plus);
                }
            }

            // Validate that transaction amounts match the sum of LoanChargePaidBy amounts
            // This prevents accounting mismatch errors
            Money transactionFeePortion = accrualTransaction.getFeeChargesPortion(currency);
            Money transactionPenaltyPortion = accrualTransaction.getPenaltyChargesPortion(currency);

            Money sumFeeFromCharges = Money.zero(currency);
            Money sumPenaltyFromCharges = Money.zero(currency);

            for (LoanChargePaidBy chargePaidBy : chargesPaid) {
                LoanCharge charge = chargePaidBy.getLoanCharge();
                Money amount = Money.of(currency, chargePaidBy.getAmount());
                if (charge.isPenaltyCharge()) {
                    sumPenaltyFromCharges = sumPenaltyFromCharges.plus(amount);
                } else if (charge.isFeeCharge()) {
                    sumFeeFromCharges = sumFeeFromCharges.plus(amount);
                }
            }

            // Check for mismatch (allow small rounding differences of 0.01)
            boolean feeMismatch = transactionFeePortion.minus(sumFeeFromCharges).abs()
                    .isGreaterThan(Money.of(currency, BigDecimal.valueOf(0.01)));
            boolean penaltyMismatch = transactionPenaltyPortion.minus(sumPenaltyFromCharges).abs()
                    .isGreaterThan(Money.of(currency, BigDecimal.valueOf(0.01)));

            if (feeMismatch || penaltyMismatch) {
                log.warn(
                        "Skipping reversal of transaction {} due to amount mismatch - Fee: transaction={}, charges={}, diff={}; Penalty: transaction={}, charges={}, diff={}. This transaction will be skipped to prevent accounting errors.",
                        accrualTransaction.getId(), transactionFeePortion, sumFeeFromCharges,
                        transactionFeePortion.minus(sumFeeFromCharges).abs(), transactionPenaltyPortion, sumPenaltyFromCharges,
                        transactionPenaltyPortion.minus(sumPenaltyFromCharges).abs());
                // Skip this transaction - don't reverse it, don't update accrual portions, don't add to
                // reversedTransactionIds
                continue;
            }

            // Reverse the entire transaction since all charges are being deactivated and amounts match
            accrualTransaction.reverse();
            reversedTransactionIds.add(accrualTransaction.getId());
            log.info("Reversed transaction {} - all charges are being deactivated, amounts validated", accrualTransaction.getId());

            // Update accrual portions on affected installments (only for successfully reversed transactions)
            Set<Integer> allInstallmentNumbers = new HashSet<>();
            allInstallmentNumbers.addAll(feesByInstallment.keySet());
            allInstallmentNumbers.addAll(penaltiesByInstallment.keySet());

            for (Integer installmentNumber : allInstallmentNumbers) {
                LoanRepaymentScheduleInstallment installment = loan.fetchRepaymentScheduleInstallment(installmentNumber);
                if (installment == null) {
                    continue;
                }

                Money feesToReverse = feesByInstallment.getOrDefault(installmentNumber, Money.zero(currency));
                Money penaltiesToReverse = penaltiesByInstallment.getOrDefault(installmentNumber, Money.zero(currency));

                Money currentFeeAccrued = installment.getFeeAccrued(currency);
                Money currentPenaltyAccrued = installment.getPenaltyAccrued(currency);
                Money currentInterestAccrued = installment.getInterestAccrued(currency);

                Money newFeeAccrued = currentFeeAccrued.minus(feesToReverse);
                Money newPenaltyAccrued = currentPenaltyAccrued.minus(penaltiesToReverse);

                // Update accrual portions (interest remains unchanged)
                installment.updateAccrualPortion(currentInterestAccrued, newFeeAccrued, newPenaltyAccrued);

                log.debug("Updated accrual for installment {} - Fee: {} -> {}, Penalty: {} -> {}", installmentNumber, currentFeeAccrued,
                        newFeeAccrued, currentPenaltyAccrued, newPenaltyAccrued);
            }
        }

        // Save all reversed transactions
        if (!accrualTransactions.isEmpty()) {
            loanTransactionRepository.saveAllAndFlush(accrualTransactions);
        }

        return reversedTransactionIds;
    }

    /**
     * Creates GL entries to reverse the fee/penalty income when a charge is reversed. For late payment fees, this
     * creates hardcoded GL entries: - Credit 210003 (Working Capital Loan) - Debit 300015 (Over Due Interest - LPI -
     * RBF)
     *
     * @param loan
     *            The loan account
     * @param loanCharge
     *            The charge being reversed
     * @param transaction
     *            The CHARGE_ADJUSTMENT transaction (for reference, but journal entries are not linked to it)
     * @param amount
     *            The amount being reversed
     * @param reversalDate
     *            The reversal date
     * @param glTransactionId
     *            The transaction ID to use for GL entries (typically the savings deposit transaction ID)
     */
    private void createGLEntriesForChargeReversal(Loan loan, LoanCharge loanCharge, LoanTransaction transaction, BigDecimal amount,
            LocalDate reversalDate, String glTransactionId) {

        try {
            // Get the office for the loan
            final Office office = officeRepositoryWrapper.findOneWithNotFoundDetection(loan.getOfficeId());
            final String currencyCode = loan.getCurrencyCode();
            // Use the provided glTransactionId (should be savings deposit transaction ID)
            // Format: "S{transactionId}" for savings transactions
            final String transactionId = glTransactionId != null ? glTransactionId : "CHARGE_REVERSAL_" + loanCharge.getId();

            // Check if this is a late payment fee/penalty
            if (loanCharge.isPenaltyCharge() && loanCharge.isOverdueInstallmentCharge()) {
                // Hardcoded GL accounts for late payment fee reversal
                // Debit: 100062 (Client Receivable Clearing Acc) - to clear the client receivable
                // Credit: 210003 (Working Capital Loan) - to reduce the working capital loan liability
                final String CREDIT_GL_CODE = "210003"; // Working Capital Loan
                final String DEBIT_GL_CODE = "100062"; // Client Receivable Clearing Acc

                // Find GL accounts by code
                final GLAccount creditAccount = glAccountRepository.findOneByGlCode(CREDIT_GL_CODE)
                        .orElseThrow(() -> new RuntimeException("GL Account " + CREDIT_GL_CODE + " not found"));

                final GLAccount debitAccount = glAccountRepository.findOneByGlCode(DEBIT_GL_CODE)
                        .orElseThrow(() -> new RuntimeException("GL Account " + DEBIT_GL_CODE + " not found"));

                // Create the description
                final String description = String.format("Reversal of late payment fee for loan %d, charge %d", loan.getId(),
                        loanCharge.getId());

                // Create CREDIT entry for 210003 (Working Capital Loan)
                // Note: These GL entries are created when savings deposit is credited, not for the loan
                // CHARGE_ADJUSTMENT transaction
                final JournalEntry creditEntry = JournalEntry.createNew(office, null, // No payment detail
                        creditAccount, currencyCode, transactionId, false, // Not manual entry
                        reversalDate, JournalEntryType.CREDIT, amount, description, 1, // Entity type: 1 = Loan
                        loan.getId(), null, // No reference number
                        null, // No loan transaction ID (journal entries are not linked to CHARGE_ADJUSTMENT
                              // transaction)
                        null, // No savings transaction ID (will be linked via transactionId format "S{id}")
                        null, // No client transaction
                        null // No share transaction
                );

                // Create DEBIT entry for 100062 (Client Receivable Clearing Acc)
                final JournalEntry debitEntry = JournalEntry.createNew(office, null, // No payment detail
                        debitAccount, currencyCode, transactionId, false, // Not manual entry
                        reversalDate, JournalEntryType.DEBIT, amount, description, 1, // Entity type: 1 = Loan
                        loan.getId(), null, // No reference number
                        null, // No loan transaction ID (journal entries are not linked to CHARGE_ADJUSTMENT
                              // transaction)
                        null, // No savings transaction ID (will be linked via transactionId format "S{id}")
                        null, // No client transaction
                        null // No share transaction
                );

                // Save the journal entries
                journalEntryRepository.saveAndFlush(creditEntry);
                journalEntryRepository.saveAndFlush(debitEntry);

                log.info("Created GL reversal entries for late payment fee charge {}: Dr {} ({}), Cr {} ({}), Amount: {}",
                        loanCharge.getId(), debitAccount.getGlCode(), debitAccount.getName(), creditAccount.getGlCode(),
                        creditAccount.getName(), amount);

                // Ensure only 2 entries are created (one debit, one credit)
                // This prevents duplicate entries from being created
            } else {
                log.warn("Charge {} is not a late payment fee. GL reversal not automated. " + "Manual GL entry may be needed.",
                        loanCharge.getId());
            }
        } catch (Exception e) {
            // Log the error but don't fail the entire operation
            // The charge reversal and savings refund are still valid
            log.error("Failed to create GL reversal entries for charge {}", loanCharge.getId(), e);
        }
    }

    private boolean hasExistingChargeReversal(Loan loan, Long loanChargeId) {
        // Check both in-memory transactions and persisted transactions
        // First check in-memory (transactions that haven't been saved yet)
        for (LoanTransaction transaction : loan.getLoanTransactions()) {
            if (transaction.isNotReversed() && transaction.getTypeOf().isChargeAdjustment()) {
                Set<LoanChargePaidBy> chargesPaid = transaction.getLoanChargesPaid();
                if (chargesPaid == null || chargesPaid.isEmpty()) {
                    continue;
                }
                for (LoanChargePaidBy chargePaidBy : chargesPaid) {
                    if (chargePaidBy.getLoanCharge() != null && loanChargeId.equals(chargePaidBy.getLoanCharge().getId())) {
                        log.warn("Found existing CHARGE_ADJUSTMENT transaction {} for charge {} in loan's in-memory transactions",
                                transaction.getId(), loanChargeId);
                        return true;
                    }
                }
            }
        }

        // Also check in database to catch any transactions that were saved but not yet loaded
        // Reload the loan to get the latest transactions from database
        Loan freshLoan = loanRepositoryWrapper.findOneWithNotFoundDetection(loan.getId());
        for (LoanTransaction transaction : freshLoan.getLoanTransactions()) {
            if (transaction.isNotReversed() && transaction.getTypeOf().isChargeAdjustment()) {
                Set<LoanChargePaidBy> chargesPaid = transaction.getLoanChargesPaid();
                if (chargesPaid == null || chargesPaid.isEmpty()) {
                    continue;
                }
                for (LoanChargePaidBy chargePaidBy : chargesPaid) {
                    if (chargePaidBy.getLoanCharge() != null && loanChargeId.equals(chargePaidBy.getLoanCharge().getId())) {
                        log.warn("Found existing CHARGE_ADJUSTMENT transaction {} for charge {} in database", transaction.getId(),
                                loanChargeId);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Resolves a payment type id for the savings deposit based on the original loan transaction that paid the charge.
     * Falls back to the first available payment type if none found.
     */
    private Long resolvePaymentTypeIdForCharge(Loan loan, LoanCharge loanCharge) {
        Long paymentTypeId = null;

        for (LoanTransaction transaction : loan.getLoanTransactions()) {
            if (transaction.isReversed()) {
                continue;
            }
            Set<LoanChargePaidBy> chargesPaid = transaction.getLoanChargesPaid();
            if (chargesPaid == null || chargesPaid.isEmpty()) {
                continue;
            }
            for (LoanChargePaidBy chargePaidBy : chargesPaid) {
                if (chargePaidBy.getLoanCharge() != null && chargePaidBy.getLoanCharge().getId().equals(loanCharge.getId())) {
                    PaymentDetail paymentDetail = transaction.getPaymentDetail();
                    if (paymentDetail != null && paymentDetail.getPaymentType() != null) {
                        paymentTypeId = paymentDetail.getPaymentType().getId();
                        break;
                    }
                }
            }
            if (paymentTypeId != null) {
                break;
            }
        }

        if (paymentTypeId == null) {
            List<PaymentTypeData> paymentTypes = paymentTypeReadPlatformService.retrieveAllPaymentTypesWithCode();
            if (paymentTypes != null && !paymentTypes.isEmpty()) {
                for (PaymentTypeData paymentTypeData : paymentTypes) {
                    if (Boolean.TRUE.equals(paymentTypeData.getIsCashPayment())) {
                        paymentTypeId = paymentTypeData.getId();
                        break;
                    }
                }
                if (paymentTypeId == null) {
                    paymentTypeId = paymentTypes.get(0).getId();
                }
            }
        }

        if (paymentTypeId == null) {
            log.warn("No payment type found for charge {} on loan {}. Savings deposit will fail validation.", loanCharge.getId(),
                    loan.getId());
        }

        return paymentTypeId;
    }

    /**
     * Updates the repayment schedule to reflect the reversed charge. The charge is already marked inactive, so
     * recalculation will exclude it. The UI should show the reversed overdue interest with strikethrough since the
     * charge is inactive.
     *
     * NOTE: This method is currently not used - schedule recalculation is done directly in reversePaidLoanCharge.
     * Keeping it for potential future use.
     */
    private void updateRepaymentScheduleForReversedCharge(Loan loan, LoanCharge loanCharge, BigDecimal reversedAmount) {
        if (!loanCharge.isOverdueInstallmentCharge()) {
            return; // Only update schedule for overdue installment charges
        }

        // ✅ Use the LoanOverdueInstallmentCharge relationship (consistent with recalculation logic)
        if (loanCharge.getOverdueInstallmentCharge() != null) {
            LoanRepaymentScheduleInstallment affectedInstallment = loanCharge.getOverdueInstallmentCharge().getInstallment();
            if (affectedInstallment != null) {
                // Only recalculate the affected installment to prevent affecting other periods
                recalculateInstallmentChargesForSpecificInstallment(loan, affectedInstallment);
                log.info("Updated repayment schedule for reversed charge {} - installment {} (due: {}) will show reduced penalty amount",
                        loanCharge.getId(), affectedInstallment.getInstallmentNumber(), affectedInstallment.getDueDate());
            } else {
                log.warn("Reversed charge {} has no linked installment", loanCharge.getId());
            }
        } else {
            log.warn("Reversed charge {} has no LoanOverdueInstallmentCharge relationship", loanCharge.getId());
        }
    }

    /**
     * Recalculates charge portions for a specific installment based on currently active loan charges. This ensures only
     * the affected installment is updated, preventing removal of charges from other periods.
     *
     * CRITICAL: Uses installment ID matching to avoid detached entity issues.
     */
    private void recalculateInstallmentChargesForSpecificInstallment(Loan loan, LoanRepaymentScheduleInstallment installment) {
        MonetaryCurrency currency = loan.getCurrency();
        Money totalFee = Money.zero(currency);
        Money totalPenalty = Money.zero(currency);
        Money feeWaived = Money.zero(currency);
        Money penaltyWaived = Money.zero(currency);
        Money feeWrittenOff = Money.zero(currency);
        Money penaltyWrittenOff = Money.zero(currency);

        // Build sorted schedule installments for robust overdue-charge mapping
        List<LoanRepaymentScheduleInstallment> sortedInstallments = new ArrayList<>(loan.getRepaymentScheduleInstallments());
        sortedInstallments.sort((left, right) -> {
            if (left == null || right == null) {
                return 0;
            }
            if (left.getInstallmentNumber() != null && right.getInstallmentNumber() != null) {
                return left.getInstallmentNumber().compareTo(right.getInstallmentNumber());
            }
            if (left.getDueDate() != null && right.getDueDate() != null) {
                return left.getDueDate().compareTo(right.getDueDate());
            }
            return 0;
        });

        // Target identifiers
        Integer targetInstallmentNumber = installment.getInstallmentNumber();
        LocalDate targetDueDate = installment.getDueDate();

        // Sum up all ACTIVE charges for this specific installment
        for (LoanCharge loanCharge : loan.getLoanCharges()) {
            final boolean activeCharge = loanCharge.isActive();
            final boolean reversedPaidCharge = isReversedPaidCharge(loanCharge);
            if (!activeCharge && !reversedPaidCharge) {
                continue; // Skip inactive charges unless they represent a paid-charge reversal
            }

            // Check if this charge applies to this installment
            boolean appliesToInstallment = false;

            if (loanCharge.isOverdueInstallmentCharge()) {
                Integer resolvedInstallmentNumber = resolveInstallmentNumberForOverdueCharge(loanCharge, sortedInstallments);
                appliesToInstallment = (resolvedInstallmentNumber != null && targetInstallmentNumber != null
                        && resolvedInstallmentNumber.equals(targetInstallmentNumber));
            } else if (loanCharge.isInstalmentFee()) {
                // Installment fees apply to specific installments via LoanInstallmentCharge
                for (LoanInstallmentCharge installmentCharge : loanCharge.installmentCharges()) {
                    LoanRepaymentScheduleInstallment feeInstallment = installmentCharge.getRepaymentInstallment();
                    if (feeInstallment != null) {
                        // Match by installment number + due date (stable identifiers)
                        if (targetInstallmentNumber != null && feeInstallment.getInstallmentNumber() != null && targetDueDate != null
                                && feeInstallment.getDueDate() != null
                                && targetInstallmentNumber.equals(feeInstallment.getInstallmentNumber())
                                && targetDueDate.equals(feeInstallment.getDueDate())) {
                            appliesToInstallment = true;
                            break;
                        }
                    }
                }
            }

            if (appliesToInstallment) {
                if (loanCharge.isPenaltyCharge()) {
                    if (activeCharge) {
                        totalPenalty = totalPenalty.plus(loanCharge.getAmount(currency));
                    } else if (reversedPaidCharge) {
                        // A reversed charge may have been partially settled by a waiver (half paid / half waived).
                        // Only the PAID component is reversed; the waived component remains settled-by-waiver, so the
                        // corresponding charged amount must stay on the installment - otherwise waived > charged and
                        // the repayment schedule shows a distorted period.
                        totalPenalty = totalPenalty.plus(loanCharge.getAmountWaived(currency));
                    }
                    penaltyWaived = penaltyWaived.plus(loanCharge.getAmountWaived(currency));
                    penaltyWrittenOff = penaltyWrittenOff.plus(loanCharge.getAmountWrittenOff(currency));
                } else {
                    if (activeCharge) {
                        totalFee = totalFee.plus(loanCharge.getAmount(currency));
                    } else if (reversedPaidCharge) {
                        // See penalty branch: keep the waived component's charged amount for reversed charges.
                        totalFee = totalFee.plus(loanCharge.getAmountWaived(currency));
                    }
                    feeWaived = feeWaived.plus(loanCharge.getAmountWaived(currency));
                    feeWrittenOff = feeWrittenOff.plus(loanCharge.getAmountWrittenOff(currency));
                }
            }
        }

        // Update only this installment's charge portions
        installment.updateChargePortion(totalFee, feeWaived, feeWrittenOff, totalPenalty, penaltyWaived, penaltyWrittenOff,
                Money.zero(currency), Money.zero(currency), Money.zero(currency));

        log.debug("Updated installment {} (due: {}) - Fee: {}, Penalty: {}", installment.getInstallmentNumber(), installment.getDueDate(),
                totalFee, totalPenalty);
    }

    /**
     * Recalculates installment charge portions based on currently active loan charges. This ensures the repayment
     * schedule reflects the correct charge amounts after charges are removed. NO date validation is performed.
     *
     * WARNING: This method recalculates ALL installments. Use recalculateInstallmentChargesForSpecificInstallment when
     * possible to avoid affecting other periods.
     */
    private void recalculateInstallmentChargesFromActiveLoanCharges(Loan loan) {
        MonetaryCurrency currency = loan.getCurrency();

        // Build sorted schedule installments for robust overdue-charge mapping
        List<LoanRepaymentScheduleInstallment> sortedInstallments = new ArrayList<>(loan.getRepaymentScheduleInstallments());
        sortedInstallments.sort((left, right) -> {
            if (left == null || right == null) {
                return 0;
            }
            if (left.getInstallmentNumber() != null && right.getInstallmentNumber() != null) {
                return left.getInstallmentNumber().compareTo(right.getInstallmentNumber());
            }
            if (left.getDueDate() != null && right.getDueDate() != null) {
                return left.getDueDate().compareTo(right.getDueDate());
            }
            return 0;
        });

        // For each installment, recalculate total penalty and fee charges from active loan charges
        for (LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            Money totalFee = Money.zero(currency);
            Money totalPenalty = Money.zero(currency);
            Money feeWaived = Money.zero(currency);
            Money penaltyWaived = Money.zero(currency);
            Money feeWrittenOff = Money.zero(currency);
            Money penaltyWrittenOff = Money.zero(currency);

            Integer targetInstallmentNumber = installment.getInstallmentNumber();
            LocalDate targetDueDate = installment.getDueDate();

            // Sum up all ACTIVE charges for this installment
            for (LoanCharge loanCharge : loan.getLoanCharges()) {
                final boolean activeCharge = loanCharge.isActive();
                final boolean reversedPaidCharge = isReversedPaidCharge(loanCharge);
                if (!activeCharge && !reversedPaidCharge) {
                    continue; // Skip inactive charges unless they represent a paid-charge reversal
                }

                // Check if this charge applies to this installment
                boolean appliesToInstallment = false;

                if (loanCharge.isOverdueInstallmentCharge()) {
                    Integer resolvedInstallmentNumber = resolveInstallmentNumberForOverdueCharge(loanCharge, sortedInstallments);
                    appliesToInstallment = (resolvedInstallmentNumber != null && targetInstallmentNumber != null
                            && resolvedInstallmentNumber.equals(targetInstallmentNumber));
                } else if (loanCharge.isInstalmentFee()) {
                    // Installment fees apply to specific installments via LoanInstallmentCharge
                    for (LoanInstallmentCharge installmentCharge : loanCharge.installmentCharges()) {
                        LoanRepaymentScheduleInstallment feeInstallment = installmentCharge.getRepaymentInstallment();
                        if (feeInstallment != null && targetInstallmentNumber != null && feeInstallment.getInstallmentNumber() != null
                                && targetDueDate != null && feeInstallment.getDueDate() != null
                                && targetInstallmentNumber.equals(feeInstallment.getInstallmentNumber())
                                && targetDueDate.equals(feeInstallment.getDueDate())) {
                            appliesToInstallment = true;
                            break;
                        }
                    }
                }

                if (appliesToInstallment) {
                    if (loanCharge.isPenaltyCharge()) {
                        if (activeCharge) {
                            totalPenalty = totalPenalty.plus(loanCharge.getAmount(currency));
                        } else if (reversedPaidCharge) {
                            // Half-paid/half-waived reversal: only the paid component is reversed; keep the waived
                            // component's charged amount so waived never exceeds charged on the installment.
                            totalPenalty = totalPenalty.plus(loanCharge.getAmountWaived(currency));
                        }
                        penaltyWaived = penaltyWaived.plus(loanCharge.getAmountWaived(currency));
                        penaltyWrittenOff = penaltyWrittenOff.plus(loanCharge.getAmountWrittenOff(currency));
                    } else {
                        if (activeCharge) {
                            totalFee = totalFee.plus(loanCharge.getAmount(currency));
                        } else if (reversedPaidCharge) {
                            totalFee = totalFee.plus(loanCharge.getAmountWaived(currency));
                        }
                        feeWaived = feeWaived.plus(loanCharge.getAmountWaived(currency));
                        feeWrittenOff = feeWrittenOff.plus(loanCharge.getAmountWrittenOff(currency));
                    }
                }
            }

            // Update the installment's charge portions
            installment.updateChargePortion(totalFee, feeWaived, feeWrittenOff, totalPenalty, penaltyWaived, penaltyWrittenOff,
                    Money.zero(currency), Money.zero(currency), Money.zero(currency));

            log.debug("Updated installment {} - Fee: {}, Penalty: {}", installment.getInstallmentNumber(), totalFee, totalPenalty);
        }
    }

    /**
     * Resolves the target EMI installment number for an overdue charge.
     * <p>
     * Resolution order: 1) Direct {@code LoanOverdueInstallmentCharge} link, when it points to an installment that
     * still exists in the current schedule. 2) Due-date window mapping against repayment schedule installments:
     * (fromDate, dueDate] when fromDate exists, otherwise (prevDueDate, currentDueDate], as a fallback for when the
     * direct link is missing or stale (e.g. after a reschedule/restructure replaced the installment it pointed to).
     * <p>
     * The direct link is preferred (see BUG_REPORT.md Finding #2): {@code applyChargeToOverdueLoanInstallment} creates
     * every real overdue/LPI charge with {@code entry.getValue()} (the charge's own {@code dueDate}) set to the OWNING
     * installment's due date PLUS the configured penalty-wait/grace days, and links it to that same owning installment
     * via {@code LoanOverdueInstallmentCharge} at creation time - by design, a daily-accruing LPI charge's
     * {@code dueDate} is always AFTER its own installment's due date. The date-window heuristic below assumes a
     * charge's {@code dueDate} falls inside its owning installment's own (fromDate, dueDate] window, which is true for
     * ordinary fees but is never true for these overdue charges - so on its own it systematically resolves them one
     * installment too late (into whichever later installment's window their dueDate happens to land in), silently
     * misattributing genuinely-outstanding penalties to the wrong installment (or to none) the moment either
     * recalculation method below runs. The direct link, being set once at charge-creation time to the exact installment
     * the penalty was actually charged against, does not have this problem and is safe to trust whenever it still
     * resolves to a real installment in the current schedule.
     * </p>
     */
    private Integer resolveInstallmentNumberForOverdueCharge(LoanCharge loanCharge,
            List<LoanRepaymentScheduleInstallment> sortedInstallments) {
        if (loanCharge == null || !loanCharge.isOverdueInstallmentCharge()) {
            return null;
        }

        final Integer directLinkInstallmentNumber = resolveViaDirectOverdueInstallmentLink(loanCharge, sortedInstallments);
        if (directLinkInstallmentNumber != null) {
            return directLinkInstallmentNumber;
        }

        if (sortedInstallments == null || sortedInstallments.isEmpty() || loanCharge.getDueDate() == null) {
            return null;
        }

        LocalDate chargeDueDate = loanCharge.getDueDate();
        LocalDate prevDueDate = null;
        LoanRepaymentScheduleInstallment lastValid = null;

        for (LoanRepaymentScheduleInstallment current : sortedInstallments) {
            if (current == null || current.getInstallmentNumber() == null || current.getDueDate() == null) {
                continue;
            }

            LocalDate currentDueDate = current.getDueDate();
            LocalDate currentFromDate = current.getFromDate();

            // Preferred mapping when fromDate is available: (fromDate, dueDate]
            if (currentFromDate != null) {
                if (chargeDueDate.isAfter(currentFromDate) && !chargeDueDate.isAfter(currentDueDate)) {
                    return current.getInstallmentNumber();
                }
            }

            // First valid installment: anything on/before its due date maps here.
            if (prevDueDate == null) {
                if (!chargeDueDate.isAfter(currentDueDate)) {
                    return current.getInstallmentNumber();
                }
            } else {
                // Regular window: (prevDueDate, currentDueDate]
                if (chargeDueDate.isAfter(prevDueDate) && !chargeDueDate.isAfter(currentDueDate)) {
                    return current.getInstallmentNumber();
                }
            }

            prevDueDate = currentDueDate;
            lastValid = current;
        }

        // If chargeDueDate is after the last schedule due date, map to the last installment as a safe fallback.
        if (lastValid != null && lastValid.getInstallmentNumber() != null) {
            return lastValid.getInstallmentNumber();
        }

        return null;
    }

    /**
     * Returns the installment number from the charge's direct {@code LoanOverdueInstallmentCharge} link, but ONLY when
     * that link's installment can still be found in the CURRENT {@code sortedInstallments} (matched structurally via
     * {@link #isSameInstallment}) - guarding against a stale link left over from before a reschedule/restructure
     * replaced the installment it used to point to. Returns {@code null} when there is no link, or the link's
     * installment no longer exists, so the caller falls back to date-window mapping.
     */
    private Integer resolveViaDirectOverdueInstallmentLink(LoanCharge loanCharge,
            List<LoanRepaymentScheduleInstallment> sortedInstallments) {
        if (loanCharge.getOverdueInstallmentCharge() == null || loanCharge.getOverdueInstallmentCharge().getInstallment() == null) {
            return null;
        }
        final LoanRepaymentScheduleInstallment linkedInstallment = loanCharge.getOverdueInstallmentCharge().getInstallment();
        if (linkedInstallment.getInstallmentNumber() == null) {
            return null;
        }
        if (sortedInstallments == null || sortedInstallments.isEmpty()) {
            // No current schedule to validate against (e.g. called before installments are loaded) - trust the link.
            return linkedInstallment.getInstallmentNumber();
        }
        final boolean linkedInstallmentStillExists = sortedInstallments.stream()
                .anyMatch(candidate -> isSameInstallment(candidate, linkedInstallment));
        return linkedInstallmentStillExists ? linkedInstallment.getInstallmentNumber() : null;
    }

    private static boolean isReversedPaidCharge(final LoanCharge loanCharge) {
        if (loanCharge == null || loanCharge.isActive() || loanCharge.getLoanChargePaidBySet() == null) {
            return false;
        }
        return loanCharge.getLoanChargePaidBySet().stream().anyMatch(chargePaidBy -> chargePaidBy.getLoanTransaction() != null
                && chargePaidBy.getLoanTransaction().isNotReversed() && chargePaidBy.getLoanTransaction().getTypeOf().isChargeAdjustment());
    }

    private boolean isSameInstallment(LoanRepaymentScheduleInstallment left, LoanRepaymentScheduleInstallment right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left.getInstallmentNumber() != null && left.getInstallmentNumber().equals(right.getInstallmentNumber())
                && left.getDueDate() != null && left.getDueDate().equals(right.getDueDate());
    }

    /**
     * Remaps existing overdue-installment links onto the current schedule and recreates missing
     * {@code m_loan_overdue_installment_charge} join rows for active LPI charges. Returns {@code true} when any charge
     * was mutated and needs a flush.
     */
    boolean repairOrphanOverdueInstallmentChargeLinks(final Loan loan) {
        if (loan == null || loan.getLoanCharges() == null || loan.getLoanCharges().isEmpty()) {
            return false;
        }
        boolean mutated = false;
        final Long penaltyPostingWaitPeriod = this.configurationDomainService.retrieveGraceOnPenaltyPostingPeriod();
        final long postingWaitDays = penaltyPostingWaitPeriod != null ? penaltyPostingWaitPeriod : 0L;

        for (final LoanCharge loanCharge : loan.getLoanCharges()) {
            if (loanCharge == null || !loanCharge.isOverdueInstallmentCharge() || !loanCharge.isActive()) {
                continue;
            }

            final LoanOverdueInstallmentCharge existingLink = loanCharge.getOverdueInstallmentCharge();
            if (existingLink != null && existingLink.getInstallment() != null
                    && existingLink.getInstallment().getInstallmentNumber() != null) {
                final LoanRepaymentScheduleInstallment current = loan
                        .fetchRepaymentScheduleInstallment(existingLink.getInstallment().getInstallmentNumber());
                if (current != null && !isSameInstallment(current, existingLink.getInstallment())) {
                    existingLink.updateLoanRepaymentScheduleInstallment(current);
                    mutated = true;
                }
                continue;
            }

            final LoanRepaymentScheduleInstallment installment = resolveInstallmentForOrphanOverdueCharge(loan, loanCharge);
            if (installment == null) {
                log.warn(
                        "Active overdue/LPI charge {} on loan {} has no m_loan_overdue_installment_charge join and no installment could be resolved; leaving charge active but unlinked (LPI frequency lookup will skip it)",
                        loanCharge.getId(), loan.getId());
                continue;
            }

            final Integer frequencyNumber = inferFrequencyNumberForOrphanOverdueCharge(loanCharge, installment, postingWaitDays);
            final LoanOverdueInstallmentCharge repaired = new LoanOverdueInstallmentCharge(loanCharge, installment, frequencyNumber);
            loanCharge.updateOverdueInstallmentCharge(repaired);
            mutated = true;
            log.info("Repaired orphan overdue/LPI charge {} on loan {} → installment {} frequency {}", loanCharge.getId(), loan.getId(),
                    installment.getInstallmentNumber(), frequencyNumber);
        }
        return mutated;
    }

    /**
     * Best-effort installment resolution for an active overdue charge that lost its join row. Prefers a sole normal
     * installment (PF/RF bullet loans), then the latest installment whose due date is strictly before the charge due
     * date (LPI due dates are always after the owning installment due date).
     */
    private LoanRepaymentScheduleInstallment resolveInstallmentForOrphanOverdueCharge(final Loan loan, final LoanCharge loanCharge) {
        final List<LoanRepaymentScheduleInstallment> candidates = loan.getRepaymentScheduleInstallments().stream().filter(
                i -> i != null && i.getInstallmentNumber() != null && i.getDueDate() != null && !i.isRecalculatedInterestComponent())
                .sorted(Comparator.comparing(LoanRepaymentScheduleInstallment::getInstallmentNumber)).toList();
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (loanCharge.getDueLocalDate() == null) {
            return null;
        }
        LoanRepaymentScheduleInstallment best = null;
        for (final LoanRepaymentScheduleInstallment installment : candidates) {
            if (loanCharge.getDueLocalDate().isAfter(installment.getDueDate())) {
                best = installment;
            }
        }
        return best;
    }

    private Integer inferFrequencyNumberForOrphanOverdueCharge(final LoanCharge loanCharge,
            final LoanRepaymentScheduleInstallment installment, final long postingWaitDays) {
        if (loanCharge.getDueLocalDate() == null || installment.getDueDate() == null) {
            return 1;
        }
        // Core creates the first LPI occurrence at installmentDue + penaltyPostingWaitPeriod days; subsequent daily
        // occurrences increment frequency_number. Infer from that so re-apply skips this occurrence.
        final LocalDate firstChargeDate = installment.getDueDate().plusDays(postingWaitDays);
        final long daysFromFirst = ChronoUnit.DAYS.between(firstChargeDate, loanCharge.getDueLocalDate());
        final int frequency = (int) daysFromFirst + 1;
        return Math.max(frequency, 1);
    }

    private boolean hasRepaymentScheduleChargeMismatch(Loan loan) {
        MonetaryCurrency currency = loan.getCurrency();

        // Build sorted schedule installments for robust overdue-charge mapping
        List<LoanRepaymentScheduleInstallment> sortedInstallments = new ArrayList<>(loan.getRepaymentScheduleInstallments());
        sortedInstallments.sort((left, right) -> {
            if (left == null || right == null) {
                return 0;
            }
            if (left.getInstallmentNumber() != null && right.getInstallmentNumber() != null) {
                return left.getInstallmentNumber().compareTo(right.getInstallmentNumber());
            }
            if (left.getDueDate() != null && right.getDueDate() != null) {
                return left.getDueDate().compareTo(right.getDueDate());
            }
            return 0;
        });

        for (LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            Money expectedFee = Money.zero(currency);
            Money expectedPenalty = Money.zero(currency);

            // Get the target installment ID and number for matching (use the actual installment from loan's schedule)
            Integer targetInstallmentNumber = installment.getInstallmentNumber();
            LocalDate targetDueDate = installment.getDueDate();

            for (LoanCharge loanCharge : loan.getLoanCharges()) {
                if (!loanCharge.isActive()) {
                    continue;
                }
                boolean appliesToInstallment = false;
                if (loanCharge.isOverdueInstallmentCharge()) {
                    Integer resolvedInstallmentNumber = resolveInstallmentNumberForOverdueCharge(loanCharge, sortedInstallments);
                    appliesToInstallment = (resolvedInstallmentNumber != null && targetInstallmentNumber != null
                            && resolvedInstallmentNumber.equals(targetInstallmentNumber));
                } else if (loanCharge.isInstalmentFee()) {
                    for (LoanInstallmentCharge installmentCharge : loanCharge.installmentCharges()) {
                        LoanRepaymentScheduleInstallment feeInstallment = installmentCharge.getRepaymentInstallment();
                        if (feeInstallment != null && targetInstallmentNumber != null && feeInstallment.getInstallmentNumber() != null
                                && targetDueDate != null && feeInstallment.getDueDate() != null
                                && targetInstallmentNumber.equals(feeInstallment.getInstallmentNumber())
                                && targetDueDate.equals(feeInstallment.getDueDate())) {
                            appliesToInstallment = true;
                            break;
                        }
                    }
                }

                if (appliesToInstallment) {
                    if (loanCharge.isPenaltyCharge()) {
                        expectedPenalty = expectedPenalty.plus(loanCharge.getAmount(currency));
                    } else {
                        expectedFee = expectedFee.plus(loanCharge.getAmount(currency));
                    }
                }
            }

            if (!installment.getFeeChargesCharged(currency).isEqualTo(expectedFee)
                    || !installment.getPenaltyChargesCharged(currency).isEqualTo(expectedPenalty)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected LoanOverdueDTO applyChargeToOverdueLoanInstallment(final Loan loan, final Long loanChargeId, final Integer periodNumber,
            final JsonCommand command) {
        boolean runInterestRecalculation = false;
        boolean chargesApplied = false;
        final Charge chargeDefinition = this.chargeRepository.findOneWithNotFoundDetection(loanChargeId);

        Collection<Integer> frequencyNumbers = loanChargeReadPlatformService.retrieveOverdueInstallmentChargeFrequencyNumber(loan,
                chargeDefinition, periodNumber);

        Integer feeFrequency = chargeDefinition.feeFrequency();
        final ScheduledDateGenerator scheduledDateGenerator = new DefaultScheduledDateGenerator();
        Map<Integer, LocalDate> scheduleDates = new HashMap<>();
        final Long penaltyWaitPeriodValue = this.configurationDomainService.retrievePenaltyWaitPeriod();
        final Long penaltyPostingWaitPeriodValue = this.configurationDomainService.retrieveGraceOnPenaltyPostingPeriod();
        final LocalDate dueDate = command.localDateValueOfParameterNamed("dueDate");
        long diff = penaltyWaitPeriodValue + 1 - penaltyPostingWaitPeriodValue;
        if (diff < 1) {
            diff = 1L;
        }
        LocalDate startDate = dueDate.plusDays(penaltyWaitPeriodValue + 1L);
        int frequencyNumber = 1;
        if (feeFrequency == null) {
            scheduleDates.put(frequencyNumber++, startDate.minusDays(diff));
        } else {
            while (!DateUtils.isDateInTheFuture(startDate)) {
                scheduleDates.put(frequencyNumber++, startDate.minusDays(diff));

                startDate = scheduledDateGenerator.getRepaymentPeriodDate(PeriodFrequencyType.fromInt(feeFrequency),
                        chargeDefinition.feeInterval(), startDate);
            }
        }

        for (Integer frequency : frequencyNumbers) {
            scheduleDates.remove(frequency);
        }

        LoanRepaymentScheduleInstallment installment = null;
        LocalDate lastChargeAppliedDate = dueDate;
        LocalDate recalculateFrom = DateUtils.getBusinessLocalDate();
        if (!scheduleDates.isEmpty()) {
            installment = loan.fetchRepaymentScheduleInstallment(periodNumber);
            lastChargeAppliedDate = installment.getDueDate();
            businessEventNotifierService.notifyPreBusinessEvent(new LoanApplyOverdueChargeBusinessEvent(loan));

            for (Map.Entry<Integer, LocalDate> entry : scheduleDates.entrySet()) {

                final LoanCharge loanCharge = loanChargeAssembler.createNewFromJson(loan, chargeDefinition, command, entry.getValue());

                if (BigDecimal.ZERO.compareTo(loanCharge.amount()) == 0) {
                    continue;
                }
                LoanOverdueInstallmentCharge overdueInstallmentCharge = new LoanOverdueInstallmentCharge(loanCharge, installment,
                        entry.getKey());
                loanCharge.updateOverdueInstallmentCharge(overdueInstallmentCharge);

                boolean isAppliedOnBackDate = addCharge(loan, chargeDefinition, loanCharge);
                chargesApplied = true;
                runInterestRecalculation = runInterestRecalculation || isAppliedOnBackDate;
                if (DateUtils.isBefore(entry.getValue(), recalculateFrom)) {
                    recalculateFrom = entry.getValue();
                }
                if (DateUtils.isAfter(entry.getValue(), lastChargeAppliedDate)) {
                    lastChargeAppliedDate = entry.getValue();
                }
            }
            businessEventNotifierService.notifyPostBusinessEvent(new LoanApplyOverdueChargeBusinessEvent(loan));
            businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));
        }

        return new CredXLoanOverdueDTO(loan, runInterestRecalculation, recalculateFrom, lastChargeAppliedDate, chargesApplied);
    }

    @Override
    @Transactional
    public void applyOverdueChargesForLoan(final Long loanId, final Collection<OverdueLoanScheduleData> overdueLoanScheduleDataList) {
        // Repair active overdue/LPI charges that lost their m_loan_overdue_installment_charge join (typically after a
        // schedule regenerate/reschedule) BEFORE the core apply path runs. Core's frequency lookup used to NPE on those
        // orphans and fail the whole LPI job batch for the loan.
        try {
            final Loan loanForRepair = this.loanAssembler.assembleFrom(loanId);
            if (repairOrphanOverdueInstallmentChargeLinks(loanForRepair)) {
                this.loanAccountService.saveAndFlushLoanWithDataIntegrityViolationChecks(loanForRepair);
            }
        } catch (Exception e) {
            log.warn("Failed to repair orphan overdue installment charge links for loan {} before LPI apply: {}", loanId, e.getMessage());
        }

        // Delegate to parent to apply penalties; CredX skips full transaction reprocess on this path (see override
        // hook).
        super.applyOverdueChargesForLoan(loanId, overdueLoanScheduleDataList);

        // After penalties and schedule changes, recompute custom statuses and fire webhooks
        try {
            Loan updatedLoan = this.loanAssembler.assembleFrom(loanId);

            // Compute new custom loan status based on updated schedule/installments
            CustomLoanStatus oldCustomLoanStatus = updatedLoan.hasCustomStatus() ? updatedLoan.getCustomLoanStatus() : null;
            CustomLoanStatus newCustomLoanStatus = LoanTransactionInstallmentUtils.computeCustomLoanStatusForLoan(updatedLoan);
            updatedLoan.setCustomLoanStatus(newCustomLoanStatus);
            updatedLoan = this.loanAccountService.saveAndFlushLoanWithDataIntegrityViolationChecks(updatedLoan);

            // LOC status aggregation if drawdown
            Optional<LoanLineOfCreditParams> invoice = loanLineOfCreditParamsRepository.findByLoanId(updatedLoan.getId());
            boolean isDrawdown = invoice.isPresent();
            Optional<Long> locIdOpt = invoice.map(p -> p.getLineOfCredit() != null ? p.getLineOfCredit().getId() : null);

            LocStatusAggregationData locStatusAggregationData;
            if (isDrawdown && locIdOpt.isPresent() && lineOfCreditRepository != null) {
                LineOfCredit loc = invoice.get().getLineOfCredit();
                locStatusAggregationData = this.locStatusAggregationUtils.computeLocStatusAggregationData(loc, updatedLoan);
                lineOfCreditRepository.save(loc);
            } else {
                locStatusAggregationData = null;
            }

            final Loan finalLoan = updatedLoan;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    transactionTemplate.execute(status -> {
                        loanStatusWebhookPublisher.publish(finalLoan, oldCustomLoanStatus, isDrawdown, locIdOpt);
                        if (locStatusAggregationData != null) {
                            lineOfCreditStatusWebhookPublisher.publish(finalLoan, locStatusAggregationData.getDefaultLocStatus().name(),
                                    locStatusAggregationData.getOldLocCustomStatus().name(),
                                    locStatusAggregationData.getNewLocCustomStatus().name(), isDrawdown, locIdOpt);
                        }
                        return null;
                    });
                }
            });
        } catch (Exception e) {
            log.warn("Failed to recompute/publish custom statuses after overdue penalties for loan {}: {}", loanId, e.getMessage());
        }
    }

    /**
     * Daily LPI accrual posts new penalty charges only. Replaying all repayments under PIPF reverses SQL/manual
     * principal-first corrections and creates duplicate UI rows — skip full reprocess on this path.
     */
    @Override
    protected boolean shouldReprocessTransactionsAfterOverdueChargeApply(final Loan loan) {
        return false;
    }
}
