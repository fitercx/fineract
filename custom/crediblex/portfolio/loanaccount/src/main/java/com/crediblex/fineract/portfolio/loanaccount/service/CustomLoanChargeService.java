package com.crediblex.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanChargeService extends LoanChargeService {

    public CustomLoanChargeService(LoanChargeValidator loanChargeValidator,
            LoanTransactionProcessingService loanTransactionProcessingService) {
        super(loanChargeValidator, loanTransactionProcessingService);
    }

    @Override
    public void recalculateLoanCharge(final Loan loan, final LoanCharge loanCharge, final int penaltyWaitPeriod) {
        BigDecimal approvedPrincipal = loan.getApprovedPrincipal();
        if (loan.isReceivableLocLoan()) {
            loan.setPrincipal(approvedPrincipal);
        }
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal chargeAmt;
        BigDecimal totalChargeAmt = BigDecimal.ZERO;
        if (loanCharge.getChargeCalculation().isPercentageBased()) {
            if (loanCharge.isOverdueInstallmentCharge()) {
                amount = loan.calculateOverdueAmountPercentageAppliedTo(loanCharge, penaltyWaitPeriod);
            } else {
                amount = loan.calculateAmountPercentageAppliedTo(loanCharge);
            }
            chargeAmt = loanCharge.getPercentage();
            if (loanCharge.isInstalmentFee()) {
                totalChargeAmt = loan.calculatePerInstallmentChargeAmount(loanCharge);
            }
        } else {
            chargeAmt = loanCharge.amountOrPercentage();
        }
        if (loanCharge.isActive()) {
            loan.clearLoanInstallmentChargesBeforeRegeneration(loanCharge);
            loanCharge.update(chargeAmt, loanCharge.getDueLocalDate(), amount, loan.fetchNumberOfInstallmensAfterExceptions(),
                    totalChargeAmt);
            loanChargeValidator.validateChargeHasValidSpecifiedDateIfApplicable(loan, loanCharge, loan.getDisbursementDate());
        }

        if (loan.isReceivableLocLoan()) {
            loan.setPrincipal(approvedPrincipal);
        }
    }

}
