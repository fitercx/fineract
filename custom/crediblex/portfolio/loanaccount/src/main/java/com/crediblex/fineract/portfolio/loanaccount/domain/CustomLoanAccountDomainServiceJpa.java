package com.crediblex.fineract.portfolio.loanaccount.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanBalanceChangedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanChargePaymentPostBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanChargePaymentPreBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanForeClosurePostBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanForeClosurePreBusinessEvent;
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
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainServiceJpa;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCollateralManagementRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
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
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecksRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class CustomLoanAccountDomainServiceJpa extends LoanAccountDomainServiceJpa {

    private final AccountAssociationsRepository accountAssociationsRepository;
    private final AccountTransfersWritePlatformService accountTransfersWritePlatformService;

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
            @Lazy AccountTransfersWritePlatformService accountTransfersWritePlatformService) {
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
        final LoanRepaymentScheduleInstallment foreCloseDetail = loan.fetchLoanForeclosureDetail(foreClosureDate);

        loanAccrualsProcessingService.processAccrualsOnLoanForeClosure(loan, foreClosureDate, newTransactions);

        Money interestPayable = foreCloseDetail.getInterestCharged(currency);
        Money feePayable = foreCloseDetail.getFeeChargesCharged(currency);
        Money penaltyPayable = foreCloseDetail.getPenaltyChargesCharged(currency);
        Money taxPayable = foreCloseDetail.getTaxChargesCharged(currency);
        Money payPrincipal = foreCloseDetail.getPrincipal(currency);

        // For Factor Rate loans, fees/taxes might not be properly reflected in individual installments
        // Update each independently from loan summary if installment amount is zero but loan summary has outstanding amounts
        // Only access loanSummary if at least one needs updating (Factor Rate loan with zero fees or taxes)
        if (loan.isFactorRateEnabled() && (feePayable.isZero() || taxPayable.isZero())) {
            final LoanSummary loanSummary = loan.getSummary();
            if (feePayable.isZero()) {
                Money feeOutstanding = Money.of(currency, loanSummary.getTotalFeeChargesOutstanding());
                if (feeOutstanding.isGreaterThanZero()) {
                    feePayable = feeOutstanding;
                }
            }
            if (taxPayable.isZero()) {
                Money taxOutstanding = Money.of(currency, loanSummary.getTotalTaxChargesOutstanding());
                if (taxOutstanding.isGreaterThanZero()) {
                    taxPayable = taxOutstanding;
                }
            }
        }
        updateInstallmentsPostDate(loan, foreClosureDate);

        LoanTransaction payment = null;
        List<Long> transactionIds = new ArrayList<>();

        loanDownPaymentTransactionValidator.validateAccountStatus(loan, LoanEvent.LOAN_FORECLOSURE);

        loanForeclosureValidator.validateForForeclosure(loan, foreClosureDate);

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
            newTransactions.add(payment);

        } else {

            if (payPrincipal.plus(interestPayable).plus(feePayable).plus(penaltyPayable).plus(taxPayable).isGreaterThanZero()) {
                final PaymentDetail paymentDetail = null;
                payment = LoanTransaction.repayment(loan.getOffice(),
                        payPrincipal.plus(interestPayable).plus(feePayable).plus(penaltyPayable).plus(taxPayable), paymentDetail,
                        foreClosureDate, externalId);
                payment.updateLoan(loan);
                newTransactions.add(payment);
            }

            handleForeClosureTransactions(loan, payment, defaultLoanLifecycleStateMachine, scheduleGeneratorDTO);
        }

        if (loan.isReceivableLocLoan()) {
            loan.getLoanRepaymentScheduleDetail().setPrincipal(totalPrincipalBeforeForClosure.getAmount());
        }

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

        loan = loanAccountService.saveAndFlushLoanWithDataIntegrityViolationChecks(loan);

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

}
