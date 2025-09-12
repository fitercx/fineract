package com.crediblex.fineract.portfolio.loanaccount.service;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeAssembler;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Custom implementation of LoanChargeAssembler that applies LOC-specific logic
 * for late payment fee calculations when creating overdue installment charges.
 */
@Component
@Slf4j
public class CustomLoanChargeAssembler extends LoanChargeAssembler {

    private final CustomLatePaymentFeeCalculationService latePaymentFeeCalculationService;

    @Autowired
    public CustomLoanChargeAssembler(FromJsonHelper fromApiJsonHelper,
                                   ChargeRepositoryWrapper chargeRepository,
                                   LoanChargeRepository loanChargeRepository,
                                   LoanProductRepository loanProductRepository,
                                   org.apache.fineract.infrastructure.core.service.ExternalIdFactory externalIdFactory,
                                   CustomLatePaymentFeeCalculationService latePaymentFeeCalculationService) {
        super(fromApiJsonHelper, chargeRepository, loanChargeRepository, loanProductRepository, externalIdFactory);
        this.latePaymentFeeCalculationService = latePaymentFeeCalculationService;
        log.info("🚀 CustomLoanChargeAssembler initialized successfully!");
        log.info("CustomLoanChargeAssembler will be used for all loan charge creation!");
        System.out.println("🚀 CustomLoanChargeAssembler initialized successfully!");
        System.out.println("CustomLoanChargeAssembler will be used for all loan charge creation!");
    }

    @Override
    public LoanCharge createNewFromJson(final Loan loan, final Charge chargeDefinition, final JsonCommand command,
                                       final LocalDate dueDate) {
        log.info("=== CREATING LOAN CHARGE ===");
        log.info("Loan ID: {}, Charge ID: {}, Charge Name: '{}', Due Date: {}", 
                loan.getId(), chargeDefinition.getId(), chargeDefinition.getName(), dueDate);
        System.out.println("=== CREATING LOAN CHARGE ===");
        System.out.println("Loan ID: " + loan.getId() + ", Charge ID: " + chargeDefinition.getId() + ", Charge Name: '" + chargeDefinition.getName() + "', Due Date: " + dueDate);
        
        // First, create the charge using the original logic
        LoanCharge loanCharge = super.createNewFromJson(loan, chargeDefinition, command, dueDate);
        
        log.info("Original charge amount: {} (amount: {})", loanCharge.amountOrPercentage(), loanCharge.amount());
        System.out.println("Original charge amount: " + loanCharge.amountOrPercentage() + " (amount: " + loanCharge.amount() + ")");
        
        // Check if this is an overdue installment charge that should use our custom logic
        if (loanCharge.isOverdueInstallmentCharge() && loanCharge.getChargeCalculation().isPercentageBased()) {
            log.info("Processing overdue installment charge with custom logic");
            
            try {
                // Check if this is a RECEIVABLE LOC loan that should use discounted amount
                if (latePaymentFeeCalculationService.shouldUseDiscountedAmountForLatePaymentFee(loan)) {
                    log.info("Using discounted amount for RECEIVABLE LOC late payment fee calculation");
                    
                    // Get the installment for this charge
                    var installment = loanCharge.getOverdueInstallmentCharge().getInstallment();
                    var graceDate = org.apache.fineract.infrastructure.core.service.DateUtils.getBusinessLocalDate()
                            .minusDays(0); // Use 0 for now, penalty wait period should come from configuration
                    
                    if (org.apache.fineract.infrastructure.core.service.DateUtils.isAfter(graceDate, installment.getDueDate())) {
                        // Use discounted amount (net disbursal amount) instead of disbursed amount
                        var discountedAmount = loan.getNetDisbursalAmount();
                        log.info("Net disbursal amount: {}, Principal amount: {}", 
                                discountedAmount, loan.getPrincipal().getAmount());
                        
                        if (discountedAmount != null && discountedAmount.compareTo(BigDecimal.ZERO) > 0) {
                            // Calculate the new amount based on discounted amount
                            BigDecimal newAmount = discountedAmount.multiply(loanCharge.getPercentage())
                                    .divide(BigDecimal.valueOf(100), 2, 
                                            java.math.RoundingMode.HALF_UP);
                            
                            log.info("Recalculating charge amount: {} * {}% = {}", 
                                    discountedAmount, loanCharge.getPercentage(), newAmount);
                            
                            // Update the charge with the new amount using the proper method for percentage-based charges
                            loanCharge.update(loanCharge.getPercentage(), dueDate, discountedAmount, null, newAmount);
                            log.info("Updated charge amount to: {}", loanCharge.amount());
                        }
                    }
                }
                
                // Check if this is a PAYABLE LOC loan
                else if (latePaymentFeeCalculationService.isPayableLocLoan(loan)) {
                    log.info("Using LOC late payment fee for PAYABLE LOC loan");
                    
                    // Get the late payment fee from LOC table
                    BigDecimal locLatePaymentFee = latePaymentFeeCalculationService.getLocLatePaymentFee(loan);
                    log.info("LOC late payment fee from table: {}", locLatePaymentFee);
                    
                    if (locLatePaymentFee != null && locLatePaymentFee.compareTo(BigDecimal.ZERO) > 0) {
                        log.info("Using LOC late payment fee: {}", locLatePaymentFee);
                        
                        // For PAYABLE LOC, we need to convert the flat fee to a percentage-based charge
                        // First, get the amount the percentage should be applied to
                        BigDecimal amountToApplyTo = loan.getPrincipal().getAmount();
                        BigDecimal feePercentage = locLatePaymentFee.multiply(BigDecimal.valueOf(100)).divide(amountToApplyTo, 6, java.math.RoundingMode.HALF_UP);
                        
                        // Update the charge with the LOC late payment fee using the proper method for percentage-based charges
                        loanCharge.update(feePercentage, dueDate, amountToApplyTo, null, locLatePaymentFee);
                        log.info("Updated charge amount to LOC fee: {} (calculated as {}% of {})", locLatePaymentFee, feePercentage, amountToApplyTo);
                    }
                }
                
                log.info("Final charge amount: {} (amount: {})", loanCharge.amountOrPercentage(), loanCharge.amount());
                
            } catch (Exception e) {
                log.error("Error applying custom logic to loan charge: {}", e.getMessage(), e);
                // Keep the original amount if there's an error
            }
        }
        
        log.info("=== END CREATING LOAN CHARGE ===");
        return loanCharge;
    }
}
