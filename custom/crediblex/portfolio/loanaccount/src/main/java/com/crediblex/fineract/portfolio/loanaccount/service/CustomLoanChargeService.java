package com.crediblex.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanChargeService extends LoanChargeService {

    private static final Logger log = LoggerFactory.getLogger(CustomLoanChargeService.class);

    public CustomLoanChargeService(LoanChargeValidator loanChargeValidator,
            LoanTransactionProcessingService loanTransactionProcessingService) {
        super(loanChargeValidator, loanTransactionProcessingService);
    }

    @Override
    public void recalculateLoanCharge(final Loan loan, final LoanCharge loanCharge, final int penaltyWaitPeriod) {
        BigDecimal approvedPrincipal = loan.getApprovedPrincipal();
        BigDecimal currentPrincipal = loan.getLoanRepaymentScheduleDetail().getPrincipal().getAmount();
        // For LOC Receivable loans, only reset principal to approvedPrincipal if it's already at approvedPrincipal.
        // If principal is at proposed principal (set during schedule regeneration), don't reset it - we want to
        // use that proposed principal for charge recalculation.
        boolean shouldResetPrincipal = loan.isReceivableLocLoan() && currentPrincipal.compareTo(approvedPrincipal) == 0;
        if (loan.isReceivableLocLoan() && shouldResetPrincipal) {
            // Principal is at approvedPrincipal, ensure it stays there (no-op but consistent)
            loan.setPrincipal(approvedPrincipal);
        }
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal chargeAmt;
        BigDecimal totalChargeAmt = BigDecimal.ZERO;
        if (loanCharge.getChargeCalculation().isPercentageBased()) {
            if (loanCharge.isOverdueInstallmentCharge()) {
                amount = loan.calculateOverdueAmountPercentageAppliedTo(loanCharge, penaltyWaitPeriod);
            } else {
                // For multi-disbursement loans with DISBURSEMENT charges not linked to a specific tranche,
                // use approved principal instead of letting getDerivedAmountForCharge return the first tranche amount
                if (loan.isMultiDisburmentLoan() && loanCharge.isDisbursementCharge() && loanCharge.getTrancheDisbursementCharge() == null
                        && loanCharge.getChargeCalculation().isPercentageOfAmount()) {
                    amount = approvedPrincipal;
                } else {
                    // calculateAmountPercentageAppliedTo now uses getProposedPrincipal() for LOC Receivable loans
                    amount = loan.calculateAmountPercentageAppliedTo(loanCharge);
                }
            }
            chargeAmt = loanCharge.getPercentage();
            if (loanCharge.isInstalmentFee()) {
                // For LOC Receivable loans with installment fees, use amount (proposed principal)
                // instead of calculating from installments (which uses disbursed principal)
                // This ensures consistent fee calculation: 10% of loan amount, not 10% of disbursed amount
                if (loan.isReceivableLocLoan() && loanCharge.getChargeCalculation().isPercentageBased()) {
                    // Calculate directly from amount which is already set to proposed principal via
                    // calculateAmountPercentageAppliedTo()
                    // This applies to all percentage-based charges (PERCENT_OF_AMOUNT, PERCENT_OF_AMOUNT_AND_INTEREST,
                    // PERCENT_OF_INTEREST)
                    totalChargeAmt = amount.multiply(chargeAmt).divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);
                    log.info(
                            "LOC Receivable loan {}: Recalculating installment fee. amountPercentageAppliedTo={}, percentage={}, totalChargeAmt={}, current principal={}",
                            loan.getId(), amount, chargeAmt, totalChargeAmt, currentPrincipal);
                } else {
                    totalChargeAmt = loan.calculatePerInstallmentChargeAmount(loanCharge);
                }
            }
        } else {
            chargeAmt = loanCharge.amountOrPercentage();
        }
        if (loanCharge.isActive()) {
            loan.clearLoanInstallmentChargesBeforeRegeneration(loanCharge);
            log.info(
                    "Loan {}: Calling loanCharge.update() with chargeAmt={}, amountPercentageAppliedTo={}, totalChargeAmt={}, current principal={}",
                    loan.getId(), chargeAmt, amount, totalChargeAmt, loan.getLoanRepaymentScheduleDetail().getPrincipal().getAmount());
            loanCharge.update(chargeAmt, loanCharge.getDueLocalDate(), amount, loan.fetchNumberOfInstallmensAfterExceptions(),
                    totalChargeAmt);
            log.info("Loan {}: After loanCharge.update(), charge amount={}, amountOutstanding={}", loan.getId(), loanCharge.getAmount(),
                    loanCharge.getAmountOutstanding());
            loanChargeValidator.validateChargeHasValidSpecifiedDateIfApplicable(loan, loanCharge, loan.getDisbursementDate());
        }

        // Only restore principal if we reset it at the start (defensive cleanup)
        if (shouldResetPrincipal) {
            loan.setPrincipal(approvedPrincipal);
        }
    }

}
