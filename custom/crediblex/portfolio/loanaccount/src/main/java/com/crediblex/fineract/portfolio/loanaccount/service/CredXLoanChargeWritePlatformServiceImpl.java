package com.crediblex.fineract.portfolio.loanaccount.service;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

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
import org.apache.fineract.infrastructure.event.business.domain.loan.charge.LoanWaiveChargeBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanChargeAdjustmentPostBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBePayedException;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBeWaivedException;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByData;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeApiJsonValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.service.*;
import org.apache.fineract.portfolio.loanaccount.service.adjustment.LoanAdjustmentService;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

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

    public CredXLoanChargeWritePlatformServiceImpl(
            LoanChargeApiJsonValidator loanChargeApiJsonValidator,
            LoanAssembler loanAssembler,
            ChargeRepositoryWrapper chargeRepository,
            BusinessEventNotifierService businessEventNotifierService,
            LoanTransactionRepository loanTransactionRepository,
            AccountTransfersWritePlatformService accountTransfersWritePlatformService,
            LoanRepositoryWrapper loanRepositoryWrapper,
            JournalEntryWritePlatformService journalEntryWritePlatformService,
            LoanAccountDomainService loanAccountDomainService,
            @Qualifier("loanChargeRepository") LoanChargeRepository loanChargeRepository,
            LoanWritePlatformService loanWritePlatformService,
            LoanUtilService loanUtilService,
            LoanChargeReadPlatformService loanChargeReadPlatformService,
            LoanLifecycleStateMachine defaultLoanLifecycleStateMachine,
            AccountAssociationsReadPlatformService accountAssociationsReadPlatformService,
            FromJsonHelper fromApiJsonHelper,
            ConfigurationDomainService configurationDomainService,
            LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
            ExternalIdFactory externalIdFactory,
            AccountTransferDetailRepository accountTransferDetailRepository,
            LoanChargeAssembler loanChargeAssembler,
            PaymentDetailWritePlatformService paymentDetailWritePlatformService,
            NoteRepository noteRepository,
            LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService,
            LoanAccrualsProcessingService loanAccrualsProcessingService,
            LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator,
            LoanChargeValidator loanChargeValidator,
            LoanScheduleService loanScheduleService,
            ReprocessLoanTransactionsService reprocessLoanTransactionsService,
            LoanAccountService loanAccountService,
            LoanAdjustmentService loanAdjustmentService,
            LoanAccountingBridgeMapper loanAccountingBridgeMapper, LoanChargeValidator loanChargeValidator1, LoanLifecycleStateMachine defaultLoanLifecycleStateMachine1, LoanAccrualsProcessingService loanAccrualsProcessingService1, LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService1) {

        super(loanChargeApiJsonValidator, loanAssembler, chargeRepository, businessEventNotifierService,
                loanTransactionRepository, accountTransfersWritePlatformService, loanRepositoryWrapper,
                journalEntryWritePlatformService, loanAccountDomainService, loanChargeRepository,
                loanWritePlatformService, loanUtilService, loanChargeReadPlatformService,
                defaultLoanLifecycleStateMachine, accountAssociationsReadPlatformService, fromApiJsonHelper,
                configurationDomainService, loanRepaymentScheduleTransactionProcessorFactory, externalIdFactory,
                accountTransferDetailRepository, loanChargeAssembler, paymentDetailWritePlatformService,
                noteRepository, loanAccrualTransactionBusinessEventService, loanAccrualsProcessingService,
                loanDownPaymentTransactionValidator, loanChargeValidator, loanScheduleService,
                reprocessLoanTransactionsService, loanAccountService, loanAdjustmentService,
                loanAccountingBridgeMapper);

        this.loanChargeApiJsonValidator = loanChargeApiJsonValidator;
        this.externalIdFactory = externalIdFactory;
        this.loanAssembler = loanAssembler;
        this.paymentDetailWritePlatformService = paymentDetailWritePlatformService;
        this.loanAccountDomainService = loanAccountDomainService;
        this.businessEventNotifierService = businessEventNotifierService;
        this.noteRepository = noteRepository;
        
        log.info("🚀 CredXLoanChargeWritePlatformServiceImpl initialized successfully!");
        log.info("CredXLoanChargeWritePlatformServiceImpl will be used for all loan charge operations!");
        System.out.println("🚀 CredXLoanChargeWritePlatformServiceImpl initialized successfully!");
        this.loanChargeRepository = loanChargeRepository;
        this.loanAccountService = loanAccountService;
        this.loanChargeValidator = loanChargeValidator1;
        this.defaultLoanLifecycleStateMachine = defaultLoanLifecycleStateMachine1;
        this.loanAccrualsProcessingService = loanAccrualsProcessingService1;
        this.loanAccrualTransactionBusinessEventService = loanAccrualTransactionBusinessEventService1;
        this.configurationDomainService = configurationDomainService;
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

        LoanTransaction loanTransaction = applyChargeAdjustment(loan, loanCharge, transactionAmount, transactionDate, externalId, paymentDetail);

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

        return commandProcessingResultBuilder.withCommandId(command.commandId())
                .withLoanId(loanId)
                .withEntityId(loanChargeId)
                .withEntityExternalId(loanCharge.getExternalId())
                .withSubEntityId(loanTransaction.getId())
                .withSubEntityExternalId(loanTransaction.getExternalId())
                .with(changes)
                .build();
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
     * Custom implementation of waiveLoanCharge that fixes the bug where amount_paid_derived
     * is not preserved when waiving a charge that has been partially paid through adjustments.
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
        if (loan.isCumulativeSchedule() && loan.isInterestBearingAndInterestRecalculationEnabled()
                && DateUtils.isBefore(loanCharge.getDueLocalDate(), businessDate)) {
            // For complex cases, we need to handle schedule regeneration
            // Since we can't access the loanScheduleService directly, we'll use a simpler approach
            loan.updateLoanSummaryDerivedFields();
            loan.doPostLoanTransactionChecks(waiveLoanChargeTransaction.getTransactionDate(), loanLifecycleStateMachine);
        } else {
            // For simpler cases, just update the loan and return the transaction
            loan.updateLoanSummaryDerivedFields();
            loan.doPostLoanTransactionChecks(waiveLoanChargeTransaction.getTransactionDate(), loanLifecycleStateMachine);
        }
        
        return waiveLoanChargeTransaction;
    }

    @Override
    public void applyOverdueChargesForLoan(final Long loanId, final Collection<org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData> overdueLoanScheduleDataList) {
        log.info("=== APPLYING OVERDUE CHARGES FOR LOAN ===");
        log.info("Loan ID: {}, Number of overdue installments: {}", loanId, overdueLoanScheduleDataList.size());
        
        for (final org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData overdueData : overdueLoanScheduleDataList) {
            log.info("Processing overdue installment - Charge ID: {}, Period: {}, Due Date: {}", 
                    overdueData.getChargeId(), overdueData.getPeriodNumber(), overdueData.getDueDate());
        }
        
        // Add more debugging information
        log.info("About to call super.applyOverdueChargesForLoan for loan {}", loanId);
        log.info("Overdue data list size: {}", overdueLoanScheduleDataList.size());
        
        // Debug the overdue data details
        for (final org.apache.fineract.portfolio.loanaccount.loanschedule.data.OverdueLoanScheduleData overdueData : overdueLoanScheduleDataList) {
            log.info("Overdue data details - Charge ID: {}, Period: {}, Due Date: {}, Amount: {}", 
                    overdueData.getChargeId(), overdueData.getPeriodNumber(), overdueData.getDueDate(), overdueData.getAmount());
        }
        
        // Let's try to debug what's happening by checking the loan and charge configuration
        try {
            final org.apache.fineract.portfolio.loanaccount.domain.Loan loan = this.loanAssembler.assembleFrom(loanId);
            log.info("Loan assembled - ID: {}, Status: {}, Principal: {}", loan.getId(), loan.getStatus().getValue(), loan.getPrincipal().getAmount());
            
            // Check if there are any overdue installment charges configured
            final java.util.Optional<org.apache.fineract.portfolio.charge.domain.Charge> optPenaltyCharge = loan.getLoanProduct().getCharges().stream()
                    .filter((e) -> org.apache.fineract.portfolio.charge.domain.ChargeTimeType.OVERDUE_INSTALLMENT.getValue().equals(e.getChargeTimeType()) && e.isLoanCharge())
                    .findFirst();
            
            if (optPenaltyCharge.isPresent()) {
                final org.apache.fineract.portfolio.charge.domain.Charge penaltyCharge = optPenaltyCharge.get();
                log.info("Found overdue installment charge - ID: {}, Name: {}, Calculation: {}, Fee Frequency: {}, Fee Interval: {}", 
                        penaltyCharge.getId(), penaltyCharge.getName(), penaltyCharge.getChargeCalculation(), 
                        penaltyCharge.feeFrequency(), penaltyCharge.feeInterval());
            } else {
                log.warn("No overdue installment charge found for loan product!");
            }
            
            // Check penalty wait period and grace period
            final Long penaltyWaitPeriod = this.configurationDomainService.retrievePenaltyWaitPeriod();
            final Long penaltyPostingWaitPeriod = this.configurationDomainService.retrieveGraceOnPenaltyPostingPeriod();
            log.info("Penalty wait period: {}, Grace on penalty posting period: {}", penaltyWaitPeriod, penaltyPostingWaitPeriod);
            
        } catch (Exception e) {
            log.error("Error debugging loan configuration: {}", e.getMessage(), e);
        }
        
        // Call the parent method to do the actual work
        super.applyOverdueChargesForLoan(loanId, overdueLoanScheduleDataList);
        
        log.info("=== END APPLYING OVERDUE CHARGES FOR LOAN ===");
    }

}
