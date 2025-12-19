package com.crediblex.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanDisbursementValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementService;
import org.apache.fineract.portfolio.loanaccount.service.ReprocessLoanTransactionsService;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;

public class CustomLoanDisbursementService extends LoanDisbursementService {

    public CustomLoanDisbursementService(LoanChargeValidator loanChargeValidator, LoanDisbursementValidator loanDisbursementValidator,
            ReprocessLoanTransactionsService reprocessLoanTransactionsService) {
        super(loanChargeValidator, loanDisbursementValidator, reprocessLoanTransactionsService);
    }

    public void handleDisbursementTransaction(final Loan loan, final LocalDate disbursedOn, final PaymentDetail paymentDetail) {
        // add repayment transaction to track incoming money from client to mfi for (charges due at time of
        // disbursement)

        final Money totalFeeChargesDueAtDisbursement = loan.getSummary().getTotalFeeChargesDueAtDisbursement(loan.getCurrency());

        Money feeAndPenaltyPortion = Money.zero(loan.getCurrency());
        Money taxesPaid = Money.zero(loan.getCurrency());

        final LoanTransaction chargesPayment = LoanTransaction.repaymentAtDisbursement(loan.getOffice(), feeAndPenaltyPortion,
                paymentDetail, disbursedOn, null);
        final LoanTransaction taxPaymentTransaction = LoanTransaction.vatDeductionAtDisbursement(loan.getOffice(), taxesPaid, paymentDetail,
                disbursedOn, null);

        final Integer installmentNumber = null;
        for (final LoanCharge charge : loan.getActiveCharges()) {
            LocalDate actualDisbursementDate = loan.getActualDisbursementDate(charge);
            if ((charge.getCharge().getChargeTimeType().equals(ChargeTimeType.DISBURSEMENT.getValue())
                    && disbursedOn.equals(actualDisbursementDate) && !charge.isWaived() && !charge.isFullyPaid())
                    || (charge.getCharge().getChargeTimeType().equals(ChargeTimeType.TRANCHE_DISBURSEMENT.getValue())
                            && disbursedOn.equals(actualDisbursementDate) && !charge.isWaived() && !charge.isFullyPaid())
                    || isMultiTrancheDisbursementChargeApplicable(charge, loan, disbursedOn)) {
                if (totalFeeChargesDueAtDisbursement.isGreaterThanZero() && !charge.getChargePaymentMode().isPaymentModeAccountTransfer()) {
                    // Calculate proportional fee for multi-tranche loans
                    Money chargeAmount = calculateProportionalChargeAmount(loan, charge, disbursedOn);
                    Money taxAmount = calculateProportionalTaxAmount(loan, charge, disbursedOn);

                    // Mark charge as fully paid only if this is a tranche-specific charge or single disbursement
                    if (charge.getTrancheDisbursementCharge() != null || !loan.isMultiDisburmentLoan()) {
                        charge.markAsFullyPaid();
                        if (charge.hasTax() && taxAmount.isGreaterThanZero()) {
                            charge.markAsFullyPaidWithTaxes();
                        }
                    } else {
                        // For multi-tranche DISBURSEMENT charges without tranche association,
                        // track partial payment to ensure total doesn't exceed full charge amount
                        Money totalPaidSoFar = charge.getAmountPaid(loan.getCurrency()).plus(chargeAmount);
                        if (totalPaidSoFar.isGreaterThanOrEqualTo(charge.getAmount(loan.getCurrency()))) {
                            charge.markAsFullyPaid();
                            if (charge.hasTax()) {
                                charge.markAsFullyPaidWithTaxes();
                            }
                        } else {
                            charge.updatePaidAmountBy(chargeAmount, null, null);
                            charge.updateTaxAmountPaidBy(taxAmount);
                        }
                    }

                    final LoanChargePaidBy loanChargePaidBy = new LoanChargePaidBy(chargesPayment, charge, chargeAmount.getAmount(),
                            installmentNumber);
                    chargesPayment.getLoanChargesPaid().add(loanChargePaidBy);
                    // Treat all such charge amounts as fee portion (no longer misusing interest portion here)
                    feeAndPenaltyPortion = feeAndPenaltyPortion.plus(chargeAmount);
                    if (charge.hasTax() && taxAmount.isGreaterThanZero()) {
                        taxesPaid = taxesPaid.plus(taxAmount);
                        final LoanChargePaidBy taxChargePaidBy = new LoanChargePaidBy(taxPaymentTransaction, charge, taxAmount.getAmount(),
                                installmentNumber);
                        taxPaymentTransaction.getLoanChargesPaid().add(taxChargePaidBy);
                    }
                }
            } else if (disbursedOn.equals(loan.getActualDisbursementDate())
                    && loan.isNoneOrCashOrUpfrontAccrualAccountingEnabledOnLoanProduct()) {
                loan.handleChargeAppliedTransaction(charge, disbursedOn);
            }
        }

        if (feeAndPenaltyPortion.isGreaterThanZero()) {
            final Money zero = Money.zero(loan.getCurrency());
            chargesPayment.updateComponentsAndTotal(zero, zero, feeAndPenaltyPortion, zero);
            chargesPayment.updateLoan(loan);
            loan.addLoanTransaction(chargesPayment);
            loan.updateLoanOutstandingBalances();
        }

        if (taxesPaid.isGreaterThanZero()) {
            final Money zero = Money.zero(loan.getCurrency());
            taxPaymentTransaction.updateComponentsAndTotal(zero, zero, taxesPaid, zero);
            taxPaymentTransaction.updateLoan(loan);
            loan.addLoanTransaction(taxPaymentTransaction);
            loan.updateLoanOutstandingBalances();
        }

        final LocalDate expectedDate = loan.getExpectedFirstRepaymentOnDate();
        loanDisbursementValidator.validateDisburseDate(loan, disbursedOn, expectedDate);
    }

    private boolean isMultiTrancheDisbursementChargeApplicable(final LoanCharge loanCharge, final Loan loan, final LocalDate disbursedOn) {
        if (loan.isMultiDisburmentLoan() && loanCharge.getCharge().getChargeTimeType().equals(ChargeTimeType.DISBURSEMENT.getValue())) {
            final List<LoanDisbursementDetails> loanDisbursementDetails = loan.getDisbursementDetails();
            for (final LoanDisbursementDetails disbursementDetail : loanDisbursementDetails) {
                if (disbursementDetail.getDisbursementDate().isEqual(disbursedOn)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Calculates proportional charge amount for multi-tranche loans. For DISBURSEMENT charges on multi-tranche loans
     * without tranche association, calculates: (trancheAmount / sanctionedAmount) * totalChargeAmount
     *
     * @param loan
     *            the loan
     * @param charge
     *            the charge
     * @param disbursedOn
     *            the disbursement date
     * @return the proportional charge amount
     */
    private Money calculateProportionalChargeAmount(final Loan loan, final LoanCharge charge, final LocalDate disbursedOn) {
        // If charge is linked to a specific tranche, use its amount directly
        if (charge.getTrancheDisbursementCharge() != null) {
            return charge.getAmount(loan.getCurrency());
        }

        // For multi-tranche loans with DISBURSEMENT charges, calculate proportionally
        if (loan.isMultiDisburmentLoan() && charge.getCharge().getChargeTimeType().equals(ChargeTimeType.DISBURSEMENT.getValue())) {
            // Find the current tranche being disbursed
            Optional<LoanDisbursementDetails> currentTranche = loan.getDisbursementDetails().stream()
                    .filter(detail -> disbursedOn.equals(detail.actualDisbursementDate())).findFirst();

            if (currentTranche.isPresent()) {
                Money trancheAmount = Money.of(loan.getCurrency(), currentTranche.get().principal());
                Money sanctionedAmount = Money.of(loan.getCurrency(), loan.getApprovedPrincipal());
                Money totalChargeAmount = charge.getAmount(loan.getCurrency());

                if (sanctionedAmount.isGreaterThanZero()) {
                    // Calculate proportional fee: (trancheAmount / sanctionedAmount) * totalChargeAmount
                    BigDecimal proportionalAmount = totalChargeAmount.getAmount().multiply(trancheAmount.getAmount())
                            .divide(sanctionedAmount.getAmount(), 6, RoundingMode.HALF_UP);
                    return Money.of(loan.getCurrency(), proportionalAmount);
                }
            }
        }

        // Default: use full charge amount (for single disbursement or non-multi-tranche loans)
        return charge.getAmount(loan.getCurrency());
    }

    /**
     * Calculates proportional tax amount for multi-tranche loans.
     *
     * @param loan
     *            the loan
     * @param charge
     *            the charge
     * @param disbursedOn
     *            the disbursement date
     * @return the proportional tax amount
     */
    private Money calculateProportionalTaxAmount(final Loan loan, final LoanCharge charge, final LocalDate disbursedOn) {
        if (!charge.hasTax()) {
            return Money.zero(loan.getCurrency());
        }

        // If charge is linked to a specific tranche, use its tax amount directly
        if (charge.getTrancheDisbursementCharge() != null) {
            return charge.getTaxAmount(loan.getCurrency());
        }

        // For multi-tranche loans with DISBURSEMENT charges, calculate tax proportionally
        if (loan.isMultiDisburmentLoan() && charge.getCharge().getChargeTimeType().equals(ChargeTimeType.DISBURSEMENT.getValue())) {
            Optional<LoanDisbursementDetails> currentTranche = loan.getDisbursementDetails().stream()
                    .filter(detail -> disbursedOn.equals(detail.actualDisbursementDate())).findFirst();

            if (currentTranche.isPresent()) {
                Money trancheAmount = Money.of(loan.getCurrency(), currentTranche.get().principal());
                Money sanctionedAmount = Money.of(loan.getCurrency(), loan.getApprovedPrincipal());
                Money totalTaxAmount = charge.getTaxAmount(loan.getCurrency());

                if (sanctionedAmount.isGreaterThanZero()) {
                    BigDecimal proportionalTax = totalTaxAmount.getAmount().multiply(trancheAmount.getAmount())
                            .divide(sanctionedAmount.getAmount(), 6, RoundingMode.HALF_UP);
                    return Money.of(loan.getCurrency(), proportionalTax);
                }
            }
        }

        // Default: use full tax amount
        return charge.getTaxAmount(loan.getCurrency());
    }
}
