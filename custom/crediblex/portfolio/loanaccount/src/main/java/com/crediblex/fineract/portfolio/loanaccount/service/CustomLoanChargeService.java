package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@Slf4j
public class CustomLoanChargeService extends LoanChargeService {

    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

    public CustomLoanChargeService(LoanChargeValidator loanChargeValidator,
            LoanTransactionProcessingService loanTransactionProcessingService,
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        super(loanChargeValidator, loanTransactionProcessingService);
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
    }

    @Override
    public void recalculateLoanCharge(final Loan loan, final LoanCharge loanCharge, final int penaltyWaitPeriod) {
        // Check if this is a LOC Receivable loan by checking LOC params directly
        // The isReceivableLocLoan field might not be set yet during approval
        // Note: During loan submission, the loan might not have an ID yet, so check for null
        boolean isReceivableLocLoan = false;
        if (loan.getId() != null) {
            Optional<LoanLineOfCreditParams> locParams = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
            isReceivableLocLoan = locParams.isPresent() && locParams.get().getLineOfCredit().getProductType().isReceivable();
        } else {
            // If loan ID is null (during submission), fall back to the field value
            isReceivableLocLoan = loan.isReceivableLocLoan();
        }

        BigDecimal approvedPrincipal = loan.getApprovedPrincipal();
        // For LOC Receivable loans, preserve the original principal (proposed amount) instead of setting it to approved
        // amount
        // This ensures charge calculations use the proposed principal (loan amount before interest deduction)
        // The getDerivedAmountForCharge() method already handles LOC Receivable loans correctly by returning
        // getProposedPrincipal()
        // Use LOC params check instead of loan.isReceivableLocLoan() which might not be set yet
        if (!isReceivableLocLoan) {
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
                // use disbursed principal when at least one tranche is already disbursed (e.g. after deleting
                // future tranches). Otherwise use approved principal (e.g. at approval, no disbursement yet).
                // This keeps fee tied to actual disbursed tranche amount, not full loan amount.
                if (loan.isMultiDisburmentLoan() && loanCharge.isDisbursementCharge() && loanCharge.getTrancheDisbursementCharge() == null
                        && loanCharge.getChargeCalculation().isPercentageOfAmount()) {
                    BigDecimal disbursedAmount = loan.getDisbursedAmount();
                    if (disbursedAmount != null && disbursedAmount.compareTo(BigDecimal.ZERO) > 0) {
                        amount = disbursedAmount;
                    } else {
                        amount = approvedPrincipal;
                    }
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
                // Use isReceivableLocLoan from LOC params check (not the field which might not be set)
                if (isReceivableLocLoan && loanCharge.getChargeCalculation().isPercentageBased()) {
                    // Calculate directly from amount which is already set to proposed principal via
                    // calculateAmountPercentageAppliedTo()
                    // This applies to all percentage-based charges (PERCENT_OF_AMOUNT, PERCENT_OF_AMOUNT_AND_INTEREST,
                    // PERCENT_OF_INTEREST)
                    totalChargeAmt = amount.multiply(chargeAmt).divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);
                } else {
                    totalChargeAmt = loan.calculatePerInstallmentChargeAmount(loanCharge);
                }
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

        // For LOC Receivable loans, principal should remain at proposed amount (don't restore to approvedPrincipal)
        // For other loans, restore to approvedPrincipal
        // Use isReceivableLocLoan from LOC params check (not the field which might not be set)
        if (!isReceivableLocLoan) {
            loan.setPrincipal(approvedPrincipal);
        }
    }

}
