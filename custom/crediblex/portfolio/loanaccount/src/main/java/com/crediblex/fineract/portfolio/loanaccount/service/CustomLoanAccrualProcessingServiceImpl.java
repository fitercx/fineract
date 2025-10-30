package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.data.CustomAccountingBridgeDataDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.misc.NotNull;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.config.TaskExecutorConstant;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.core.service.MathUtil;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanAccrualAdjustmentTransactionBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanAccrualTransactionCreatedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.domain.loan.transaction.LoanTransactionBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeLoanTransactionDTO;
import org.apache.fineract.portfolio.loanaccount.data.AccrualPeriodData;
import org.apache.fineract.portfolio.loanaccount.data.AccrualPeriodsData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanInterestRecalculationDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleGeneratorFactory;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualTransactionBusinessEventService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Primary
public class CustomLoanAccrualProcessingServiceImpl extends LoanAccrualsProcessingServiceImpl {

    public CustomLoanAccrualProcessingServiceImpl(ExternalIdFactory externalIdFactory,
            BusinessEventNotifierService businessEventNotifierService, ConfigurationDomainService configurationDomainService,
            LoanRepositoryWrapper loanRepositoryWrapper,
            LoanAccrualTransactionBusinessEventService loanAccrualTransactionBusinessEventService,
            JournalEntryWritePlatformService journalEntryWritePlatformService, LoanTransactionRepository loanTransactionRepository,
            LoanScheduleGeneratorFactory loanScheduleFactory,
            @Qualifier(TaskExecutorConstant.CONFIGURABLE_TASK_EXECUTOR_BEAN_NAME) ThreadPoolTaskExecutor taskExecutor,
            TransactionTemplate transactionTemplate, LoanAccountingBridgeMapper loanAccountingBridgeMapper) {
        super(externalIdFactory, businessEventNotifierService, configurationDomainService, loanRepositoryWrapper,
                loanAccrualTransactionBusinessEventService, journalEntryWritePlatformService, loanTransactionRepository,
                loanScheduleFactory, taskExecutor, transactionTemplate, loanAccountingBridgeMapper);
    }

    @Override
    protected void addAccruals(@NotNull final Loan loan, @NotNull LocalDate tillDate, final boolean periodic, final boolean isFinal,
            final boolean addJournal, final boolean chargeOnDueDate) {
        if ((!isFinal && !loan.isOpen()) || loan.isNpa() || loan.isChargedOff()
                || !loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()) {
            return;
        }

        final LoanInterestRecalculationDetails recalculationDetails = loan.getLoanInterestRecalculationDetails();
        if (recalculationDetails != null && recalculationDetails.isCompoundingToBePostedAsTransaction()) {
            return;
        }
        final List<LoanTransaction> existingAccruals = retrieveListOfAccrualTransactions(loan);
        final LocalDate lastDueDate = loan.getLastLoanRepaymentScheduleInstallment().getDueDate();
        reverseTransactionsAfter(existingAccruals, lastDueDate, addJournal);
        ensureAccrualTransactionMappings(loan, existingAccruals, chargeOnDueDate);
        if (DateUtils.isAfter(tillDate, lastDueDate)) {
            tillDate = lastDueDate;
        }

        final boolean progressiveAccrual = isProgressiveAccrual(loan);
        final LocalDate accruedTill = loan.getAccruedTill();
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final LocalDate accrualDate = isFinal
                ? (progressiveAccrual ? (DateUtils.isBefore(lastDueDate, businessDate) ? lastDueDate : businessDate)
                        : getFinalAccrualTransactionDate(loan))
                : tillDate;
        if (progressiveAccrual && accruedTill != null && !DateUtils.isAfter(tillDate, accruedTill)) {
            if (isFinal) {
                reverseTransactionsAfter(existingAccruals, accrualDate, addJournal);
            } else if (existingAccruals.stream().anyMatch(t -> !t.isReversed() && !DateUtils.isBefore(t.getDateOf(), accrualDate))) {
                return;
            }
        }

        final AccrualPeriodsData accrualPeriods = calculateAccrualAmounts(loan, tillDate, periodic, isFinal, chargeOnDueDate);
        final boolean mergeTransactions = isFinal || progressiveAccrual;
        final MonetaryCurrency currency = loan.getLoanProductRelatedDetail().getCurrency();
        List<LoanTransaction> accrualTransactions = new ArrayList<>();
        Money totalInterestPortion = null;
        LoanTransaction mergeAccrualTransaction = null;
        LoanTransaction mergeAdjustTransaction = null;
        for (AccrualPeriodData period : accrualPeriods.getPeriods()) {
            final Money interestAccruable = MathUtil.nullToZero(period.getInterestAccruable(), currency);
            final Money interestPortion = MathUtil.minus(interestAccruable, period.getInterestAccrued());
            final Money feeAccruable = MathUtil.nullToZero(period.getFeeAccruable(), currency);
            final Money feePortion = MathUtil.minus(feeAccruable, period.getFeeAccrued());
            final Money penaltyAccruable = MathUtil.nullToZero(period.getPenaltyAccruable(), currency);
            final Money penaltyPortion = MathUtil.minus(penaltyAccruable, period.getPenaltyAccrued());
            if (MathUtil.isEmpty(interestPortion) && MathUtil.isEmpty(feePortion) && MathUtil.isEmpty(penaltyPortion)) {
                continue;
            }
            if (mergeTransactions) {
                totalInterestPortion = MathUtil.plus(totalInterestPortion, interestPortion);
                if (progressiveAccrual) {
                    final Money feeAdjustmentPortion = MathUtil.negate(feePortion);
                    final Money penaltyAdjustmentPortion = MathUtil.negate(penaltyPortion);
                    mergeAdjustTransaction = createOrMergeAccrualTransaction(loan, mergeAdjustTransaction, accrualDate, period,
                            accrualTransactions, null, feeAdjustmentPortion, penaltyAdjustmentPortion, null, true);
                }
                mergeAccrualTransaction = createOrMergeAccrualTransaction(loan, mergeAccrualTransaction, accrualDate, period,
                        accrualTransactions, null, feePortion, penaltyPortion, null, false);
            } else {
                final LocalDate dueDate = period.getDueDate();
                if (!isFinal && DateUtils.isAfter(dueDate, tillDate) && DateUtils.isBefore(tillDate, accruedTill)) {
                    continue;
                }
                final LocalDate periodAccrualDate = DateUtils.isBefore(dueDate, accrualDate) ? dueDate : accrualDate;
                final LoanTransaction accrualTransaction = addAccrualTransaction(loan, periodAccrualDate, period, interestPortion,
                        feePortion, penaltyPortion, null, false);
                if (accrualTransaction != null) {
                    accrualTransactions.add(accrualTransaction);
                }
            }
            final LoanRepaymentScheduleInstallment installment = loan.fetchRepaymentScheduleInstallment(period.getInstallmentNumber());
            installment.updateAccrualPortion(interestAccruable, feeAccruable, penaltyAccruable);
        }
        if (mergeTransactions && !MathUtil.isEmpty(totalInterestPortion)) {
            if (progressiveAccrual) {
                final Money interestAdjustmentPortion = MathUtil.negate(totalInterestPortion);
                createOrMergeAccrualTransaction(loan, mergeAdjustTransaction, accrualDate, null, accrualTransactions,
                        interestAdjustmentPortion, null, null, null, true);
            }
            createOrMergeAccrualTransaction(loan, mergeAccrualTransaction, accrualDate, null, accrualTransactions, totalInterestPortion,
                    null, null, null, false);
        }
        if (accrualTransactions.isEmpty()) {
            return;
        }

        if (!isFinal || progressiveAccrual) {
            loan.setAccruedTill(isFinal ? accrualDate : tillDate);
        }

        accrualTransactions = loanTransactionRepository.saveAll(accrualTransactions);
        loanTransactionRepository.flush();

        if (addJournal) {
            final List<AccountingBridgeLoanTransactionDTO> newTransactionDTOs = new ArrayList<>();
            for (LoanTransaction accrualTransaction : accrualTransactions) {
                final LoanTransactionBusinessEvent businessEvent = accrualTransaction.isAccrual()
                        ? new LoanAccrualTransactionCreatedBusinessEvent(accrualTransaction)
                        : new LoanAccrualAdjustmentTransactionBusinessEvent(accrualTransaction);
                businessEventNotifierService.notifyPostBusinessEvent(businessEvent);
                final AccountingBridgeLoanTransactionDTO transactionDTO = loanAccountingBridgeMapper
                        .mapToLoanTransactionData(accrualTransaction, currency.getCode());
                newTransactionDTOs.add(transactionDTO);
            }
            final CustomAccountingBridgeDataDTO accountingBridgeData = new CustomAccountingBridgeDataDTO(loan.getId(),
                    loan.getLoanProduct().getId(), loan.getOfficeId(), loan.getCurrencyCode(), loan.getSummary().getTotalInterestCharged(),
                    loan.isNoneOrCashOrUpfrontAccrualAccountingEnabledOnLoanProduct(),
                    loan.isUpfrontAccrualAccountingEnabledOnLoanProduct(), loan.isPeriodicAccrualAccountingEnabledOnLoanProduct(), false,
                    false, false, null, newTransactionDTOs, null);

            // Populate LOC receivable flags for proper accounting treatment
            boolean isLocReceivable = loan.getLoanProduct().isEnableLocReceivable()
                    && loan.isPeriodicAccrualAccountingEnabledOnLoanProduct();
            if (isLocReceivable) {
                accountingBridgeData.setLocReceivable(true);
                accountingBridgeData.setTotalContractualInterest(
                        loan.getSummary().getTotalInterestCharged() != null ? loan.getSummary().getTotalInterestCharged()
                                : BigDecimal.ZERO);
                // Note: For accruals, we don't need fee amounts as fees are not accrued for LOC receivable
                accountingBridgeData.setTotalDisbursementFees(BigDecimal.ZERO);
                accountingBridgeData.setTotalDisbursementFeesTax(BigDecimal.ZERO);
            }

            this.journalEntryWritePlatformService.createJournalEntriesForLoan(accountingBridgeData);
        }
    }
}
