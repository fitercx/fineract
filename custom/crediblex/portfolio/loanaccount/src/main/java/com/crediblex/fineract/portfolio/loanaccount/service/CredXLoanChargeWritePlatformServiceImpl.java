package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.repository.CustomLoanChargeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Primary
public class CredXLoanChargeWritePlatformServiceImpl extends LoanChargeWritePlatformServiceImpl {

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
            CustomLoanChargeRepository customLoanChargeRepository) {

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

        log.info("Found {} overdue charges for loan {}, successfully deactivated {}", totalChargesFound, loanId, deactivatedCount);

        // Reload loan to get updated state
        Loan loan = loanAssembler.assembleFrom(loanId);

        // Only update loan if we actually deactivated any charges
        if (deactivatedCount > 0) {
            try {
                // Find and reverse accrual transactions linked to the deactivated charges
                List<Long> reversedTransactionIds = reverseAccrualTransactionsForCharges(loan, deactivatedChargeIds);

                // Recalculate installment charge portions from active charges
                recalculateInstallmentChargesFromActiveLoanCharges(loan);

                // Update loan schedule and summary WITHOUT reprocessing transactions (to avoid date validation)
                loan.updateLoanScheduleDependentDerivedFields();
                loan.updateLoanSummaryAndStatus();
                loanRepositoryWrapper.saveAndFlush(loan);

                // Post journal entries to reverse accounting entries for the reversed accrual transactions
                if (!reversedTransactionIds.isEmpty()) {
                    postJournalEntries(loan, new ArrayList<>(), reversedTransactionIds);
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

            for (LoanChargePaidBy chargePaidBy : chargesPaid) {
                // Only process charges that were deactivated
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

            // Update accrual portions on affected installments
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

            // Reverse the accrual transaction
            accrualTransaction.reverse();
            reversedTransactionIds.add(accrualTransaction.getId());
        }

        // Save all reversed transactions
        if (!accrualTransactions.isEmpty()) {
            loanTransactionRepository.saveAllAndFlush(accrualTransactions);
        }

        return reversedTransactionIds;
    }

    /**
     * Recalculates installment charge portions based on currently active loan charges. This ensures the repayment
     * schedule reflects the correct charge amounts after charges are removed. NO date validation is performed.
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

                if (loanCharge.isOverdueInstallmentCharge() && loanCharge.getDueLocalDate() != null) {
                    // Overdue charges are linked by due date
                    appliesToInstallment = installment.getDueDate().equals(loanCharge.getDueLocalDate());
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
}
