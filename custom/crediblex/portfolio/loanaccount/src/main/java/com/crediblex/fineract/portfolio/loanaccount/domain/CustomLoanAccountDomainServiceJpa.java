package com.crediblex.fineract.portfolio.loanaccount.domain;

import com.crediblex.fineract.commands.LineOfCreditStatusWebhookPublisher;
import com.crediblex.fineract.commands.LoanStatusWebhookPublisher;
import com.crediblex.fineract.infrastructure.commands.utils.LoanTransactionInstallmentUtils;
import com.crediblex.fineract.portfolio.loanaccount.data.LocStatusAggregationData;
import com.crediblex.fineract.portfolio.loanaccount.util.BackdatedRepaymentValidator;
import com.crediblex.fineract.portfolio.loanaccount.util.ForeclosureAmountReconciler;
import com.crediblex.fineract.portfolio.loanaccount.util.ForeclosurePenaltyCalculator;
import com.crediblex.fineract.portfolio.loanaccount.util.ForeclosureTransactionBreakdown;
import com.crediblex.fineract.portfolio.loanaccount.util.InstallmentPenaltySyncUtils;
import com.crediblex.fineract.portfolio.loanaccount.util.LoanChargeSettlementUtils;
import com.crediblex.fineract.portfolio.loanaccount.util.LocForeclosureValidator;
import com.crediblex.fineract.portfolio.loanaccount.util.LocStatusAggregationUtils;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanBalanceChangedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanChargePaymentPostBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanChargePaymentPreBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanForeClosurePostBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanForeClosurePreBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanTransactionBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.holiday.domain.Holiday;
import org.apache.fineract.organisation.holiday.domain.HolidayRepository;
import org.apache.fineract.organisation.holiday.domain.HolidayStatusType;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.domain.AccountAssociationType;
import org.apache.fineract.portfolio.account.domain.AccountAssociations;
import org.apache.fineract.portfolio.account.domain.AccountAssociationsRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferTransaction;
import org.apache.fineract.portfolio.account.domain.AccountTransferType;
import org.apache.fineract.portfolio.account.domain.StandingInstructionRepository;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.delinquency.helper.DelinquencyEffectivePauseHelper;
import org.apache.fineract.portfolio.delinquency.service.DelinquencyReadPlatformService;
import org.apache.fineract.portfolio.delinquency.service.DelinquencyWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.data.ScheduleGeneratorDTO;
import org.apache.fineract.portfolio.loanaccount.domain.CustomLoanStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainServiceJpa;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCollateralManagementRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleProcessingWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionToRepaymentScheduleMapping;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDownPaymentTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanForeclosureValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanTransactionValidator;
import org.apache.fineract.portfolio.loanaccount.service.InterestRefundServiceDelegate;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualTransactionBusinessEventService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.apache.fineract.portfolio.loanaccount.service.LoanDownPaymentHandlerService;
import org.apache.fineract.portfolio.loanaccount.service.LoanRefundService;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.data.PostDatedChecksStatus;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecks;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecksRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Primary
public class CustomLoanAccountDomainServiceJpa extends LoanAccountDomainServiceJpa {

    private static final BigDecimal MINIMUM_LOAN_CLOSE_TOLERANCE = BigDecimal.ONE;

    private final AccountAssociationsRepository accountAssociationsRepository;
    private final AccountTransfersWritePlatformService accountTransfersWritePlatformService;

    // Status/LOC/webhook dependencies
    private final LoanStatusWebhookPublisher loanStatusWebhookPublisher;
    private final LineOfCreditStatusWebhookPublisher lineOfCreditStatusWebhookPublisher;
    private final TransactionTemplate transactionTemplate;
    private final LineOfCreditRepository lineOfCreditRepository; // optional
    private final LocStatusAggregationUtils locStatusAggregationUtils; // optional
    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository; // prefer JPA over raw SQL

    public CustomLoanAccountDomainServiceJpa(LoanAssembler loanAccountAssembler, LoanRepositoryWrapper loanRepositoryWrapper,
            LoanTransactionRepository loanTransactionRepository, ConfigurationDomainService configurationDomainService,
            HolidayRepository holidayRepository, WorkingDaysRepositoryWrapper workingDaysRepository,
            JournalEntryWritePlatformService journalEntryWritePlatformService, NoteRepository noteRepository,
            BusinessEventNotifierService businessEventNotifierService, LoanUtilService loanUtilService,
            StandingInstructionRepository standingInstructionRepository, PostDatedChecksRepository postDatedChecksRepository,
            LoanCollateralManagementRepository loanCollateralManagementRepository,
            DelinquencyWritePlatformService delinquencyWritePlatformService, LoanLifecycleStateMachine defaultLoanLifecycleStateMachine,
            ExternalIdFactory externalIdFactory, LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService,
            DelinquencyEffectivePauseHelper delinquencyEffectivePauseHelper, DelinquencyReadPlatformService delinquencyReadPlatformService,
            LoanAccrualsProcessingService loanAccrualsProcessingService,
            LoanRepaymentScheduleTransactionProcessorFactory transactionProcessorFactory,
            InterestRefundServiceDelegate interestRefundServiceDelegate, LoanTransactionValidator loanTransactionValidator,
            LoanForeclosureValidator loanForeclosureValidator, LoanDownPaymentTransactionValidator loanDownPaymentTransactionValidator,
            LoanChargeService loanChargeService, LoanScheduleService loanScheduleService,
            LoanDownPaymentHandlerService loanDownPaymentHandlerService, LoanChargeValidator loanChargeValidator,
            LoanRefundService loanRefundService, LoanAccountService loanAccountService,
            ReprocessLoanTransactionsService reprocessLoanTransactionsService, LoanAccountingBridgeMapper loanAccountingBridgeMapper,
            AccountAssociationsRepository accountAssociationsRepository,
            @Lazy AccountTransfersWritePlatformService accountTransfersWritePlatformService,
            LoanStatusWebhookPublisher loanStatusWebhookPublisher, LineOfCreditStatusWebhookPublisher lineOfCreditStatusWebhookPublisher,
            TransactionTemplate transactionTemplate, LineOfCreditRepository lineOfCreditRepository,
            LocStatusAggregationUtils locStatusAggregationUtils, LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        super(loanAccountAssembler, loanRepositoryWrapper, loanTransactionRepository, configurationDomainService, holidayRepository,
                workingDaysRepository, journalEntryWritePlatformService, noteRepository, businessEventNotifierService, loanUtilService,
                standingInstructionRepository, postDatedChecksRepository, loanCollateralManagementRepository,
                delinquencyWritePlatformService, defaultLoanLifecycleStateMachine, externalIdFactory,
                loanAccrualTransactionBusinessEventService, delinquencyEffectivePauseHelper, delinquencyReadPlatformService,
                loanAccrualsProcessingService, transactionProcessorFactory, interestRefundServiceDelegate, loanTransactionValidator,
                loanForeclosureValidator, loanDownPaymentTransactionValidator, loanChargeService, loanScheduleService,
                loanDownPaymentHandlerService, loanChargeValidator, loanRefundService, loanAccountService, reprocessLoanTransactionsService,
                loanAccountingBridgeMapper);
        this.accountAssociationsRepository = accountAssociationsRepository;
        this.accountTransfersWritePlatformService = accountTransfersWritePlatformService;
        this.loanStatusWebhookPublisher = loanStatusWebhookPublisher;
        this.lineOfCreditStatusWebhookPublisher = lineOfCreditStatusWebhookPublisher;
        this.transactionTemplate = transactionTemplate;
        this.lineOfCreditRepository = lineOfCreditRepository;
        this.locStatusAggregationUtils = locStatusAggregationUtils;
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
    }

    @Override
    @Transactional
    public LoanTransaction makeChargePayment(final Loan loan, final Long chargeId, final LocalDate transactionDate,
            final BigDecimal transactionAmount, final PaymentDetail paymentDetail, final String noteText, final ExternalId txnExternalId,
            final Integer transactionType, Integer installmentNumber) {
        boolean isAccountTransfer = true;
        checkClientOrGroupActive(loan);
        if (loan.isChargedOff() && DateUtils.isBefore(transactionDate, loan.getChargedOffOnDate())) {
            throw new GeneralPlatformDomainRuleException("error.msg.transaction.date.cannot.be.earlier.than.charge.off.date", "Loan: "
                    + loan.getId()
                    + " backdated transaction is not allowed. Transaction date cannot be earlier than the charge-off date of the loan",
                    loan.getId());
        }
        businessEventNotifierService.notifyPreBusinessEvent(new LoanChargePaymentPreBusinessEvent(loan));

        final List<Long> existingTransactionIds = new ArrayList<>();
        final List<Long> existingReversedTransactionIds = new ArrayList<>();

        final Money paymentAmout = Money.of(loan.getCurrency(), transactionAmount);
        final LoanTransactionType loanTransactionType = LoanTransactionType.fromInt(transactionType);

        final LoanTransaction newPaymentTransaction = loanTransactionType.isVatDeductionAtDisbursement()
                ? LoanTransaction.vatDeductionAtDisbursement(loan.getOffice(), paymentAmout, paymentDetail, transactionDate, txnExternalId)
                : LoanTransaction.loanPayment(null, loan.getOffice(), paymentAmout, paymentDetail, transactionDate, txnExternalId,
                        loanTransactionType);

        if (loanTransactionType.isRepaymentAtDisbursement() || loanTransactionType.isVatDeductionAtDisbursement()) {
            loan.handlePayDisbursementTransaction(chargeId, newPaymentTransaction, existingTransactionIds, existingReversedTransactionIds);
        } else {
            final boolean allowTransactionsOnHoliday = this.configurationDomainService.allowTransactionsOnHolidayEnabled();
            final List<Holiday> holidays = this.holidayRepository.findByOfficeIdAndGreaterThanDate(loan.getOfficeId(), transactionDate,
                    HolidayStatusType.ACTIVE.getValue());
            final WorkingDays workingDays = this.workingDaysRepository.findOne();
            final boolean allowTransactionsOnNonWorkingDay = this.configurationDomainService.allowTransactionsOnNonWorkingDayEnabled();
            final boolean isHolidayEnabled = this.configurationDomainService.isRescheduleRepaymentsOnHolidaysEnabled();
            HolidayDetailDTO holidayDetailDTO = new HolidayDetailDTO(isHolidayEnabled, holidays, workingDays, allowTransactionsOnHoliday,
                    allowTransactionsOnNonWorkingDay);

            loanDownPaymentTransactionValidator.validateAccountStatus(loan, LoanEvent.LOAN_CHARGE_PAYMENT);
            loanTransactionValidator.validateRepaymentDateIsOnHoliday(newPaymentTransaction.getTransactionDate(),
                    holidayDetailDTO.isAllowTransactionsOnHoliday(), holidayDetailDTO.getHolidays());
            loanTransactionValidator.validateRepaymentDateIsOnNonWorkingDay(newPaymentTransaction.getTransactionDate(),
                    holidayDetailDTO.getWorkingDays(), holidayDetailDTO.isAllowTransactionsOnNonWorkingDay());
            loanTransactionValidator.validateActivityNotBeforeLastTransactionDate(loan, newPaymentTransaction.getTransactionDate(),
                    LoanEvent.LOAN_CHARGE_PAYMENT);
            loanTransactionValidator.validateActivityNotBeforeClientOrGroupTransferDate(loan, LoanEvent.LOAN_CHARGE_PAYMENT,
                    newPaymentTransaction.getTransactionDate());
            loanChargeService.makeChargePayment(loan, chargeId, defaultLoanLifecycleStateMachine, existingTransactionIds,
                    existingReversedTransactionIds, newPaymentTransaction, installmentNumber);
        }
        loanAccountService.saveLoanTransactionWithDataIntegrityViolationChecks(newPaymentTransaction);
        loanAccountService.saveAndFlushLoanWithDataIntegrityViolationChecks(loan);
        loan.updateLoanSummaryDerivedFields();
        if (StringUtils.isNotBlank(noteText)) {
            final Note note = Note.loanTransactionNote(loan, newPaymentTransaction, noteText);
            this.noteRepository.save(note);
        }

        loanAccrualsProcessingService.processAccrualsOnInterestRecalculation(loan, loan.isInterestBearingAndInterestRecalculationEnabled(),
                false);
        businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));
        businessEventNotifierService.notifyPostBusinessEvent(new LoanChargePaymentPostBusinessEvent(newPaymentTransaction));

        postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds, isAccountTransfer);
        loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);
        return newPaymentTransaction;
    }

    @Override
    public LoanTransaction foreCloseLoan(Loan loan, final LocalDate foreClosureDate, final String noteText, final ExternalId externalId,
            Map<String, Object> changes) {

        if (loan.isChargedOff() && DateUtils.isBefore(foreClosureDate, loan.getChargedOffOnDate())) {
            throw new GeneralPlatformDomainRuleException("error.msg.transaction.date.cannot.be.earlier.than.charge.off.date", "Loan: "
                    + loan.getId()
                    + " backdated transaction is not allowed. Transaction date cannot be earlier than the charge-off date of the loan",
                    loan.getId());
        }

        Money totalPrincipalBeforeForClosure = loan.getLoanRepaymentScheduleDetail().getPrincipal();

        businessEventNotifierService.notifyPreBusinessEvent(new LoanForeClosurePreBusinessEvent(loan));
        MonetaryCurrency currency = loan.getCurrency();
        List<LoanTransaction> newTransactions = new ArrayList<>();

        final List<Long> existingTransactionIds = new ArrayList<>();
        final List<Long> existingReversedTransactionIds = new ArrayList<>();
        existingTransactionIds.addAll(loan.findExistingTransactionIds());
        existingReversedTransactionIds.addAll(loan.findExistingReversedTransactionIds());
        final ScheduleGeneratorDTO scheduleGeneratorDTO = null;
        if (InstallmentPenaltySyncUtils.syncOutstandingOverduePenaltyOntoSchedule(loan)) {
            loan = loanAccountService.saveAndFlushLoanWithDataIntegrityViolationChecks(loan);
        }
        final LoanRepaymentScheduleInstallment foreCloseDetail = loan.fetchLoanForeclosureDetail(foreClosureDate);

        loanAccrualsProcessingService.processAccrualsOnLoanForeClosure(loan, foreClosureDate, newTransactions);

        Money interestPayable = foreCloseDetail.getInterestCharged(currency);
        Money feePayable = foreCloseDetail.getFeeChargesCharged(currency);
        // foreCloseDetail.getPenaltyChargesCharged() sums each installment's CACHED penaltyChargesOutstanding
        // field. On a multi-installment loan with 2+ separately-overdue installments (each carrying its own
        // daily-accruing LPI charges), that cache can go stale/inflated relative to what the loan's charges
        // actually total - the exact same class of stale-installment-penalty-cache bug fixed for reverse-LPI in
        // CredXLoanChargeWritePlatformServiceImpl (see BUG_REPORT.md Finding #2), just reached via a different,
        // unpatched path here. Recomputing directly from loan.getActiveCharges() (the source of truth) avoids
        // ever withdrawing more than the loan actually owes from the linked savings account during foreclosure
        // settlement - see BUG_REPORT.md Finding #3, where this stale cache caused a real 55.12 AED
        // over-withdrawal and left the loan stuck "Overpaid" instead of "Closed".
        Money penaltyPayable = ForeclosurePenaltyCalculator.computePenaltyPayableFromActiveCharges(loan, foreClosureDate, currency);
        Money taxPayable = foreCloseDetail.getTaxChargesCharged(currency);
        Money payPrincipal = foreCloseDetail.getPrincipal(currency);

        // For Factor Rate loans, always use loan summary totals for fees and taxes
        // The installment-based calculation (retrieveIncomeOutstandingTillDate) only includes fees from installments
        // due up to the foreclosure date, not all outstanding fees. Since Factor Rate loans charge fees upfront and
        // allocate them across all installments, foreclosure should include ALL outstanding fees/taxes from the loan
        // summary.
        // Extract values as BigDecimal immediately to avoid entity state issues before collection modifications
        if (loan.isFactorRateEnabled()) {
            final LoanSummary loanSummary = loan.getSummary();
            if (loanSummary != null) {
                BigDecimal feeOutstandingAmount = loanSummary.getTotalFeeChargesOutstanding();
                BigDecimal taxOutstandingAmount = loanSummary.getTotalTaxChargesOutstanding();

                // Always use loan summary values for Factor Rate loans to ensure all outstanding fees/taxes are
                // included
                // Create new Money objects from extracted BigDecimal values to avoid entity references
                feePayable = Money.of(currency, feeOutstandingAmount);
                taxPayable = Money.of(currency, taxOutstandingAmount);
            }
        }

        LoanTransaction payment = null;
        List<Long> transactionIds = new ArrayList<>();

        // Validations MUST run before updateInstallmentsPostDate: that rewrite replaces the unpaid installment's due
        // date with the foreclosure date itself. LocForeclosureValidator (and any check that reads the live schedule
        // due dates) would then always see foreclosureDate == dueDate and incorrectly reject every early LOC
        // foreclosure as "on or past due".
        loanDownPaymentTransactionValidator.validateAccountStatus(loan, LoanEvent.LOAN_FORECLOSURE);

        loanForeclosureValidator.validateForForeclosure(loan, foreClosureDate);
        // Fix 1: for LOC (payable/receivable) loans, block foreclosure once the loan is on/past its earliest unpaid
        // installment due date - that is no longer an early-settlement scenario. No-op for non-LOC loans.
        LocForeclosureValidator.validateNotDueOrOverdue(loan, foreClosureDate, loanLineOfCreditParamsRepository.findByLoanId(loan.getId()));
        // General backdate-too-far-in-the-past guard, independent of (and in addition to) the "not before the
        // loan's last non-waiver transaction date" check just above - see BackdatedRepaymentValidator javadoc and
        // BUG_REPORT.md "Backdate limit" finding.
        BackdatedRepaymentValidator.validateWithinBackdateLimit(loan, foreClosureDate, "foreclosure");

        // LMS-119 (part 2): updateInstallmentsPostDate rebuilds the final installment from GROSS scheduled principal
        // with zero paid, relying on the full-history reprocess to re-apply payments already made against the merged
        // installments. On the single-transaction settlement path (non-recalc foreclosure, see
        // CustomLoanDownPaymentHandlerService) history is NOT replayed, so a partial principal/interest already paid on
        // a merged installment - e.g. a mid-period repayment or an LPI-reversal reallocation - would be dropped and
        // double-counted as a phantom overpayment (loan 14288: 9.67). Capture what was already paid on the
        // to-be-merged installments here, then carry it onto the rewritten final installment below so its outstanding
        // reflects reality. No-op when nothing was pre-paid on those installments (the normal foreclosure case).
        final boolean singleTxnSettlementPath = !loan.isFactorRateEnabled() && !loan.isInterestBearingAndInterestRecalculationEnabled();
        Money mergedPaidPrincipal = Money.zero(currency);
        Money mergedPaidInterest = Money.zero(currency);
        if (singleTxnSettlementPath) {
            for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
                if (!DateUtils.isAfter(foreClosureDate, installment.getDueDate())) {
                    mergedPaidPrincipal = mergedPaidPrincipal.plus(installment.getPrincipalCompleted(currency));
                    mergedPaidInterest = mergedPaidInterest.plus(installment.getInterestPaid(currency));
                }
            }
        }

        if (!loan.isFactorRateEnabled()) {
            updateInstallmentsPostDate(loan, foreClosureDate);
        }

        if (singleTxnSettlementPath && (mergedPaidPrincipal.isGreaterThanZero() || mergedPaidInterest.isGreaterThanZero())) {
            final List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
            if (!installments.isEmpty()) {
                final LoanRepaymentScheduleInstallment finalInstallment = installments.get(installments.size() - 1);
                if (mergedPaidInterest.isGreaterThanZero()) {
                    finalInstallment.payInterestComponent(foreClosureDate, mergedPaidInterest);
                }
                if (mergedPaidPrincipal.isGreaterThanZero()) {
                    finalInstallment.payPrincipalComponent(foreClosureDate, mergedPaidPrincipal);
                }
            }
        }

        // Overpayment guard (all products). The components above are assembled from sources that do not always line up
        // with the schedule this settlement is finally allocated against - Factor Rate fees come from the loan summary,
        // and LPI/penalty comes from active charges that may sit on an already-complete installment or one the
        // updateInstallmentsPostDate rewrite just removed. The waterfall processor books exactly
        // (rawAmount - Σ installment.totalOutstanding) as overpayment, which over-withdraws from the linked savings
        // account and leaves the loan stuck "Overpaid" instead of "Closed" (see UAT loans 15628, 15109, 11188).
        // Clamp the settlement to what the (now-prepared) schedule can actually absorb; the trimmed amount is the
        // LPI / unearned fee that an early foreclosure is meant to waive anyway. No-op when the amount already fits.
        final ForeclosureAmountReconciler.Result reconciled = ForeclosureAmountReconciler.reconcile(loan, currency, payPrincipal,
                interestPayable, feePayable, penaltyPayable, taxPayable);
        if (reconciled.wasReduced()) {
            org.slf4j.LoggerFactory.getLogger(CustomLoanAccountDomainServiceJpa.class)
                    .info("Foreclosure on loan {} as of {}: auto-waived {} of LPI/unearned fee to prevent overpayment "
                            + "(raw {} -> settleable {}).", loan.getId(), foreClosureDate, reconciled.waived().getAmount(),
                            payPrincipal.plus(interestPayable).plus(feePayable).plus(penaltyPayable).plus(taxPayable).getAmount(),
                            reconciled.total().getAmount());
            payPrincipal = reconciled.principal();
            interestPayable = reconciled.interest();
            feePayable = reconciled.fee();
            penaltyPayable = reconciled.penalty();
            taxPayable = reconciled.tax();
        }

        org.slf4j.LoggerFactory.getLogger(CustomLoanAccountDomainServiceJpa.class)
                .info("Foreclosure on loan {} as of {}: about to settle {} (principal={} interest={} fees={} penalties={} tax={}) "
                        + "against schedule state: {}", loan.getId(), foreClosureDate,
                        payPrincipal.plus(interestPayable).plus(feePayable).plus(penaltyPayable).plus(taxPayable).getAmount(),
                        payPrincipal.getAmount(), interestPayable.getAmount(), feePayable.getAmount(), penaltyPayable.getAmount(),
                        taxPayable.getAmount(), describeOutstandingComponents(loan, currency));

        /// //This is where we should be doing the transfer from.

        // Check if loan has a linked savings account for foreclosure transfer
        AccountAssociations accountAssociation = accountAssociationsRepository.findByLoanIdAndType(loan.getId(),
                AccountAssociationType.LINKED_ACCOUNT_ASSOCIATION.getValue());

        if (accountAssociation != null && accountAssociation.linkedSavingsAccount() != null) {
            // Foreclosure via account transfer from linked savings account
            SavingsAccount linkedSavingsAccount = accountAssociation.linkedSavingsAccount();

            // Validate that the linked savings account is active
            if (!linkedSavingsAccount.isActive()) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.foreclosure.linked.savings.account.not.active",
                        "The linked savings account is not active and cannot be used for foreclosure", linkedSavingsAccount.getId());
            }

            BigDecimal totalForeclosureAmount = payPrincipal.plus(interestPayable).plus(feePayable).plus(penaltyPayable).plus(taxPayable)
                    .getAmount();

            // Prepare transfer details
            // NO need to validate balances, it will be validated in the withdraw process
            final boolean isRegularTransaction = true;
            final boolean isExceptionForBalanceCheck = false;

            // Create AccountTransferDTO for savings to loan transfer
            final AccountTransferDTO accountTransferDTO = new AccountTransferDTO(foreClosureDate, totalForeclosureAmount,
                    PortfolioAccountType.SAVINGS, PortfolioAccountType.LOAN, linkedSavingsAccount.getId(), loan.getId(),
                    "Foreclosure payment from linked savings account " + linkedSavingsAccount.getAccountNumber(), null, null, null, // paymentDetail
                    null, // fromTransferType
                    LoanTransactionType.REPAYMENT.getValue(), // toTransferType
                    null, // chargeId
                    null, // loanInstallmentNumber
                    AccountTransferType.LOAN_FORECLOSURE.getValue(), // transferType
                    null, // accountTransferDetails
                    noteText, externalId, loan, null, // toSavingsAccount
                    linkedSavingsAccount, // fromSavingsAccount
                    isRegularTransaction, isExceptionForBalanceCheck);

            // Execute the account transfer - this will handle both withdrawal and repayment
            Long transferTransactionId = accountTransfersWritePlatformService.transferFunds(accountTransferDTO);

            Optional<AccountTransferTransaction> accountTransferTransaction = this.accountTransfersWritePlatformService
                    .getToLoanTransactionFromAccountTransferId(transferTransactionId);

            if (accountTransferTransaction.isEmpty()) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.foreclosure.linked.savings.account.transfer.failed",
                        "The transfer from linked savings account failed, loan foreclosure cannot be completed", loan.getId());
            }

            payment = accountTransferTransaction.get().getToLoanTransaction();
            if (payment != null && payment.getLoan() != null) {
                loan = payment.getLoan();
            }
            newTransactions.add(payment);
            // Loan-side foreclosure allocation already ran inside transferFunds (LOAN_FORECLOSURE branch).

        } else {

            if (payPrincipal.plus(interestPayable).plus(feePayable).plus(penaltyPayable).plus(taxPayable).isGreaterThanZero()) {
                final PaymentDetail paymentDetail = null;
                payment = LoanTransaction.repayment(loan.getOffice(),
                        payPrincipal.plus(interestPayable).plus(feePayable).plus(penaltyPayable).plus(taxPayable), paymentDetail,
                        foreClosureDate, externalId);
                payment.updateLoan(loan);
                // Use the reconciled (overpayment-clamped) components, not a fresh raw recompute, so the breakdown
                // matches the trimmed settlement total.
                ForeclosureTransactionBreakdown.applyComponents(payment, payPrincipal, interestPayable, feePayable, penaltyPayable,
                        taxPayable);
                newTransactions.add(payment);
            }

            handleForeClosureTransactions(loan, payment, defaultLoanLifecycleStateMachine, scheduleGeneratorDTO);
        }

        if (loan.isReceivableLocLoan()) {
            loan.getLoanRepaymentScheduleDetail().setPrincipal(totalPrincipalBeforeForClosure.getAmount());
        }

        LoanChargeSettlementUtils.closeIfFullySettled(loan, foreClosureDate, defaultLoanLifecycleStateMachine);

        loanAccrualsProcessingService.reprocessExistingAccruals(loan);
        if (loan.isInterestBearingAndInterestRecalculationEnabled()) {
            loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
        }

        for (LoanTransaction newTransaction : newTransactions) {
            loanAccountService.saveLoanTransactionWithDataIntegrityViolationChecks(newTransaction);
            transactionIds.add(newTransaction.getId());
        }
        changes.put("transactions", transactionIds);
        changes.put("eventAmount", payPrincipal.getAmount().negate());

        // For multi-disbursement loans, check if the loan should be closed based on disbursed amounts only
        if (loan.isMultiDisburmentLoan()) {
            final BigDecimal totalOutstandingForMultiDisburse = calculateMultiDisbursementLoanOutstanding(loan, currency);
            if (totalOutstandingForMultiDisburse.compareTo(BigDecimal.ZERO) == 0) {
                // Loan has zero outstanding based on disbursed amounts - close it
                loan.setClosedOnDate(foreClosureDate);
                defaultLoanLifecycleStateMachine.transition(LoanEvent.REPAID_IN_FULL, loan);
            }
        }
        loan = loanAccountService.saveAndFlushLoanWithDataIntegrityViolationChecks(loan);

        // Post-condition safety net ("flight check" at execution). Independent of how the settlement amount was
        // derived, verify the ACTUAL outcome: foreclosure must close the loan with neither overpayment (money
        // over-withdrawn from the linked savings account) nor a residual balance (an undercharge that leaves the loan
        // open). If either holds, abort the whole @Transactional - this rolls back the savings withdrawal and every
        // loan posting atomically, so a miscalculated settlement moves no money and is surfaced for review instead of
        // being silently committed. This is the guarantee the amount computation alone cannot give.
        final Money residualOverpaid = loan.getTotalOverpaidAsMoney();
        if (residualOverpaid != null && residualOverpaid.isGreaterThanZero()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.foreclosure.would.overpay",
                    "Foreclosure of loan " + loan.getId() + " as of " + foreClosureDate + " would overpay by "
                            + residualOverpaid.getAmount() + ": " + describeOutstandingComponents(loan, currency)
                            + ". Aborting to avoid over-withdrawal; review the loan's outstanding charges/schedule before retrying.",
                    loan.getId(), residualOverpaid.getAmount());
        }
        final Money residualOutstanding = loan.getSummary() == null ? null : loan.getSummary().getTotalOutstanding(currency);
        if (residualOutstanding != null && residualOutstanding.isGreaterThanZero()) {
            // Name the component(s) the shortfall sits in. Without it the total alone cannot distinguish an
            // under-quoted principal from unearned LPI the schedule still carries, and the offending state only
            // exists inside this about-to-be-rolled-back transaction, so it cannot be inspected afterwards.
            throw new GeneralPlatformDomainRuleException("error.msg.loan.foreclosure.would.not.close",
                    "Foreclosure of loan " + loan.getId() + " as of " + foreClosureDate + " would leave " + residualOutstanding.getAmount()
                            + " outstanding (loan would not close): " + describeOutstandingComponents(loan, currency)
                            + ". Aborting; review the loan's charges/schedule before retrying.",
                    loan.getId(), residualOutstanding.getAmount());
        }

        if (StringUtils.isNotBlank(noteText)) {
            changes.put("note", noteText);
            final Note note = Note.loanNote(loan, noteText);
            this.noteRepository.save(note);
        }

        postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds, false);
        loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);
        businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));
        businessEventNotifierService.notifyPostBusinessEvent(new LoanForeClosurePostBusinessEvent(payment));
        return payment;
    }

    /**
     * Renders the loan's still-outstanding components plus the surviving schedule rows behind them. Used only to enrich
     * the foreclosure post-condition failure, whose offending state lives inside the transaction being rolled back and
     * is therefore invisible in the database afterwards.
     */
    private String describeOutstandingComponents(final Loan loan, final MonetaryCurrency currency) {
        final StringBuilder sb = new StringBuilder();
        final LoanSummary summary = loan.getSummary();
        if (summary != null) {
            sb.append("principal=").append(summary.getTotalPrincipalOutstanding()).append(", interest=")
                    .append(summary.getTotalInterestOutstanding()).append(", fees=").append(summary.getTotalFeeChargesOutstanding())
                    .append(", penalties=").append(summary.getTotalPenaltyChargesOutstanding());
        }
        sb.append("; unsettled installments after foreclosure rewrite: [");
        boolean first = true;
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (!installment.getTotalOutstanding(currency).isGreaterThanZero()) {
                continue;
            }
            if (!first) {
                sb.append("; ");
            }
            first = false;
            sb.append('#').append(installment.getInstallmentNumber()).append(' ').append(installment.getFromDate()).append("->")
                    .append(installment.getDueDate()).append(" principal=")
                    .append(installment.getPrincipalOutstanding(currency).getAmount()).append(" interest=")
                    .append(installment.getInterestOutstanding(currency).getAmount()).append(" fees=")
                    .append(installment.getFeeChargesOutstanding(currency).getAmount()).append(" penalties=")
                    .append(installment.getPenaltyChargesOutstanding(currency).getAmount());
        }
        return sb.append(']').toString();
    }

    /**
     * The base rewrite merges every installment due on or after the foreclosure date into one installment whose
     * principal is the sum of the merged installments' <em>gross</em> principal
     * ({@code totalPrincipal.plus(installment.getPrincipal(currency))}), and constructs it with nothing marked as paid.
     * Principal already settled on any of those installments is therefore deleted along with them - and with it, the
     * schedule's record of money the borrower has genuinely already paid.
     * <p>
     * That breaks the invariant "schedule-recorded payments == cash actually received", which shows up twice over. The
     * orphaned amount has no installment left to sit on, so it is booked as <em>overpayment</em>; and the merged
     * installment is simultaneously that same amount <em>short</em> of what the settlement quote covers, because
     * {@link Loan#fetchLoanForeclosureDetail} draws its principal from {@code summary.getTotalPrincipalOutstanding()},
     * which is net of the already-paid amount. The foreclosure post-condition guard tests overpayment first, so it
     * reports an overpay even though the settlement is also short by the same figure - and rolls everything back.
     * <p>
     * Interest and charges drift the same way. The merged installment's interest/fees/penalties come from
     * {@link Loan#retrieveIncomeForOverlappingPeriod}, which copies the gross indices of
     * {@code fetchInterestFeeAndPenaltyTillDate} and never subtracts the accounted (paid/waived) counterparts that the
     * quote's {@code retrieveIncomeOutstandingTillDate} does subtract. Anything already paid inside the straddling
     * window is therefore re-charged, and the waterfall diverts settlement money to it - leaving principal short by
     * that amount instead.
     * <p>
     * Loan 14288 hit both. An LPI reversal moved 4.80 + 4.87 of paid penalty onto installment 4's principal; the
     * rewrite dropped installment 4, leaving 21,180.39 recorded against 21,190.06 received (the 9.67 overpay). With
     * that corrected, the merged 2026-08-10 -&gt; 2026-08-20 window re-charged the 91.13 of LPI already paid within it,
     * leaving principal 91.13 short. The full-history reprocess used to mask both by rebuilding every split from
     * scratch, but it recasts the merged installment's interest down to the last transaction date - the larger error
     * the single-transaction path was introduced to avoid.
     * <p>
     * Carrying every already-settled component forward keeps both sides whole: the schedule still accounts for exactly
     * the cash received, and the merged installment's outstanding equals what the quote charges for. Loans with nothing
     * settled inside the merged window are unaffected.
     */
    @Override
    protected void updateInstallmentsPostDate(final Loan loan, final LocalDate transactionDate) {
        final MonetaryCurrency currency = loan.getCurrency();
        final Money zero = Money.zero(currency);

        // Principal: the base sums the merged-away installments' gross principal, so recover what they had settled.
        // Mirrors the base method's own selection of the installments it is about to merge away.
        Money settledPrincipal = zero;
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (!DateUtils.isAfter(transactionDate, installment.getDueDate())) {
                settledPrincipal = settledPrincipal.plus(orZero(installment.getPrincipalCompleted(currency), zero));
            }
        }

        // Interest and charges: the base takes these from the straddling installment via
        // Loan#retrieveIncomeForOverlappingPeriod, which copies the gross indices of fetchInterestFeeAndPenaltyTillDate
        // and never subtracts the "accounted" (paid/waived) counterparts the quote's retrieveIncomeOutstandingTillDate
        // does. Gather them before the rewrite disposes of that installment and deactivates charges.
        Money settledInterest = zero;
        Money waivedInterest = zero;
        Money settledFee = zero;
        Money waivedFee = zero;
        Money settledPenalty = zero;
        Money waivedPenalty = zero;
        final LoanRepaymentScheduleInstallment straddling = findStraddlingInstallment(loan, transactionDate);
        if (straddling != null) {
            settledInterest = orZero(straddling.getInterestPaid(currency), zero);
            waivedInterest = orZero(straddling.getInterestWaived(currency), zero);
            final boolean isFirstNormalInstallment = straddling.getInstallmentNumber().equals(
                    LoanRepaymentScheduleProcessingWrapper.fetchFirstNormalInstallmentNumber(loan.getRepaymentScheduleInstallments()));
            for (final LoanCharge charge : loan.getLoanCharges()) {
                if (charge == null || !charge.isActive() || charge.isDueAtDisbursement()
                        || !charge.isDueInPeriod(straddling.getFromDate(), transactionDate, isFirstNormalInstallment)) {
                    continue;
                }
                if (charge.isPenaltyCharge()) {
                    settledPenalty = settledPenalty.plus(orZero(charge.getAmountPaid(currency), zero));
                    waivedPenalty = waivedPenalty.plus(orZero(charge.getAmountWaived(currency), zero));
                } else {
                    settledFee = settledFee.plus(orZero(charge.getAmountPaid(currency), zero));
                    waivedFee = waivedFee.plus(orZero(charge.getAmountWaived(currency), zero));
                }
            }
        }

        super.updateInstallmentsPostDate(loan, transactionDate);

        // The rewrite is the only thing that leaves an installment due exactly on the foreclosure date; everything
        // else due on or after it was just removed.
        LoanRepaymentScheduleInstallment merged = null;
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (DateUtils.isEqual(transactionDate, installment.getDueDate())) {
                merged = installment;
            }
        }
        if (merged == null) {
            return;
        }

        // Waivers first: the pay* helpers cap at outstanding, which is itself net of waived.
        if (waivedFee.isGreaterThanZero() || waivedPenalty.isGreaterThanZero()) {
            merged.updateChargePortion(orZero(merged.getFeeChargesCharged(currency), zero), waivedFee, zero,
                    orZero(merged.getPenaltyChargesCharged(currency), zero), waivedPenalty, zero,
                    orZero(merged.getTaxChargesCharged(currency), zero), zero, zero);
        }
        if (waivedInterest.isGreaterThanZero()) {
            merged.waiveInterestComponent(transactionDate, waivedInterest);
        }
        final Money appliedPrincipal = settledPrincipal.isGreaterThanZero()
                ? orZero(merged.payPrincipalComponent(transactionDate, settledPrincipal), zero)
                : zero;
        final Money appliedInterest = settledInterest.isGreaterThanZero()
                ? orZero(merged.payInterestComponent(transactionDate, settledInterest), zero)
                : zero;
        final Money appliedFee = settledFee.isGreaterThanZero() ? orZero(merged.payFeeChargesComponent(transactionDate, settledFee), zero)
                : zero;
        final Money appliedPenalty = settledPenalty.isGreaterThanZero()
                ? orZero(merged.payPenaltyChargesComponent(transactionDate, settledPenalty), zero)
                : zero;

        if (appliedPrincipal.isGreaterThanZero() || appliedInterest.isGreaterThanZero() || appliedFee.isGreaterThanZero()
                || appliedPenalty.isGreaterThanZero()) {
            org.slf4j.LoggerFactory.getLogger(CustomLoanAccountDomainServiceJpa.class)
                    .info("Foreclosure on loan {} as of {}: carried already-settled amounts onto merged installment {} {}->{} "
                            + "(principal={} interest={} fees={} penalties={}); it now owes principal={} interest={} fees={} penalties={}",
                            loan.getId(), transactionDate, merged.getInstallmentNumber(), merged.getFromDate(), merged.getDueDate(),
                            appliedPrincipal.getAmount(), appliedInterest.getAmount(), appliedFee.getAmount(), appliedPenalty.getAmount(),
                            orZero(merged.getPrincipalOutstanding(currency), zero).getAmount(),
                            orZero(merged.getInterestOutstanding(currency), zero).getAmount(),
                            orZero(merged.getFeeChargesOutstanding(currency), zero).getAmount(),
                            orZero(merged.getPenaltyChargesOutstanding(currency), zero).getAmount());
        }
    }

    private static Money orZero(final Money value, final Money zero) {
        return value == null ? zero : value;
    }

    /**
     * The installment whose period contains the foreclosure date - the one the base rewrite prorates interest and
     * charges from, and therefore the one whose already-settled amounts the merged installment must inherit.
     */
    private LoanRepaymentScheduleInstallment findStraddlingInstallment(final Loan loan, final LocalDate transactionDate) {
        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            if (DateUtils.isDateInRangeFromInclusiveToExclusive(transactionDate, installment.getFromDate(), installment.getDueDate())) {
                return installment;
            }
        }
        return null;
    }

    @Transactional
    @Override
    public LoanTransaction makeRepayment(final LoanTransactionType repaymentTransactionType, Loan loan, final LocalDate transactionDate,
            final BigDecimal transactionAmount, final PaymentDetail paymentDetail, final String noteText, final ExternalId txnExternalId,
            final boolean isRecoveryRepayment, final String chargeRefundChargeType, boolean isAccountTransfer,
            HolidayDetailDTO holidayDetailDto, Boolean isHolidayValidationDone, final boolean isLoanToLoanTransfer) {
        checkClientOrGroupActive(loan);

        LoanBusinessEvent repaymentEvent = getLoanRepaymentTypeBusinessEvent(repaymentTransactionType, isRecoveryRepayment, loan);
        businessEventNotifierService.notifyPreBusinessEvent(repaymentEvent);

        // TODO: Is it required to validate transaction date with meeting dates
        // if repayments is synced with meeting?
        /*
         * if(loan.isSyncDisbursementWithMeeting()){ // validate actual disbursement date against meeting date
         * CalendarInstance calendarInstance = this.calendarInstanceRepository.findCalendarInstaneByLoanId
         * (loan.getId(), CalendarEntityType.LOANS.getValue()); this.loanEventApiJsonValidator
         * .validateRepaymentDateWithMeetingDate(transactionDate, calendarInstance); }
         */

        final List<Long> existingTransactionIds = new ArrayList<>();
        final List<Long> existingReversedTransactionIds = new ArrayList<>();

        final Money repaymentAmount = Money.of(loan.getCurrency(), transactionAmount);
        LoanTransaction newRepaymentTransaction;
        if (isRecoveryRepayment) {
            newRepaymentTransaction = LoanTransaction.recoveryRepayment(loan.getOffice(), repaymentAmount, paymentDetail, transactionDate,
                    txnExternalId);
        } else {
            newRepaymentTransaction = LoanTransaction.repaymentType(repaymentTransactionType, loan.getOffice(), repaymentAmount,
                    paymentDetail, transactionDate, txnExternalId, chargeRefundChargeType);
        }

        LocalDate recalculateFrom = null;
        if (loan.isInterestBearingAndInterestRecalculationEnabled()) {
            recalculateFrom = transactionDate;
        }
        final ScheduleGeneratorDTO scheduleGeneratorDTO = this.loanUtilService.buildScheduleGeneratorDTO(loan, recalculateFrom,
                holidayDetailDto);

        if (!isHolidayValidationDone) {
            final HolidayDetailDTO holidayDetailDTO = scheduleGeneratorDTO.getHolidayDetailDTO();
            loanTransactionValidator.validateRepaymentDateIsOnHoliday(newRepaymentTransaction.getTransactionDate(),
                    holidayDetailDTO.isAllowTransactionsOnHoliday(), holidayDetailDTO.getHolidays());
            loanTransactionValidator.validateRepaymentDateIsOnNonWorkingDay(newRepaymentTransaction.getTransactionDate(),
                    holidayDetailDTO.getWorkingDays(), holidayDetailDTO.isAllowTransactionsOnNonWorkingDay());
        }
        final LoanEvent event = isRecoveryRepayment ? LoanEvent.LOAN_RECOVERY_PAYMENT : LoanEvent.LOAN_REPAYMENT_OR_WAIVER;
        loanTransactionValidator.validateActivityNotBeforeLastTransactionDate(loan, newRepaymentTransaction.getTransactionDate(), event);
        loanDownPaymentTransactionValidator.validateRepaymentTypeAccountStatus(loan, newRepaymentTransaction, event);
        loanTransactionValidator.validateActivityNotBeforeClientOrGroupTransferDate(loan, event,
                newRepaymentTransaction.getTransactionDate());
        makeRepayment(loan, newRepaymentTransaction, defaultLoanLifecycleStateMachine, existingTransactionIds,
                existingReversedTransactionIds, scheduleGeneratorDTO);
        closeLoanIfFinalRepaymentResidualIsWithinTolerance(loan, newRepaymentTransaction);

        if (loan.isInterestBearingAndInterestRecalculationEnabled()) {
            loanAccrualsProcessingService.reprocessExistingAccruals(loan);
            loanAccrualsProcessingService.processIncomePostingAndAccruals(loan);
        }

        loanAccountService.saveLoanTransactionWithDataIntegrityViolationChecks(newRepaymentTransaction);
        loan = loanAccountService.saveAndFlushLoanWithDataIntegrityViolationChecks(loan);

        if (StringUtils.isNotBlank(noteText)) {
            final Note note = Note.loanTransactionNote(loan, newRepaymentTransaction, noteText);
            this.noteRepository.save(note);
        }

        loanAccrualsProcessingService.processAccrualsOnInterestRecalculation(loan, loan.isInterestBearingAndInterestRecalculationEnabled(),
                false);

        setLoanDelinquencyTag(loan, transactionDate);

        if (!repaymentTransactionType.isChargeRefund()) {
            LoanTransactionBusinessEvent transactionRepaymentEvent = getTransactionRepaymentTypeBusinessEvent(repaymentTransactionType,
                    isRecoveryRepayment, newRepaymentTransaction);
            businessEventNotifierService.notifyPostBusinessEvent(new LoanBalanceChangedBusinessEvent(loan));
            businessEventNotifierService.notifyPostBusinessEvent(transactionRepaymentEvent);
        }

        postJournalEntries(loan, existingTransactionIds, existingReversedTransactionIds, isAccountTransfer, isLoanToLoanTransfer);
        loanAccrualTransactionBusinessEventService.raiseBusinessEventForAccrualTransactions(loan, existingTransactionIds);

        // disable all active standing orders linked to this loan if status
        // changes to closed
        disableStandingInstructionsLinkedToClosedLoan(loan);

        // Compute and persist custom loan status + LOC aggregation; publish after commit
        try {
            CustomLoanStatus oldCustomStatus = loan.hasCustomStatus() ? loan.getCustomLoanStatus() : null;
            CustomLoanStatus customLoanStatus = LoanTransactionInstallmentUtils.computeCustomLoanStatusForLoan(loan);
            loan.setCustomLoanStatus(customLoanStatus);

            // Prefer JPA repository for drawdown/LOC lookup
            Optional<LoanLineOfCreditParams> locParamsOpt = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
            boolean isDrawdown = locParamsOpt.isPresent();
            Optional<Long> locIdOpt = locParamsOpt.map(LoanLineOfCreditParams::getLineOfCredit).map(LineOfCredit::getId);

            LocStatusAggregationData locStatusAggregationData;
            if (isDrawdown && locIdOpt.isPresent() && lineOfCreditRepository != null && locStatusAggregationUtils != null) {
                Optional<LineOfCredit> locOpt = lineOfCreditRepository.findById(locIdOpt.get());
                if (locOpt.isPresent()) {
                    LineOfCredit loc = locOpt.get();
                    locStatusAggregationData = this.locStatusAggregationUtils.computeLocStatusAggregationData(loc, loan);
                    if (locStatusAggregationData != null) {
                        lineOfCreditRepository.save(loc);
                    }
                } else {
                    locStatusAggregationData = null;
                }
            } else {
                locStatusAggregationData = null;
            }

            final LocStatusAggregationData finalLocStatusAggregationData = locStatusAggregationData;
            final CustomLoanStatus finalOldCustomStatus = oldCustomStatus;
            final boolean finalIsDrawdown = isDrawdown;
            final Optional<Long> finalLocIdOpt = locIdOpt;
            final Loan finalLoanRef = loan;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    transactionTemplate.execute(innerStatus -> {
                        loanStatusWebhookPublisher.publish(finalLoanRef, finalOldCustomStatus, finalIsDrawdown, finalLocIdOpt);
                        if (finalLocStatusAggregationData != null) {
                            lineOfCreditStatusWebhookPublisher.publish(finalLoanRef,
                                    finalLocStatusAggregationData.getDefaultLocStatus().name(),
                                    finalLocStatusAggregationData.getOldLocCustomStatus().name(),
                                    finalLocStatusAggregationData.getNewLocCustomStatus().name(), finalIsDrawdown, finalLocIdOpt);
                        }
                        return null;
                    });
                }
            });
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CustomLoanAccountDomainServiceJpa.class)
                    .warn("Failed to compute/publish loan/LOC status for repayment on loan {}: {}", loan.getId(), e.getMessage());
        }

        // Mark Post Dated Check as paid.
        final Set<LoanTransactionToRepaymentScheduleMapping> loanTransactionToRepaymentScheduleMappings = newRepaymentTransaction
                .getLoanTransactionToRepaymentScheduleMappings();
        if (loanTransactionToRepaymentScheduleMappings != null) {
            for (LoanTransactionToRepaymentScheduleMapping loanTransactionToRepaymentScheduleMapping : loanTransactionToRepaymentScheduleMappings) {
                LoanRepaymentScheduleInstallment loanRepaymentScheduleInstallment = loanTransactionToRepaymentScheduleMapping
                        .getLoanRepaymentScheduleInstallment();
                if (loanRepaymentScheduleInstallment != null) {
                    final boolean isPaid = loanRepaymentScheduleInstallment.isNotFullyPaidOff();
                    PostDatedChecks postDatedChecks = this.postDatedChecksRepository
                            .getPendingPostDatedCheck(loanRepaymentScheduleInstallment);

                    if (postDatedChecks != null) {
                        if (!isPaid) {
                            postDatedChecks.setStatus(PostDatedChecksStatus.POST_DATED_CHECKS_PAID);
                        } else {
                            postDatedChecks.setStatus(PostDatedChecksStatus.POST_DATED_CHECKS_PENDING);
                        }
                        this.postDatedChecksRepository.saveAndFlush(postDatedChecks);
                    } else {
                        break;
                    }
                }
            }
        }

        return newRepaymentTransaction;
    }

    private void closeLoanIfFinalRepaymentResidualIsWithinTolerance(final Loan loan, final LoanTransaction repaymentTransaction) {
        if (!repaymentTransaction.isRepayment() && !repaymentTransaction.isRecoveryRepayment()) {
            return;
        }

        final Money tolerance = loanCloseTolerance(loan);
        if (loan.isOpen()) {
            final Money totalOutstanding = loan.getSummary().getTotalOutstanding(loan.getCurrency());
            if (totalOutstanding.isGreaterThanZero() && tolerance.isGreaterThanOrEqualTo(totalOutstanding)) {
                loan.setClosedOnDate(repaymentTransaction.getTransactionDate());
                loan.setActualMaturityDate(repaymentTransaction.getTransactionDate());
                final var statusEnum = defaultLoanLifecycleStateMachine.dryTransition(LoanEvent.REPAID_IN_FULL, loan);
                if (!statusEnum.hasStateOf(loan.getStatus())) {
                    defaultLoanLifecycleStateMachine.transition(LoanEvent.REPAID_IN_FULL, loan);
                }
            }
        }

        if (loan.isOpen()) {
            loan.doPostLoanTransactionChecks(repaymentTransaction.getTransactionDate(), defaultLoanLifecycleStateMachine);
            LoanChargeSettlementUtils.closeIfFullySettled(loan, repaymentTransaction.getTransactionDate(),
                    defaultLoanLifecycleStateMachine);
        } else if (loan.isOverPaid()) {
            final Money totalLoanOverpayment = loan.calculateTotalOverpayment();
            if (totalLoanOverpayment.isGreaterThanZero() && tolerance.isGreaterThanOrEqualTo(totalLoanOverpayment)) {
                loan.setClosedOnDate(repaymentTransaction.getTransactionDate());
                loan.setActualMaturityDate(repaymentTransaction.getTransactionDate());
                defaultLoanLifecycleStateMachine.transition(LoanEvent.REPAID_IN_FULL, loan);
            }
        }
    }

    private Money loanCloseTolerance(final Loan loan) {
        final Money minimumCloseTolerance = Money.of(loan.getCurrency(), MINIMUM_LOAN_CLOSE_TOLERANCE);
        final Money configuredTolerance = loan.getInArrearsTolerance();
        return configuredTolerance.isGreaterThanOrEqualTo(minimumCloseTolerance) ? configuredTolerance : minimumCloseTolerance;
    }

    /**
     * Calculate total outstanding for multi-disbursement loans based on disbursed amounts only. This considers: -
     * Disbursed principal outstanding (not approved principal) - Interest outstanding - Fee charges outstanding (only
     * from disbursed tranches) - Penalty charges outstanding - Tax charges outstanding (only from disbursed tranches)
     *
     * @param loan
     *            the loan to calculate outstanding for
     * @param currency
     *            the monetary currency
     * @return BigDecimal representing the total outstanding amount
     */
    private BigDecimal calculateMultiDisbursementLoanOutstanding(Loan loan, MonetaryCurrency currency) {
        final LoanSummary summary = loan.getSummary();

        // Calculate disbursed principal outstanding
        final BigDecimal disbursedPrincipal = loan.getDisbursedAmount();
        final BigDecimal principalRepaid = summary.getTotalPrincipalRepaid();
        final BigDecimal principalWrittenOff = summary.getTotalPrincipalWrittenOff();
        final BigDecimal principalAdjustments = summary.getTotalPrincipalAdjustments();
        final BigDecimal disbursedPrincipalOutstanding = disbursedPrincipal.add(principalAdjustments).subtract(principalRepaid)
                .subtract(principalWrittenOff);

        // Calculate other outstanding amounts
        final BigDecimal interestOutstanding = summary.getTotalInterestOutstanding();
        final BigDecimal feeOutstanding = calculateFeeChargesOutstanding(loan, currency);
        final BigDecimal penaltyOutstanding = summary.getTotalPenaltyChargesOutstanding();
        final BigDecimal taxOutstanding = calculateTaxChargesOutstanding(loan, currency);
        return disbursedPrincipalOutstanding.add(interestOutstanding).add(feeOutstanding).add(penaltyOutstanding).add(taxOutstanding);
    }

    private BigDecimal calculateFeeChargesOutstanding(final Loan loan, final MonetaryCurrency currency) {
        Money totalFeeOutstanding = Money.zero(currency);
        for (final LoanCharge charge : loan.getActiveCharges()) {
            if (charge.isPenaltyCharge()) {
                continue; // Skip penalty charges, we only want fee charges
            }
            if (!charge.isDisbursementCharge() && !charge.isTrancheDisbursementCharge()) {
                totalFeeOutstanding = totalFeeOutstanding.plus(charge.getAmountOutstanding(currency));
            }
        }
        return totalFeeOutstanding.getAmount();
    }

    private BigDecimal calculateTaxChargesOutstanding(final Loan loan, final MonetaryCurrency currency) {
        Money totalDisbursedTaxOutstanding = Money.zero(currency);
        for (final LoanCharge charge : loan.getActiveCharges()) {
            if (charge.isPenaltyCharge()) {
                continue; // Skip penalty charges
            }
            if (!charge.hasTax()) {
                continue; // Skip charges without tax
            }
            if (!charge.isDisbursementCharge() && !charge.isTrancheDisbursementCharge()) {
                totalDisbursedTaxOutstanding = totalDisbursedTaxOutstanding.plus(charge.getTaxAmountOutstanding(currency));
            }
        }
        return totalDisbursedTaxOutstanding.getAmount();
    }

}
