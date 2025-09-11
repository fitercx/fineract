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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Custom implementation of LoanChargeService that handles late payment fee calculations
 * based on discounted amount instead of disbursed amount for Line of Credit loans.
 */
@Service
@Primary
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
    }

    @Override
    public void recalculateLoanCharge(final Loan loan, final LoanCharge loanCharge, final int penaltyWaitPeriod) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal chargeAmt;
        BigDecimal totalChargeAmt = BigDecimal.ZERO;
        
        if (loanCharge.getChargeCalculation().isPercentageBased()) {
            if (loanCharge.isOverdueInstallmentCharge()) {
                // Use custom logic for overdue installment charges (late payment fees)
                amount = calculateOverdueAmountPercentageAppliedToWithDiscountedAmount(loan, loanCharge, penaltyWaitPeriod);
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
        try {
            // Check if this is a RECEIVABLE LOC loan that should use discounted amount
            if (latePaymentFeeCalculationService.shouldUseDiscountedAmountForLatePaymentFee(loan)) {
                log.debug("Using discounted amount for RECEIVABLE LOC late payment fee calculation for loan {}", loan.getId());
                
                // Get the installment for this charge
                var installment = loanCharge.getOverdueInstallmentCharge().getInstallment();
                var graceDate = DateUtils.getBusinessLocalDate().minusDays(penaltyWaitPeriod);
                
                if (DateUtils.isAfter(graceDate, installment.getDueDate())) {
                    // Use discounted amount (net disbursal amount) instead of disbursed amount
                    var discountedAmount = loan.getNetDisbursalAmount();
                    if (discountedAmount != null && discountedAmount.compareTo(BigDecimal.ZERO) > 0) {
                        log.debug("Using discounted amount {} for RECEIVABLE LOC late payment fee calculation for loan {}", 
                                discountedAmount, loan.getId());
                        return discountedAmount;
                    }
                }
            }
            
            // Check if this is a PAYABLE LOC loan
            if (latePaymentFeeCalculationService.isPayableLocLoan(loan)) {
                log.debug("Using LOC late payment fee for PAYABLE LOC loan {}", loan.getId());
                
                // Get the late payment fee from LOC table
                BigDecimal locLatePaymentFee = latePaymentFeeCalculationService.getLocLatePaymentFee(loan);
                if (locLatePaymentFee != null && locLatePaymentFee.compareTo(BigDecimal.ZERO) > 0) {
                    log.debug("Using LOC late payment fee {} for PAYABLE LOC loan {}", 
                            locLatePaymentFee, loan.getId());
                    return locLatePaymentFee;
                }
            }
            
            // Fall back to original calculation if not applicable or if there's an error
            return loan.calculateOverdueAmountPercentageAppliedTo(loanCharge, penaltyWaitPeriod);
            
        } catch (Exception e) {
            log.warn("Error calculating overdue amount with LOC-specific logic for loan {}: {}", 
                    loan.getId(), e.getMessage());
            // Fall back to original calculation
            return loan.calculateOverdueAmountPercentageAppliedTo(loanCharge, penaltyWaitPeriod);
        }
    }
}
