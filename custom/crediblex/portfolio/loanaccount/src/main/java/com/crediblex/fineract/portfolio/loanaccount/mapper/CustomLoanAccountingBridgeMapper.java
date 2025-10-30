package com.crediblex.fineract.portfolio.loanaccount.mapper;

import com.crediblex.fineract.portfolio.loanaccount.data.CustomAccountingBridgeDataDTO;
import com.crediblex.fineract.portfolio.loanaccount.data.CustomLoanChargePaidByDTO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeDataDTO;
import org.apache.fineract.portfolio.loanaccount.data.AccountingBridgeLoanTransactionDTO;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargePaidByDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRelation;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRelationTypeEnum;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanAccountingBridgeMapper;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.apache.fineract.portfolio.tax.domain.TaxComponent;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CustomLoanAccountingBridgeMapper extends LoanAccountingBridgeMapper {

    @Override
    public List<AccountingBridgeDataDTO> deriveAccountingBridgeDataForChargeOff(final String currencyCode,
            final List<Long> existingTransactionIds, final List<Long> existingReversedTransactionIds, final boolean isAccountTransfer,
            final Loan loan) {
        final List<AccountingBridgeLoanTransactionDTO> newLoanTransactionsBeforeChargeOff = new ArrayList<>();
        final List<AccountingBridgeLoanTransactionDTO> newLoanTransactionsAfterChargeOff = new ArrayList<>();

        // split the transactions according charge-off date
        classifyTransactionsBasedOnChargeOffDate(newLoanTransactionsBeforeChargeOff, newLoanTransactionsAfterChargeOff,
                existingTransactionIds, existingReversedTransactionIds, currencyCode, loan);

        CustomAccountingBridgeDataDTO beforeChargeOff = new CustomAccountingBridgeDataDTO(loan.getId(), loan.productId(),
                loan.getOfficeId(), currencyCode, loan.getSummary().getTotalInterestCharged(),
                loan.isCashBasedAccountingEnabledOnLoanProduct(), loan.isUpfrontAccrualAccountingEnabledOnLoanProduct(),
                loan.isPeriodicAccrualAccountingEnabledOnLoanProduct(), isAccountTransfer, false, loan.isFraud(),
                loan.fetchChargeOffReasonId(), newLoanTransactionsBeforeChargeOff, null);

        CustomAccountingBridgeDataDTO afterChargeOff = new CustomAccountingBridgeDataDTO(loan.getId(), loan.productId(), loan.getOfficeId(),
                currencyCode, loan.getSummary().getTotalInterestCharged(), loan.isCashBasedAccountingEnabledOnLoanProduct(),
                loan.isUpfrontAccrualAccountingEnabledOnLoanProduct(), loan.isPeriodicAccrualAccountingEnabledOnLoanProduct(),
                isAccountTransfer, true, loan.isFraud(), loan.fetchChargeOffReasonId(), newLoanTransactionsAfterChargeOff, null);

        List<AccountingBridgeDataDTO> result = new ArrayList<>();
        result.add(beforeChargeOff);
        result.add(afterChargeOff);
        return result;
    }

    @Override
    public AccountingBridgeDataDTO deriveAccountingBridgeData(final String currencyCode, final List<Long> existingTransactionIds,
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

        BigDecimal netAmountForLiabilityTransfer = ((loan.getNetDisbursalAmount() != null)
                && (loan.getNetDisbursalAmount().compareTo(BigDecimal.ZERO) < 0))
                        ? loan.getApprovedPrincipal().add(loan.getNetDisbursalAmount())
                        : loan.getApprovedPrincipal();

        CustomAccountingBridgeDataDTO accountingData = new CustomAccountingBridgeDataDTO(loan.getId(), loan.productId(), loan.getOfficeId(),
                currencyCode, loan.getSummary().getTotalInterestCharged(), loan.isCashBasedAccountingEnabledOnLoanProduct(),
                loan.isUpfrontAccrualAccountingEnabledOnLoanProduct(), loan.isPeriodicAccrualAccountingEnabledOnLoanProduct(),
                isAccountTransfer, loan.isChargedOff(), loan.isFraud(), loan.fetchChargeOffReasonId(), newLoanTransactions,
                netAmountForLiabilityTransfer);

        // Populate LOC receivable specific fields for upfront accrual accounting
        boolean isLocReceivable = loan.getLoanProduct().isEnableLocReceivable() && loan.isPeriodicAccrualAccountingEnabledOnLoanProduct();

        if (isLocReceivable) {
            accountingData.setLocReceivable(true);

            // Calculate total contractual interest from loan schedule installments (not summary)
            // At disbursement time, loan.getSummary().getTotalInterestCharged() may be 0
            BigDecimal totalContractualInterest = BigDecimal.ZERO;
            if (loan.getRepaymentScheduleInstallments() != null) {
                for (var installment : loan.getRepaymentScheduleInstallments()) {
                    Money interestCharged = installment.getInterestCharged(loan.getCurrency());
                    if (interestCharged != null) {
                        totalContractualInterest = totalContractualInterest.add(interestCharged.getAmount());
                    }
                }
            }
            accountingData.setTotalContractualInterest(totalContractualInterest);

            // Calculate total disbursement fees (net + tax)
            BigDecimal totalDisbursementFees = BigDecimal.ZERO;
            BigDecimal totalDisbursementFeesTax = BigDecimal.ZERO;

            for (LoanCharge charge : loan.getCharges()) {
                if ((charge.isDisbursementCharge() || charge.isInstalmentFee()) && !charge.isWaived()) {
                    BigDecimal chargeAmount = charge.getAmount();
                    if (chargeAmount != null) {
                        totalDisbursementFees = totalDisbursementFees.add(chargeAmount);

                        // Add tax amount if charge has tax
                        if (charge.hasTax() && charge.getTaxAmount() != null) {
                            BigDecimal taxAmount = Money.of(loan.getCurrency(), charge.getTaxAmount()).getAmount();
                            totalDisbursementFees = totalDisbursementFees.add(taxAmount);
                            totalDisbursementFeesTax = totalDisbursementFeesTax.add(taxAmount);
                        }
                    }
                }
            }

            accountingData.setTotalDisbursementFees(totalDisbursementFees);
            accountingData.setTotalDisbursementFeesTax(totalDisbursementFeesTax);

            // Calculate total accrued interest from non-reversed accrual transactions
            BigDecimal totalAccruedInterest = BigDecimal.ZERO;
            for (LoanTransaction transaction : loan.getLoanTransactions()) {
                if (transaction.isAccrual() && !transaction.isReversed()) {
                    BigDecimal interestPortion = transaction.getInterestPortion();
                    if (interestPortion != null) {
                        totalAccruedInterest = totalAccruedInterest.add(interestPortion);
                    }
                }
            }
            accountingData.setTotalAccruedInterest(totalAccruedInterest);

            // Calculate total interest charged from loan schedule installments
            BigDecimal totalInterestCharged = BigDecimal.ZERO;
            if (loan.getRepaymentScheduleInstallments() != null) {
                for (var installment : loan.getRepaymentScheduleInstallments()) {
                    Money interestCharged = installment.getInterestCharged(loan.getCurrency());
                    if (interestCharged != null) {
                        totalInterestCharged = totalInterestCharged.add(interestCharged.getAmount());
                    }
                }
            }
            accountingData.setTotalInterestCharged(totalInterestCharged);
        }

        return accountingData;
    }

    @Override
    public AccountingBridgeLoanTransactionDTO mapToLoanTransactionData(final LoanTransaction transaction, final String currencyCode) {
        final AccountingBridgeLoanTransactionDTO transactionDTO = new AccountingBridgeLoanTransactionDTO();
        final boolean isFactorRateEnabled = transaction.getLoan().isFactorRateEnabled();
        transactionDTO.setFactorRateEnabled(isFactorRateEnabled);

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
        transactionDTO.setTaxChargesPortion(transaction.getTaxChargesPortion());
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
                final CustomLoanChargePaidByDTO loanChargePaidData = new CustomLoanChargePaidByDTO();
                final LoanCharge loanCharge = chargePaidBy.getLoanCharge();

                loanChargePaidData.setChargeId(loanCharge.getCharge().getId());
                loanChargePaidData.setIsPenalty(loanCharge.isPenaltyCharge());
                loanChargePaidData.setLoanChargeId(loanCharge.getId());
                loanChargePaidData.setAmount(chargePaidBy.getAmount());
                loanChargePaidData.setInstallmentNumber(chargePaidBy.getInstallmentNumber());

                if (loanCharge.hasTax() && loanCharge.isDisbursementCharge()) {
                    // For now only covering this because we are sure all taxes have been paid
                    // We need to have transactions adjusted to actually give breakdown of tax by component
                    loanChargePaidData.setTaxGroupId(loanCharge.getCharge().getTaxGroup().getId());
                    loanChargePaidData.setTaxGroupName(loanCharge.getCharge().getTaxGroup().getName());

                    loanCharge.getCharge().getTaxGroup().getTaxGroupMappings().stream().findFirst().ifPresent(mapping -> {
                        loanChargePaidData.setTaxGLAccountId(
                                mapping.getTaxComponent().getDebitAccount() != null ? mapping.getTaxComponent().getDebitAccount().getId()
                                        : null);
                        loanChargePaidData.setIncomeGLAccountId(
                                mapping.getTaxComponent().getCreditAccount() != null ? mapping.getTaxComponent().getCreditAccount().getId()
                                        : null);
                    });

                    loanChargePaidData.setBaseAmount(loanCharge.getAmount());
                    loanChargePaidData.setTaxAmount(Money.of(loanCharge.getLoan().getCurrency(), loanCharge.getTaxAmount()).getAmount());
                } else if (loanCharge.hasTax() && loanCharge.isInstalmentFee() && isFactorRateEnabled) {
                    final BigDecimal taxAmount = chargePaidBy.getTaxAmount();
                    if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                        final TaxGroupMappings taxGroupMappings = loanCharge.getCharge().getTaxGroup().getTaxGroupMappings().stream()
                                .findFirst()
                                .orElseThrow(() -> new GeneralPlatformDomainRuleException(
                                        "error.msg.loan.charge.paid.by.tax.group.mapping.not.found",
                                        "Tax group mapping not found for tax group: " + loanCharge.getCharge().getTaxGroup().getName()));
                        final TaxComponent taxComponent = taxGroupMappings.getTaxComponent();
                        final Long creditGLAccountId = taxComponent.getCreditAccount().getId();
                        final Long debitGLAccountId = taxComponent.getDebitAccount().getId();
                        loanChargePaidData.markAsApplicableToFactoRateFeeTaxes();
                        loanChargePaidData.setCreditGLAccountId(creditGLAccountId);
                        loanChargePaidData.setDebitGLAccountId(debitGLAccountId);
                        loanChargePaidData.setTaxAmount(taxAmount);
                        loanChargePaidData.setTaxGroupId(loanCharge.getCharge().getTaxGroup().getId());
                        loanChargePaidData.setTaxGroupName(loanCharge.getCharge().getTaxGroup().getName());
                    }
                } else if (loanCharge.hasTax() && loanCharge.isSpecifiedDueDate()) {
                    final BigDecimal taxAmount = chargePaidBy.getTaxAmount();
                    if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                        final TaxGroupMappings taxGroupMappings = loanCharge.getCharge().getTaxGroup().getTaxGroupMappings().stream()
                                .findFirst()
                                .orElseThrow(() -> new GeneralPlatformDomainRuleException(
                                        "error.msg.loan.charge.paid.by.tax.group.mapping.not.found",
                                        "Tax group mapping not found for tax group: " + loanCharge.getCharge().getTaxGroup().getName()));
                        final TaxComponent taxComponent = taxGroupMappings.getTaxComponent();
                        final Long creditGLAccountId = taxComponent.getCreditAccount().getId();
                        final Long debitGLAccountId = taxComponent.getDebitAccount().getId();
                        loanChargePaidData.markAsApplicableToSpecifiedDueDateTaxes();
                        loanChargePaidData.setCreditGLAccountId(creditGLAccountId);
                        loanChargePaidData.setDebitGLAccountId(debitGLAccountId);
                        loanChargePaidData.setTaxAmount(taxAmount);
                        loanChargePaidData.setTaxGroupId(loanCharge.getCharge().getTaxGroup().getId());
                        loanChargePaidData.setTaxGroupName(loanCharge.getCharge().getTaxGroup().getName());
                    }
                }
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

}
