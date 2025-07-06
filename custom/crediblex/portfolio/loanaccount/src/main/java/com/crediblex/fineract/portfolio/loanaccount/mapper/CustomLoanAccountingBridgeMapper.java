package com.crediblex.fineract.portfolio.loanaccount.mapper;

import com.crediblex.fineract.portfolio.loanaccount.data.CustomAccountingBridgeDataDTO;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanWrapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeLoanTransactionDTO;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByDTO;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomLoanAccountingBridgeMapper {

    public CustomAccountingBridgeDataDTO deriveAccountingBridgeData(final String currencyCode, final List<Long> existingTransactionIds,
            final List<Long> existingReversedTransactionIds, final boolean isAccountTransfer, final Loan loan) {
        final List<AccountingBridgeLoanTransactionDTO> newLoanTransactions = new ArrayList<>();
        for (final LoanTransaction transaction : loan.getLoanTransactions()) {
            if (transaction.isReversed() && existingTransactionIds.contains(transaction.getId())
                    && !existingReversedTransactionIds.contains(transaction.getId())) {
                newLoanTransactions.add(mapToLoanTransactionData(transaction, currencyCode));
            } else if (!existingTransactionIds.contains(transaction.getId())) {
                newLoanTransactions.add(mapToLoanTransactionData(transaction, currencyCode));
            }
        }

        CustomAccountingBridgeDataDTO dto = new CustomAccountingBridgeDataDTO();
        dto.setNewLoanTransactions(newLoanTransactions);
        dto.setLoanId(loan.getId());
        dto.setLoanProductId(loan.loanProduct().getId());
        dto.setOfficeId(loan.getOfficeId());
        dto.setCurrencyCode(currencyCode);
        dto.setCashBasedAccountingEnabled(loan.isCashBasedAccountingEnabledOnLoanProduct());
        dto.setUpfrontAccrualBasedAccountingEnabled(loan.isUpfrontAccrualAccountingEnabledOnLoanProduct());
        dto.setPeriodicAccrualBasedAccountingEnabled(loan.isPeriodicAccrualAccountingEnabledOnLoanProduct());
        dto.setAccountTransfer(isAccountTransfer);
        dto.setChargeOff(loan.isChargedOff());
        dto.setFraud(loan.isFraud());
        dto.setChargeOffReasonCodeValue(loan.getChargeOffReason() != null ? loan.getChargeOffReason().getId() : null);

        // Calculate breakdowns
        LoanWrapper loanWrapper = new LoanWrapper(loan);
        BigDecimal principal = loan.getPrincipal().getAmount();
        BigDecimal fees = loanWrapper.deriveTotalFeeChargesDueAtDisbursement();
        BigDecimal vat = loanWrapper.deriveTotalVATChargesDueAtDisbursement(); // Assume this method exists or implement
                                                                               // accordingly
        BigDecimal netDisbursal = loan.getNetDisbursalAmount();

        dto.setPrincipalPortion(principal);
        dto.setFeesPortion(fees);
        dto.setVatPortion(vat);
        dto.setNetDisbursalAmount(netDisbursal);

        return dto;
    }

    public AccountingBridgeLoanTransactionDTO mapToLoanTransactionData(final LoanTransaction transaction, final String currencyCode) {
        final AccountingBridgeLoanTransactionDTO transactionDTO = new AccountingBridgeLoanTransactionDTO();

        transactionDTO.setId(transaction.getId());
        transactionDTO.setOfficeId(transaction.getOffice().getId());
        transactionDTO.setType(LoanEnumerations.transactionType(transaction.getTypeOf()));
        transactionDTO.setReversed(transaction.isReversed());
        transactionDTO.setDate(transaction.getTransactionDate());
        transactionDTO.setCurrencyCode(currencyCode);
        transactionDTO.setAmount(transaction.getAmount());
        transactionDTO.setNetDisbursalAmount(transaction.getLoan().getNetDisbursalAmount());

        if (transactionDTO.getType().isChargeback() && (transaction.getLoan().getCreditAllocationRules() == null
                || transaction.getLoan().getCreditAllocationRules().isEmpty())) {
            transactionDTO.setPrincipalPortion(transaction.getAmount());
        } else {
            transactionDTO.setPrincipalPortion(transaction.getPrincipalPortion());
        }

        transactionDTO.setInterestPortion(transaction.getInterestPortion());
        transactionDTO.setFeeChargesPortion(transaction.getFeeChargesPortion());
        transactionDTO.setPenaltyChargesPortion(transaction.getPenaltyChargesPortion());
        transactionDTO.setOverPaymentPortion(transaction.getOverPaymentPortion());

        if (transactionDTO.getType().isChargeRefund()) {
            transactionDTO.setChargeRefundChargeType(transaction.getChargeRefundChargeType());
        }

        if (transaction.getPaymentDetail() != null) {
            transactionDTO.setPaymentTypeId(transaction.getPaymentDetail().getPaymentType().getId());
        }

        if (!transaction.getLoanChargesPaid().isEmpty()) {
            List<LoanChargePaidByDTO> loanChargesPaidData = new ArrayList<>();
            for (final LoanChargePaidBy chargePaidBy : transaction.getLoanChargesPaid()) {
                final LoanChargePaidByDTO loanChargePaidData = new LoanChargePaidByDTO();
                loanChargePaidData.setChargeId(chargePaidBy.getLoanCharge().getCharge().getId());
                loanChargePaidData.setIsPenalty(chargePaidBy.getLoanCharge().isPenaltyCharge());
                loanChargePaidData.setLoanChargeId(chargePaidBy.getLoanCharge().getId());
                loanChargePaidData.setAmount(chargePaidBy.getAmount());
                loanChargePaidData.setInstallmentNumber(chargePaidBy.getInstallmentNumber());

                loanChargesPaidData.add(loanChargePaidData);
            }
            transactionDTO.setLoanChargesPaid(loanChargesPaidData);
        }

        if (transactionDTO.getType().isChargeback() && transaction.getOverPaymentPortion() != null
                && transaction.getOverPaymentPortion().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal principalPaid = transaction.getOverPaymentPortion();
            BigDecimal feePaid = BigDecimal.ZERO;
            BigDecimal penaltyPaid = BigDecimal.ZERO;
            if (!transaction.getLoanTransactionToRepaymentScheduleMappings().isEmpty()) {
                principalPaid = transaction.getLoanTransactionToRepaymentScheduleMappings().stream()
                        .map(mapping -> Optional.ofNullable(mapping.getPrincipalPortion()).orElse(BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                feePaid = transaction.getLoanTransactionToRepaymentScheduleMappings().stream()
                        .map(mapping -> Optional.ofNullable(mapping.getFeeChargesPortion()).orElse(BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                penaltyPaid = transaction.getLoanTransactionToRepaymentScheduleMappings().stream()
                        .map(mapping -> Optional.ofNullable(mapping.getPenaltyChargesPortion()).orElse(BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            transactionDTO.setPrincipalPaid(principalPaid);
            transactionDTO.setFeePaid(feePaid);
            transactionDTO.setPenaltyPaid(penaltyPaid);
        }

        LoanTransactionRelation loanTransactionRelation = transaction.getLoanTransactionRelations().stream()
                .filter(e -> LoanTransactionRelationTypeEnum.CHARGE_ADJUSTMENT.equals(e.getRelationType())).findAny().orElse(null);
        if (loanTransactionRelation != null) {
            LoanCharge loanCharge = loanTransactionRelation.getToCharge();
            transactionDTO.setLoanChargeData(loanCharge.toData());
        }

        transactionDTO.setLoanToLoanTransfer(false);

        return transactionDTO;
    }

    public List<CustomAccountingBridgeDataDTO> deriveAccountingBridgeDataForChargeOff(final String currencyCode,
            final List<Long> existingTransactionIds, final List<Long> existingReversedTransactionIds, final boolean isAccountTransfer,
            final Loan loan) {
        final List<AccountingBridgeLoanTransactionDTO> newLoanTransactionsBeforeChargeOff = new ArrayList<>();
        final List<AccountingBridgeLoanTransactionDTO> newLoanTransactionsAfterChargeOff = new ArrayList<>();

        // split the transactions according charge-off date
        classifyTransactionsBasedOnChargeOffDate(newLoanTransactionsBeforeChargeOff, newLoanTransactionsAfterChargeOff,
                existingTransactionIds, existingReversedTransactionIds, currencyCode, loan);

        LoanWrapper loanWrapper = new LoanWrapper(loan);
        BigDecimal principal = loan.getPrincipal().getAmount();
        BigDecimal fees = loanWrapper.deriveTotalFeeChargesDueAtDisbursement();
        BigDecimal vat = loanWrapper.deriveTotalVATChargesDueAtDisbursement();
        BigDecimal netDisbursal = loan.getNetDisbursalAmount();

        CustomAccountingBridgeDataDTO beforeChargeOff = new CustomAccountingBridgeDataDTO(loan.getId(), loan.productId(),
                loan.getOfficeId(), currencyCode, loan.getSummary().getTotalInterestCharged(),
                loan.isCashBasedAccountingEnabledOnLoanProduct(), loan.isUpfrontAccrualAccountingEnabledOnLoanProduct(),
                loan.isPeriodicAccrualAccountingEnabledOnLoanProduct(), isAccountTransfer, false, loan.isFraud(),
                loan.fetchChargeOffReasonId(), newLoanTransactionsBeforeChargeOff, principal, fees, vat, netDisbursal);

        CustomAccountingBridgeDataDTO afterChargeOff = new CustomAccountingBridgeDataDTO(loan.getId(), loan.productId(), loan.getOfficeId(),
                currencyCode, loan.getSummary().getTotalInterestCharged(), loan.isCashBasedAccountingEnabledOnLoanProduct(),
                loan.isUpfrontAccrualAccountingEnabledOnLoanProduct(), loan.isPeriodicAccrualAccountingEnabledOnLoanProduct(),
                isAccountTransfer, true, loan.isFraud(), loan.fetchChargeOffReasonId(), newLoanTransactionsAfterChargeOff, principal, fees,
                vat, netDisbursal);

        List<CustomAccountingBridgeDataDTO> result = new ArrayList<>();
        result.add(beforeChargeOff);
        result.add(afterChargeOff);
        return result;
    }

    private void classifyTransactionsBasedOnChargeOffDate(final List<AccountingBridgeLoanTransactionDTO> newLoanTransactionsBeforeChargeOff,
            final List<AccountingBridgeLoanTransactionDTO> newLoanTransactionsAfterChargeOff, final List<Long> existingTransactionIds,
            final List<Long> existingReversedTransactionIds, final String currencyCode, final Loan loan) {
        // Before
        filterTransactionsByChargeOffDate(newLoanTransactionsBeforeChargeOff, currencyCode, existingTransactionIds,
                existingReversedTransactionIds,
                transaction -> DateUtils.isBefore(transaction.getTransactionDate(), loan.getChargedOffOnDate()), loan);
        // On
        filterTransactionsByChargeOffDate(newLoanTransactionsBeforeChargeOff, newLoanTransactionsAfterChargeOff, currencyCode,
                existingTransactionIds, existingReversedTransactionIds,
                transaction -> DateUtils.isEqual(transaction.getTransactionDate(), loan.getChargedOffOnDate()), loan);
        // After
        filterTransactionsByChargeOffDate(newLoanTransactionsAfterChargeOff, currencyCode, existingTransactionIds,
                existingReversedTransactionIds,
                transaction -> DateUtils.isAfter(transaction.getTransactionDate(), loan.getChargedOffOnDate()), loan);
    }

    private void filterTransactionsByChargeOffDate(final List<AccountingBridgeLoanTransactionDTO> filteredTransactions,
            final String currencyCode, final List<Long> existingTransactionIds, final List<Long> existingReversedTransactionIds,
            final Predicate<LoanTransaction> chargeOffDateCriteria, final Loan loan) {
        filteredTransactions.addAll(loan.getLoanTransactions().stream() //
                .filter(chargeOffDateCriteria) //
                .filter(transaction -> {
                    boolean isExistingTransaction = existingTransactionIds.contains(transaction.getId());
                    boolean isExistingReversedTransaction = existingReversedTransactionIds.contains(transaction.getId());

                    if (transaction.isReversed() && isExistingTransaction && !isExistingReversedTransaction) {
                        return true;
                    } else {
                        return !isExistingTransaction;
                    }
                }) //
                .map(transaction -> mapToLoanTransactionData(transaction, currencyCode)).toList());
    }

    private void filterTransactionsByChargeOffDate(final List<AccountingBridgeLoanTransactionDTO> newLoanTransactionsBeforeChargeOff,
            final List<AccountingBridgeLoanTransactionDTO> newLoanTransactionsAfterChargeOff, final String currencyCode,
            final List<Long> existingTransactionIds, final List<Long> existingReversedTransactionIds,
            final Predicate<LoanTransaction> chargeOffDateCriteria, final Loan loan) {
        final Optional<LoanTransaction> chargeOffTransactionOptional = loan.getLoanTransactions().stream() //
                .filter(LoanTransaction::isChargeOff) //
                .filter(LoanTransaction::isNotReversed) //
                .findFirst();

        if (chargeOffTransactionOptional.isEmpty()) {
            return;
        }

        final LoanTransaction chargeOffTransaction = chargeOffTransactionOptional.get();
        final LoanTransaction originalChargeOffTransaction = getOriginalTransactionIfReverseReplayed(chargeOffTransaction);

        loan.getLoanTransactions().stream().filter(chargeOffDateCriteria).forEach(transaction -> {
            boolean isExistingTransaction = existingTransactionIds.contains(transaction.getId());
            boolean isExistingReversedTransaction = existingReversedTransactionIds.contains(transaction.getId());
            List<AccountingBridgeLoanTransactionDTO> targetList = null;
            if ((transaction.isReversed() && isExistingTransaction && !isExistingReversedTransaction)) {
                // reversed transactions
                LoanTransaction originalTransaction = getOriginalTransactionIfReverseReplayed(transaction);
                targetList = originalTransaction.happenedBefore(originalChargeOffTransaction) ? newLoanTransactionsBeforeChargeOff
                        : newLoanTransactionsAfterChargeOff;

            } else if (!isExistingTransaction) {
                // new and replayed transactions
                targetList = transaction.happenedBefore(chargeOffTransaction) ? newLoanTransactionsBeforeChargeOff
                        : newLoanTransactionsAfterChargeOff;
            }
            if (targetList != null) {
                targetList.add(mapToLoanTransactionData(transaction, currencyCode));
            }
        });
    }

    private LoanTransaction getOriginalTransactionIfReverseReplayed(final LoanTransaction loanTransaction) {
        if (!loanTransaction.getLoanTransactionRelations().isEmpty()) {
            return loanTransaction.getLoanTransactionRelations().stream()
                    .filter(tr -> LoanTransactionRelationTypeEnum.REPLAYED.equals(tr.getRelationType()))
                    .map(LoanTransactionRelation::getToTransaction).toList().stream().min(Comparator.comparingLong(LoanTransaction::getId))
                    .orElse(loanTransaction);
        }
        return loanTransaction;
    }

}
