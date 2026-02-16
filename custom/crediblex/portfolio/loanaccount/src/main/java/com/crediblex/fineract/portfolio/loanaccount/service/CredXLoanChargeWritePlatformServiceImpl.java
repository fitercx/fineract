package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.commands.LineOfCreditStatusWebhookPublisher;
import com.crediblex.fineract.commands.LoanStatusWebhookPublisher;
import com.crediblex.fineract.infrastructure.commands.utils.LoanTransactionInstallmentUtils;
import com.crediblex.fineract.portfolio.loanaccount.data.LocStatusAggregationData;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loanaccount.repository.CustomLoanChargeRepository;
import com.crediblex.fineract.portfolio.loanaccount.util.LocStatusAggregationUtils;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
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
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBePayedException;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBeWaivedException;
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
import org.apache.fineract.portfolio.loanaccount.domain.LoanInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRelation;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRelationTypeEnum;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeApiJsonValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualTransactionBusinessEventService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeWritePlatformServiceImpl;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.loanaccount.service.adjustment.LoanAdjustmentService;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
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
            SavingsAccountTransactionRepository savingsAccountTransactionRepository) {

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
        this.fromJsonHelper = fromApiJsonHelper;
        this.glAccountRepository = glAccountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.officeRepositoryWrapper = officeRepositoryWrapper;
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

        loanChargeAdjustmentEntranceValidation(loanCharge, transactionAmount);
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

        // Custom waiver logic that preserves the amountPaid
        if (loanCharge.isInstalmentFee()) {
            // For installment fees, handle the waiver manually
            final LoanInstallmentCharge chargePerInstallment = loanCharge.getInstallmentLoanCharge(loanInstallmentNumber);
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
            // For non-installment fees, manually set the values to preserve amountPaid
            // Set the waived amount to the outstanding amount only (not the total amount)
            loanCharge.setAmountWaived(amountOutstanding.getAmount());
            loanCharge.setOutstandingAmount(BigDecimal.ZERO);

            // Use updatePaidAmountBy with zero to trigger the waived flag setting logic
            // This will call the logic that sets this.waived = true when waivedAmount.isGreaterThanZero()
            loanCharge.updatePaidAmountBy(Money.zero(loan.getCurrency()), null, null);
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
        final LoanChargePaidBy loanChargePaidBy = new LoanChargePaidBy(waiveLoanChargeTransaction, loanCharge,
                waiveLoanChargeTransaction.getAmount(loan.getCurrency()).getAmount(), loanInstallmentNumber);
        waiveLoanChargeTransaction.getLoanChargesPaid().add(loanChargePaidBy);
        loan.addLoanTransaction(waiveLoanChargeTransaction);

        // Handle schedule regeneration and transaction reprocessing manually

        loan.updateLoanSummaryDerivedFields();
        loan.doPostLoanTransactionChecks(waiveLoanChargeTransaction.getTransactionDate(), loanLifecycleStateMachine);

        return waiveLoanChargeTransaction;
    }

    @Override
    @Transactional
    public CommandProcessingResult deactivateOverdueLoanCharge(Long loanId, JsonCommand command) {
        LocalDate fromDueDate = command.dateValueOfParameterNamed("dueDate");
        LocalDate toDueDate = command.dateValueOfParameterNamed("toDueDate");
        boolean isRemoveAll = (fromDueDate == null);

        List<LoanCharge> loanCharges;
        Integer overdueChargeTimeValue = ChargeTimeType.OVERDUE_INSTALLMENT.getValue();
        if (fromDueDate == null) {
            // Remove all: get all active overdue charges
            loanCharges = customLoanChargeRepository.findAllActiveOverdueChargesByLoanId(loanId, overdueChargeTimeValue);
        } else if (toDueDate != null) {
            // Date range: get charges within the range
            loanCharges = customLoanChargeRepository.findByLoanIdAndDueDateRange(loanId, fromDueDate, toDueDate, overdueChargeTimeValue);
        } else {
            // Single date or from date onwards: get all charges from the date
            loanCharges = customLoanChargeRepository.findByLoanIdAndFromDueDate(loanId, fromDueDate, overdueChargeTimeValue);
        }

        // Track which charges were actually deactivated (collect their IDs for accounting reversal)
        int totalChargesFound = loanCharges.size();
        List<Long> deactivatedChargeIds = new ArrayList<>();
        loanCharges.forEach(charge -> {
            if (inactivateOverdueLoanCharge(charge)) {
                deactivatedChargeIds.add(charge.getId());
            }
        });
        long deactivatedCount = deactivatedChargeIds.size();

        log.info("Found {} overdue charges for loan {}, successfully deactivated {} (isRemoveAll: {})", totalChargesFound, loanId,
                deactivatedCount, isRemoveAll);

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
                    // Find which installments are affected by the deactivated charges
                    Set<LoanRepaymentScheduleInstallment> affectedInstallments = new HashSet<>();
                    for (LoanCharge deactivatedCharge : loanCharges) {
                        if (deactivatedChargeIds.contains(deactivatedCharge.getId())) {
                            if (deactivatedCharge.isOverdueInstallmentCharge()) {
                                // ✅ Use the LoanOverdueInstallmentCharge relationship (consistent with recalculation
                                // logic)
                                if (deactivatedCharge.getOverdueInstallmentCharge() != null) {
                                    LoanRepaymentScheduleInstallment affectedInstallment = deactivatedCharge.getOverdueInstallmentCharge()
                                            .getInstallment();
                                    if (affectedInstallment != null) {
                                        affectedInstallments.add(affectedInstallment);
                                        log.info("Installment {} (due: {}) is affected by deactivated charge {}",
                                                affectedInstallment.getInstallmentNumber(), affectedInstallment.getDueDate(),
                                                deactivatedCharge.getId());
                                    } else {
                                        log.warn("Deactivated charge {} has no linked installment", deactivatedCharge.getId());
                                    }
                                } else {
                                    log.warn("Deactivated charge {} has no LoanOverdueInstallmentCharge relationship",
                                            deactivatedCharge.getId());
                                }
                            }
                        }
                    }

                    // Recalculate charges for ONLY the affected installments
                    if (!affectedInstallments.isEmpty()) {
                        log.info("Recalculating charges for {} affected installments after bulk removal", affectedInstallments.size());
                        for (LoanRepaymentScheduleInstallment affectedInstallment : affectedInstallments) {
                            recalculateInstallmentChargesForSpecificInstallment(loan, affectedInstallment);
                            log.info("Recalculated charges for installment {} (due: {})", affectedInstallment.getInstallmentNumber(),
                                    affectedInstallment.getDueDate());
                        }
                    } else {
                        // Fallback: If no installments found, do full recalculation (safety net)
                        log.warn("No affected installments found for deactivated charges, falling back to full recalculation");
                        recalculateInstallmentChargesFromActiveLoanCharges(loan);
                    }
                }

                // Update loan schedule and summary WITHOUT reprocessing transactions (to avoid date validation)
                loan.updateLoanScheduleDependentDerivedFields();
                loan.updateLoanSummaryAndStatus();
                loanRepositoryWrapper.saveAndFlush(loan);

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

        // Validation: Ensure the charge is paid
        if (!loanCharge.isPaid()) {
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

        // Calculate the total amount paid for this charge
        BigDecimal totalAmountPaid = loanCharge.getAmountPaid(loan.getCurrency()).getAmount();

        if (totalAmountPaid.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Charge {} has no amount paid", loanChargeId);
            throw new LoanChargeCannotBeWaivedException(LoanChargeCannotBeWaivedException.LoanChargeCannotBeWaivedReason.ALREADY_WAIVED,
                    loanCharge.getId());
        }

        log.info("Total amount paid for charge {}: {}", loanChargeId, totalAmountPaid);

        final LocalDate reversalDate = DateUtils.getBusinessLocalDate();
        final MonetaryCurrency currency = loan.getCurrency();

        // Log loan status and overpaid balance BEFORE reversal
        loan.updateLoanSummaryAndStatus();
        final BigDecimal overpaidBefore = loan.getTotalOverpaid() != null ? loan.getTotalOverpaid() : BigDecimal.ZERO;
        final String statusBefore = loan.getStatus() != null ? loan.getStatus().getCode() : "null";
        log.info("BEFORE reversal - Loan {} status: {}, totalOverpaid: {}, charge {} paid amount: {}", loanId, statusBefore, overpaidBefore,
                loanChargeId, totalAmountPaid);

        // Mark the charge as INACTIVE and reset paid amounts.
        // This will naturally reduce the loan's overpaid balance when we update the loan summary,
        // since the charge is no longer considered "paid".
        loanCharge.setActive(false);
        loanCharge.resetPaidAmount(currency);
        loanCharge.setOutstandingAmount(BigDecimal.ZERO);
        loanChargeRepository.saveAndFlush(loanCharge);
        log.info("Marked charge {} as inactive and reset paid amounts (amountPaid: {}, amountOutstanding: {})", loanChargeId,
                loanCharge.getAmountPaid(currency), loanCharge.getAmountOutstanding(currency));

        // Update schedule/summary from charges only (like our bulk overdue deactivation flow).
        // IMPORTANT: Only recalculate the specific installment that was affected by the reversed charge,
        // not all installments, to prevent removing charges from other periods.
        if (loanCharge.isOverdueInstallmentCharge()) {
            // ✅ Use the LoanOverdueInstallmentCharge relationship (consistent with recalculation logic)
            if (loanCharge.getOverdueInstallmentCharge() != null) {
                LoanRepaymentScheduleInstallment affectedInstallment = loanCharge.getOverdueInstallmentCharge().getInstallment();
                if (affectedInstallment != null) {
                    // Only recalculate the affected installment
                    recalculateInstallmentChargesForSpecificInstallment(loan, affectedInstallment);
                    log.info("Recalculated charges only for installment {} (due: {}) affected by reversed charge {}",
                            affectedInstallment.getInstallmentNumber(), affectedInstallment.getDueDate(), loanChargeId);
                } else {
                    log.warn("Reversed charge {} has no linked installment", loanChargeId);
                }
            } else {
                log.warn("Reversed charge {} has no LoanOverdueInstallmentCharge relationship", loanChargeId);
            }
        }
        // For non-overdue charges, recalculate all installments (shouldn't happen for our use case)
        else {
            recalculateInstallmentChargesFromActiveLoanCharges(loan);
        }

        loan.updateLoanScheduleDependentDerivedFields();
        loan.updateLoanSummaryAndStatus();
        loanRepositoryWrapper.saveAndFlush(loan);

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

        // Add the transaction to the loan (for audit trail, visible in transactions list)
        loan.addLoanTransaction(chargeAdjustmentTransaction);
        // Save the transaction first, then the loan (to avoid duplicate saves)
        this.loanTransactionRepository.saveAndFlush(chargeAdjustmentTransaction);
        loanRepositoryWrapper.saveAndFlush(loan);
        log.info("Created CHARGE_ADJUSTMENT transaction {} on loan {} for charge reversal (audit trail only, no journal entries)",
                chargeAdjustmentTransaction.getId(), loanId);

        // Note: Schedule recalculation was already done earlier (only for the affected installment)
        // Just update derived fields and summary
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
        // Skip if not an overdue installment charge
        if (!loanCharge.getChargeTimeType().isOverdueInstallment()) {
            log.warn("Skipping charge {} - not an overdue installment charge", loanCharge.getId());
            return false;
        }

        // Skip if already inactive
        if (!loanCharge.isActive()) {
            log.debug("Skipping charge {} - already inactive", loanCharge.getId());
            return false;
        }

        // Deactivate the charge
        loanCharge.setActive(false);
        loanChargeRepository.saveAndFlush(loanCharge);

        businessEventNotifierService.notifyPostBusinessEvent(new LoanUpdateChargeBusinessEvent(loanCharge));

        log.info("Successfully deactivated overdue charge {}", loanCharge.getId());
        return true;
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
     */
    private void recalculateInstallmentChargesForSpecificInstallment(Loan loan, LoanRepaymentScheduleInstallment installment) {
        MonetaryCurrency currency = loan.getCurrency();
        Money totalFee = Money.zero(currency);
        Money totalPenalty = Money.zero(currency);
        Money feeWaived = Money.zero(currency);
        Money penaltyWaived = Money.zero(currency);
        Money feeWrittenOff = Money.zero(currency);
        Money penaltyWrittenOff = Money.zero(currency);

        // Sum up all ACTIVE charges for this specific installment
        for (LoanCharge loanCharge : loan.getLoanCharges()) {
            if (!loanCharge.isActive()) {
                continue; // Skip inactive charges
            }

            // Check if this charge applies to this installment
            boolean appliesToInstallment = false;

            if (loanCharge.isOverdueInstallmentCharge()) {
                // Overdue charges are linked via LoanOverdueInstallmentCharge relationship
                if (loanCharge.getOverdueInstallmentCharge() != null) {
                    LoanRepaymentScheduleInstallment chargeInstallment = loanCharge.getOverdueInstallmentCharge().getInstallment();
                    appliesToInstallment = (chargeInstallment != null && chargeInstallment.equals(installment));
                }
            } else if (loanCharge.isInstalmentFee()) {
                // Installment fees apply to specific installments via LoanInstallmentCharge
                for (LoanInstallmentCharge installmentCharge : loanCharge.installmentCharges()) {
                    if (installmentCharge.getRepaymentInstallment().equals(installment)) {
                        appliesToInstallment = true;
                        break;
                    }
                }
            }

            if (appliesToInstallment) {
                if (loanCharge.isPenaltyCharge()) {
                    totalPenalty = totalPenalty.plus(loanCharge.getAmount(currency));
                    penaltyWaived = penaltyWaived.plus(loanCharge.getAmountWaived(currency));
                    penaltyWrittenOff = penaltyWrittenOff.plus(loanCharge.getAmountWrittenOff(currency));
                } else {
                    totalFee = totalFee.plus(loanCharge.getAmount(currency));
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

        // For each installment, recalculate total penalty and fee charges from active loan charges
        for (LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            Money totalFee = Money.zero(currency);
            Money totalPenalty = Money.zero(currency);
            Money feeWaived = Money.zero(currency);
            Money penaltyWaived = Money.zero(currency);
            Money feeWrittenOff = Money.zero(currency);
            Money penaltyWrittenOff = Money.zero(currency);

            // Sum up all ACTIVE charges for this installment
            for (LoanCharge loanCharge : loan.getLoanCharges()) {
                if (!loanCharge.isActive()) {
                    continue; // Skip inactive charges
                }

                // Check if this charge applies to this installment
                boolean appliesToInstallment = false;

                if (loanCharge.isOverdueInstallmentCharge()) {
                    // Overdue charges are linked via LoanOverdueInstallmentCharge relationship
                    if (loanCharge.getOverdueInstallmentCharge() != null) {
                        LoanRepaymentScheduleInstallment chargeInstallment = loanCharge.getOverdueInstallmentCharge().getInstallment();
                        appliesToInstallment = (chargeInstallment != null && chargeInstallment.equals(installment));
                    }
                } else if (loanCharge.isInstalmentFee()) {
                    // Installment fees apply to specific installments via LoanInstallmentCharge
                    for (LoanInstallmentCharge installmentCharge : loanCharge.installmentCharges()) {
                        if (installmentCharge.getRepaymentInstallment().equals(installment)) {
                            appliesToInstallment = true;
                            break;
                        }
                    }
                }

                if (appliesToInstallment) {
                    if (loanCharge.isPenaltyCharge()) {
                        totalPenalty = totalPenalty.plus(loanCharge.getAmount(currency));
                        penaltyWaived = penaltyWaived.plus(loanCharge.getAmountWaived(currency));
                        penaltyWrittenOff = penaltyWrittenOff.plus(loanCharge.getAmountWrittenOff(currency));
                    } else {
                        totalFee = totalFee.plus(loanCharge.getAmount(currency));
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

    @Override
    @Transactional
    public void applyOverdueChargesForLoan(final Long loanId, final Collection<OverdueLoanScheduleData> overdueLoanScheduleDataList) {
        // Delegate to parent to apply penalties and perform schedule recalculation and transaction reprocessing
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
}
