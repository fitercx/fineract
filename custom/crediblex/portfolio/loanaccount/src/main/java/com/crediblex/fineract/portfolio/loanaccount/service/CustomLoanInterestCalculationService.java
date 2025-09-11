package com.crediblex.fineract.portfolio.loanaccount.service;

import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for handling custom interest calculations for RECEIVABLE LOC loans
 * This service provides methods to adjust interest calculations after Fineract's
 * standard calculations have been performed, specifically for RECEIVABLE Line of Credit loans.
 */
@Service
public class CustomLoanInterestCalculationService {

    private static final Logger log = LoggerFactory.getLogger(CustomLoanInterestCalculationService.class);

    /**
     * Adjusts interest calculations for RECEIVABLE LOC loans
     * This method should be called after Fineract's standard interest calculations
     * to override the interest amounts with the expected interest for RECEIVABLE loans
     * 
     * @param loan The loan to adjust interest calculations for
     * @param productType The LOC product type ("RECEIVABLE" or "PAYABLE")
     * @param expectedInterest The expected interest amount for the loan
     * @return true if adjustments were made, false otherwise
     */
    @Transactional
    public boolean adjustInterestCalculationsForReceivableLoan(Loan loan, String productType, BigDecimal expectedInterest) {
        try {
            Long loanId = loan.getId();
            
            if (!LocProductType.RECEIVABLE.name().equals(productType)) {
                log.debug("Loan {} is not a RECEIVABLE LOC loan, skipping interest adjustment", loanId);
                return false;
            }
            
            if (expectedInterest == null) {
                log.warn("Could not calculate expected interest for RECEIVABLE loan {}", loanId);
                return false;
            }
            
            // Adjust the interest in the repayment schedule installments
            List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
            if (installments == null || installments.isEmpty()) {
                log.warn("No repayment schedule installments found for loan {}", loanId);
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
                log.warn("Total original interest is zero for loan {}, cannot adjust", loanId);
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
                        BigDecimal proportion = originalInterest.getAmount().divide(totalOriginalInterest.getAmount(), 10, java.math.RoundingMode.HALF_UP);
                        proportionalInterest = totalExpectedInterest.multiplyRetainScale(proportion, new java.math.MathContext(10, java.math.RoundingMode.HALF_UP));
                        remainingInterest = remainingInterest.minus(proportionalInterest);
                    }
                    
                    // Update the installment with the new interest amount
                    installment.updateInterestCharged(proportionalInterest.getAmount());
                    
                    log.debug("Updated installment {} for loan {}: original interest = {}, new interest = {}", 
                        installment.getInstallmentNumber(), loanId, originalInterest, proportionalInterest);
                }
            }
            
            log.info("Successfully adjusted interest calculations for RECEIVABLE loan {}: total expected interest = {}", 
                loanId, totalExpectedInterest);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error adjusting interest calculations for RECEIVABLE loan {}: {}", loan.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Adjusts interest calculations for foreclosure of RECEIVABLE LOC loans
     * This method calculates the final interest amount due at foreclosure
     * using the expected interest instead of the standard calculation
     * 
     * @param loan The loan being foreclosed
     * @param foreclosureDate The date of foreclosure
     * @param productType The LOC product type ("RECEIVABLE" or "PAYABLE")
     * @param expectedInterest The expected interest amount for the loan
     * @return The adjusted interest amount for foreclosure, or null if not applicable
     */
    public Money getAdjustedInterestForForeclosure(Loan loan, java.time.LocalDate foreclosureDate, String productType, BigDecimal expectedInterest) {
        try {
            Long loanId = loan.getId();
            
            if (!LocProductType.RECEIVABLE.name().equals(productType)) {
                return null; // Not applicable for non-RECEIVABLE loans
            }
            
            if (expectedInterest == null) {
                log.warn("Could not calculate expected interest for RECEIVABLE loan foreclosure {}", loanId);
                return null;
            }
            
            // For foreclosure, we might want to calculate interest up to the foreclosure date
            // This is a simplified implementation - you might want to calculate based on
            // the actual time period from disbursement to foreclosure
            Money adjustedInterest = Money.of(loan.getCurrency(), expectedInterest);
            
            log.debug("Calculated adjusted interest for foreclosure of RECEIVABLE loan {}: {}", loanId, adjustedInterest);
            
            return adjustedInterest;
            
        } catch (Exception e) {
            log.error("Error calculating adjusted interest for foreclosure of RECEIVABLE loan {}: {}", loan.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Adjusts interest calculations for rescheduling of RECEIVABLE LOC loans
     * This method recalculates the interest for rescheduled loans using the expected interest
     * 
     * @param loan The loan being rescheduled
     * @param productType The LOC product type ("RECEIVABLE" or "PAYABLE")
     * @param expectedInterest The expected interest amount for the loan
     * @return true if adjustments were made, false otherwise
     */
    @Transactional
    public boolean adjustInterestCalculationsForRescheduling(Loan loan, String productType, BigDecimal expectedInterest) {
        try {
            Long loanId = loan.getId();
            
            if (!LocProductType.RECEIVABLE.name().equals(productType)) {
                log.debug("Loan {} is not a RECEIVABLE LOC loan, skipping rescheduling interest adjustment", loanId);
                return false;
            }
            
            // For rescheduling, we need to recalculate the expected interest
            // This might involve adjusting the tenor or other parameters
            // For now, we'll use the same logic as the standard adjustment
            return adjustInterestCalculationsForReceivableLoan(loan, productType, expectedInterest);
            
        } catch (Exception e) {
            log.error("Error adjusting interest calculations for rescheduling of RECEIVABLE loan {}: {}", loan.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Adjusts the principal due amount for RECEIVABLE LOC loans
     * For RECEIVABLE loans, the principal due should be Approved Amount - Interest
     * 
     * @param loan The loan to adjust principal due for
     * @param correctPrincipalDue The correct principal due amount (Approved Amount - Interest)
     * @return true if adjustments were made, false otherwise
     */
    @Transactional
    public boolean adjustPrincipalDueForReceivableLoan(Loan loan, BigDecimal correctPrincipalDue) {
        try {
            Long loanId = loan.getId();
            
            if (correctPrincipalDue == null) {
                log.warn("Correct principal due amount is null for loan {}", loanId);
                return false;
            }
            
            // Adjust the principal due in the repayment schedule installments
            List<LoanRepaymentScheduleInstallment> installments = loan.getRepaymentScheduleInstallments();
            if (installments == null || installments.isEmpty()) {
                log.warn("No repayment schedule installments found for loan {}", loanId);
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
                log.warn("Total original principal due is zero for loan {}, cannot adjust", loanId);
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
                        BigDecimal proportion = originalPrincipalDue.getAmount().divide(totalOriginalPrincipalDue.getAmount(), 10, java.math.RoundingMode.HALF_UP);
                        proportionalPrincipalDue = correctPrincipalDueMoney.multiplyRetainScale(proportion, new java.math.MathContext(10, java.math.RoundingMode.HALF_UP));
                        remainingPrincipalDue = remainingPrincipalDue.minus(proportionalPrincipalDue);
                    }
                    
                    // Update the installment with the new principal due amount
                    installment.updatePrincipal(proportionalPrincipalDue.getAmount());
                    
                    log.debug("Updated installment {} for loan {}: original principal due = {}, new principal due = {}", 
                        installment.getInstallmentNumber(), loanId, originalPrincipalDue, proportionalPrincipalDue);
                }
            }
            
            log.info("Successfully adjusted principal due calculations for RECEIVABLE loan {}: total correct principal due = {}", 
                loanId, correctPrincipalDueMoney);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error adjusting principal due calculations for RECEIVABLE loan {}: {}", loan.getId(), e.getMessage());
            return false;
        }
    }
}
