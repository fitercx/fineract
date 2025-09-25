package com.crediblex.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for handling custom interest calculations for RECEIVABLE LOC loans This service provides methods to adjust
 * interest calculations after Fineract's standard calculations have been performed, specifically for RECEIVABLE Line of
 * Credit loans.
 */
@Service
public class CustomLoanInterestCalculationService {

    /**
     * Adjusts interest calculations for RECEIVABLE LOC loans This method should be called after Fineract's standard
     * interest calculations to override the interest amounts with the expected interest for RECEIVABLE loans
     *
     * @param loan
     *            The loan to adjust interest calculations for The LOC product type ("RECEIVABLE" or "PAYABLE")
     * @param expectedInterest
     *            The expected interest amount for the loan
     * @return true if adjustments were made, false otherwise
     */
    @Transactional
    public boolean adjustInterestCalculationsForReceivableLoan(Loan loan, BigDecimal expectedInterest) {
        try {
            if (expectedInterest == null) {
                return false;
            }

            // Adjust the interest in the repayment schedule installments
            List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
            if (installments == null || installments.isEmpty()) {
                return false;
            }

            // For RECEIVABLE loans, we need to distribute the expected interest across installments
            // This is a simplified approach - in a real implementation, you might want to
            // distribute based on the original interest proportions
            Money totalExpectedInterest = Money.of(loan.getCurrency(), expectedInterest);
            Money totalOriginalInterest = Money.zero(loan.getCurrency());

            // Calculate total original interest
            for (LoanRepaymentScheduleInstallment installment : installments) {
                if (installment.getInterestCharged() != null) {
                    totalOriginalInterest = totalOriginalInterest.plus(installment.getInterestCharged(loan.getCurrency()));
                }
            }

            if (totalOriginalInterest.isZero()) {
                return false;
            }

            // Distribute expected interest proportionally across installments
            Money remainingInterest = totalExpectedInterest;
            for (int i = 0; i < installments.size(); i++) {
                LoanRepaymentScheduleInstallment installment = installments.get(i);

                if (installment.getInterestCharged() != null) {
                    Money originalInterest = installment.getInterestCharged(loan.getCurrency());

                    // Calculate proportional interest for this installment
                    Money proportionalInterest;
                    if (i == installments.size() - 1) {
                        // Last installment gets the remaining interest to avoid rounding errors
                        proportionalInterest = remainingInterest;
                    } else {
                        // Calculate proportional amount
                        BigDecimal proportion = originalInterest.getAmount().divide(totalOriginalInterest.getAmount(), 10,
                                java.math.RoundingMode.HALF_UP);
                        proportionalInterest = totalExpectedInterest.multiplyRetainScale(proportion,
                                new java.math.MathContext(10, java.math.RoundingMode.HALF_UP));
                        remainingInterest = remainingInterest.minus(proportionalInterest);
                    }

                    // Update the installment with the new interest amount
                    installment.updateInterestCharged(proportionalInterest.getAmount());
                }
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Adjusts the principal due amount for RECEIVABLE LOC loans For RECEIVABLE loans, the principal due should be
     * Approved Amount - Interest
     *
     * @param loan
     *            The loan to adjust principal due for
     * @param correctPrincipalDue
     *            The correct principal due amount (Approved Amount - Interest)
     * @return true if adjustments were made, false otherwise
     */
    @Transactional
    public boolean adjustPrincipalDueForReceivableLoan(Loan loan, BigDecimal correctPrincipalDue) {
        try {
            if (correctPrincipalDue == null) {
                return false;
            }

            // Adjust the principal due in the repayment schedule installments
            List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
            if (installments == null || installments.isEmpty()) {
                return false;
            }

            // For RECEIVABLE loans, we need to adjust the principal due to be Approved Amount - Interest
            Money correctPrincipalDueMoney = Money.of(loan.getCurrency(), correctPrincipalDue);
            Money totalOriginalPrincipalDue = Money.zero(loan.getCurrency());

            // Calculate total original principal due
            for (LoanRepaymentScheduleInstallment installment : installments) {
                if (installment.getPrincipal() != null) {
                    totalOriginalPrincipalDue = totalOriginalPrincipalDue.plus(installment.getPrincipal(loan.getCurrency()));
                }
            }

            if (totalOriginalPrincipalDue.isZero()) {
                return false;
            }

            // Distribute the correct principal due across installments proportionally
            Money remainingPrincipalDue = correctPrincipalDueMoney;
            for (int i = 0; i < installments.size(); i++) {
                LoanRepaymentScheduleInstallment installment = installments.get(i);

                if (installment.getPrincipal() != null) {
                    Money originalPrincipalDue = installment.getPrincipal(loan.getCurrency());

                    // Calculate proportional principal due for this installment
                    Money proportionalPrincipalDue;
                    if (i == installments.size() - 1) {
                        // Last installment gets the remaining principal due to avoid rounding errors
                        proportionalPrincipalDue = remainingPrincipalDue;
                    } else {
                        // Calculate proportional amount
                        BigDecimal proportion = originalPrincipalDue.getAmount().divide(totalOriginalPrincipalDue.getAmount(), 10,
                                java.math.RoundingMode.HALF_UP);
                        proportionalPrincipalDue = correctPrincipalDueMoney.multiplyRetainScale(proportion,
                                new java.math.MathContext(10, java.math.RoundingMode.HALF_UP));
                        remainingPrincipalDue = remainingPrincipalDue.minus(proportionalPrincipalDue);
                    }

                    // Update the installment with the new principal due amount
                    installment.updatePrincipal(proportionalPrincipalDue.getAmount());
                }
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }
}
