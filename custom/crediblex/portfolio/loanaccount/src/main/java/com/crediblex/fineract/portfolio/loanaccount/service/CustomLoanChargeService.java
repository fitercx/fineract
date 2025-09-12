/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the specific language
 * governing permissions and limitations under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Custom implementation of LoanChargeService that handles late payment fee calculations
 * based on discounted amount instead of disbursed amount for Line of Credit loans.
 */
@Service
@Slf4j
public class CustomLoanChargeService extends LoanChargeService {

    private final CustomLatePaymentFeeCalculationService latePaymentFeeCalculationService;
    private final LoanChargeValidator loanChargeValidator;

    @Autowired
    public CustomLoanChargeService(LoanChargeValidator loanChargeValidator,
                                 org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService loanTransactionProcessingService,
                                 CustomLatePaymentFeeCalculationService latePaymentFeeCalculationService) {
        super(loanChargeValidator, loanTransactionProcessingService);
        this.latePaymentFeeCalculationService = latePaymentFeeCalculationService;
        this.loanChargeValidator = loanChargeValidator;
        log.info("🚀 CustomLoanChargeService initialized successfully!");
        log.info("CustomLoanChargeService will be used for all loan charge calculations!");
    }

    /**
     * Test method to verify our service is being used
     */
    public void testService() {
        log.info("🧪 TEST: CustomLoanChargeService is working correctly!");
    }

    @Override
    public void recalculateLoanCharge(final Loan loan, final LoanCharge loanCharge, final int penaltyWaitPeriod) {
        log.info("=== RECALCULATING LOAN CHARGE ===");
        log.info("Loan ID: {}, Charge ID: {}, Charge Name: '{}', Is Overdue Installment Charge: {}, Is Active: {}", 
                loan.getId(), loanCharge.getId(), loanCharge.getCharge().getName(), 
                loanCharge.isOverdueInstallmentCharge(), loanCharge.isActive());
        log.info("Charge Calculation Type: {}, Is Percentage Based: {}, Percentage: {}", 
                loanCharge.getChargeCalculation(), loanCharge.getChargeCalculation().isPercentageBased(), 
                loanCharge.getPercentage());
        log.info("Current Charge Amount: {}, Amount Outstanding: {}", 
                loanCharge.amountOrPercentage(), loanCharge.getAmountOutstanding());
        
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal chargeAmt;
        BigDecimal totalChargeAmt = BigDecimal.ZERO;
        
        if (loanCharge.getChargeCalculation().isPercentageBased()) {
            if (loanCharge.isOverdueInstallmentCharge()) {
                log.info("Processing overdue installment charge with custom logic");
                // Use custom logic for overdue installment charges (late payment fees)
                amount = calculateOverdueAmountPercentageAppliedToWithDiscountedAmount(loan, loanCharge, penaltyWaitPeriod);
                log.info("Custom calculation result: {}", amount);
            } else {
                log.info("Processing regular charge with standard logic");
                amount = loan.calculateAmountPercentageAppliedTo(loanCharge);
                log.info("Standard calculation result: {}", amount);
            }
            chargeAmt = loanCharge.getPercentage();
            if (loanCharge.isInstalmentFee()) {
                totalChargeAmt = loan.calculatePerInstallmentChargeAmount(loanCharge);
            }
        } else {
            log.info("Processing flat charge");
            chargeAmt = loanCharge.amountOrPercentage();
        }
        
        log.info("Final calculated amount: {}, chargeAmt: {}, totalChargeAmt: {}", amount, chargeAmt, totalChargeAmt);
        
        if (loanCharge.isActive()) {
            log.info("Updating active charge with new amounts");
            loan.clearLoanInstallmentChargesBeforeRegeneration(loanCharge);
            loanCharge.update(chargeAmt, loanCharge.getDueLocalDate(), amount, loan.fetchNumberOfInstallmensAfterExceptions(),
                    totalChargeAmt);
            loanChargeValidator.validateChargeHasValidSpecifiedDateIfApplicable(loan, loanCharge, loan.getDisbursementDate());
            log.info("Charge updated successfully. New amount outstanding: {}", loanCharge.getAmountOutstanding());
        } else {
            log.info("Charge is not active, skipping update");
        }
        
        log.info("=== END RECALCULATING LOAN CHARGE ===");
    }

    /**
     * Calculates the overdue amount percentage applied to for late payment fees.
     * For RECEIVABLE Line of Credit loans, this method uses the discounted amount (net disbursal amount).
     * For PAYABLE Line of Credit loans, this method uses the original calculation but retrieves the late payment fee from LOC.
     * 
     * @param loan the loan entity
     * @param loanCharge the loan charge
     * @param penaltyWaitPeriod the penalty wait period
     * @return the calculated amount
     */
    private BigDecimal calculateOverdueAmountPercentageAppliedToWithDiscountedAmount(final Loan loan, 
                                                                                    final LoanCharge loanCharge, 
                                                                                    final int penaltyWaitPeriod) {
        log.info("=== CUSTOM OVERDUE AMOUNT CALCULATION ===");
        log.info("Loan ID: {}, Penalty Wait Period: {}", loan.getId(), penaltyWaitPeriod);
        
        try {
            // Check if this is a RECEIVABLE LOC loan that should use discounted amount
            boolean shouldUseDiscounted = latePaymentFeeCalculationService.shouldUseDiscountedAmountForLatePaymentFee(loan);
            log.info("Should use discounted amount for RECEIVABLE LOC: {}", shouldUseDiscounted);
            
            if (shouldUseDiscounted) {
                log.info("Processing RECEIVABLE LOC loan with discounted amount");
                
                // Get the installment for this charge
                var installment = loanCharge.getOverdueInstallmentCharge().getInstallment();
                var graceDate = DateUtils.getBusinessLocalDate().minusDays(penaltyWaitPeriod);
                
                log.info("Installment due date: {}, Grace date: {}, Is after grace date: {}", 
                        installment.getDueDate(), graceDate, DateUtils.isAfter(graceDate, installment.getDueDate()));
                
                if (DateUtils.isAfter(graceDate, installment.getDueDate())) {
                    // Use discounted amount (net disbursal amount) instead of disbursed amount
                    var discountedAmount = loan.getNetDisbursalAmount();
                    log.info("Net disbursal amount: {}, Principal amount: {}", 
                            discountedAmount, loan.getPrincipal().getAmount());
                    
                    if (discountedAmount != null && discountedAmount.compareTo(BigDecimal.ZERO) > 0) {
                        log.info("Using discounted amount {} for RECEIVABLE LOC late payment fee calculation for loan {}",
                                discountedAmount, loan.getId());
                        log.info("=== END CUSTOM OVERDUE AMOUNT CALCULATION (RECEIVABLE) ===");
                        return discountedAmount;
                    } else {
                        log.warn("Discounted amount is null or zero: {}", discountedAmount);
                    }
                } else {
                    log.info("Not after grace date, skipping discounted amount calculation");
                }
            }
            
            // Check if this is a PAYABLE LOC loan
            boolean isPayableLoc = latePaymentFeeCalculationService.isPayableLocLoan(loan);
            log.info("Is PAYABLE LOC loan: {}", isPayableLoc);
            
            if (isPayableLoc) {
                log.info("Processing PAYABLE LOC loan with LOC late payment fee");
                
                // Get the late payment fee from LOC table
                BigDecimal locLatePaymentFee = latePaymentFeeCalculationService.getLocLatePaymentFee(loan);
                log.info("LOC late payment fee from table: {}", locLatePaymentFee);
                
                if (locLatePaymentFee != null && locLatePaymentFee.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("Using LOC late payment fee {} for PAYABLE LOC loan {}",
                            locLatePaymentFee, loan.getId());
                    log.info("=== END CUSTOM OVERDUE AMOUNT CALCULATION (PAYABLE) ===");
                    return locLatePaymentFee;
                } else {
                    log.warn("LOC late payment fee is null or zero: {}", locLatePaymentFee);
                }
            }
            
            // Fall back to original calculation if not applicable or if there's an error
            log.info("Falling back to original calculation for loan {}", loan.getId());
            BigDecimal originalAmount = loan.calculateOverdueAmountPercentageAppliedTo(loanCharge, penaltyWaitPeriod);
            log.info("Original calculation result: {}", originalAmount);
            log.info("=== END CUSTOM OVERDUE AMOUNT CALCULATION (FALLBACK) ===");
            return originalAmount;
            
        } catch (Exception e) {
            log.error("Error calculating overdue amount with LOC-specific logic for loan {}: {}", 
                    loan.getId(), e.getMessage(), e);
            // Fall back to original calculation
            BigDecimal fallbackAmount = loan.calculateOverdueAmountPercentageAppliedTo(loanCharge, penaltyWaitPeriod);
            log.info("Fallback calculation result: {}", fallbackAmount);
            log.info("=== END CUSTOM OVERDUE AMOUNT CALCULATION (ERROR) ===");
            return fallbackAmount;
        }
    }
}
