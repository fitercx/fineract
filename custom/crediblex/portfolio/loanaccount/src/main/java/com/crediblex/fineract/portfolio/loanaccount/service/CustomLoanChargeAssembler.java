package com.crediblex.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
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

/**
 * Custom implementation of LoanChargeAssembler that applies LOC-specific logic for late payment fee calculations when
 * creating overdue installment charges.
 */
@Component
@Slf4j
public class CustomLoanChargeAssembler extends LoanChargeAssembler {

    private final CustomLatePaymentFeeCalculationService latePaymentFeeCalculationService;

    @Autowired
    public CustomLoanChargeAssembler(FromJsonHelper fromApiJsonHelper, ChargeRepositoryWrapper chargeRepository,
            LoanChargeRepository loanChargeRepository, LoanProductRepository loanProductRepository,
            org.apache.fineract.infrastructure.core.service.ExternalIdFactory externalIdFactory,
            CustomLatePaymentFeeCalculationService latePaymentFeeCalculationService) {
        super(fromApiJsonHelper, chargeRepository, loanChargeRepository, loanProductRepository, externalIdFactory);
        this.latePaymentFeeCalculationService = latePaymentFeeCalculationService;
    }

    @Override
    public LoanCharge createNewFromJson(final Loan loan, final Charge chargeDefinition, final JsonCommand command,
            final LocalDate dueDate) {
        // First, create the charge using the original logic
        LoanCharge loanCharge = super.createNewFromJson(loan, chargeDefinition, command, dueDate);

        // Check if this is an overdue installment charge that should use our custom logic
        if (loanCharge.isOverdueInstallmentCharge() && loanCharge.getChargeCalculation().isPercentageBased()) {

            try {
                // Check if this is a RECEIVABLE LOC loan that should use discounted amount
                if (latePaymentFeeCalculationService.shouldUseDiscountedAmountForLatePaymentFee(loan)) {
                    // Get the installment for this charge
                    var installment = loanCharge.getOverdueInstallmentCharge().getInstallment();
                    var graceDate = org.apache.fineract.infrastructure.core.service.DateUtils.getBusinessLocalDate().minusDays(0); // Use
                                                                                                                                   // 0
                                                                                                                                   // for
                                                                                                                                   // now,
                                                                                                                                   // penalty
                                                                                                                                   // wait
                                                                                                                                   // period
                                                                                                                                   // should
                                                                                                                                   // come
                                                                                                                                   // from
                                                                                                                                   // configuration

                    if (org.apache.fineract.infrastructure.core.service.DateUtils.isAfter(graceDate, installment.getDueDate())) {
                        // Use discounted amount (net disbursal amount) instead of disbursed amount
                        var discountedAmount = loan.getNetDisbursalAmount();

                        if (discountedAmount != null && discountedAmount.compareTo(BigDecimal.ZERO) > 0) {
                            // Calculate the new amount based on discounted amount
                            BigDecimal newAmount = discountedAmount.multiply(loanCharge.getPercentage()).divide(BigDecimal.valueOf(100), 2,
                                    java.math.RoundingMode.HALF_UP);

                            // Update the charge with the new amount using the proper method for percentage-based
                            // charges
                            loanCharge.update(loanCharge.getPercentage(), dueDate, discountedAmount, null, newAmount);
                        }
                    }
                }

                // Check if this is a PAYABLE LOC loan
                else if (latePaymentFeeCalculationService.isPayableLocLoan(loan)) {
                    // Get the late payment fee from LOC table
                    BigDecimal locLatePaymentFee = latePaymentFeeCalculationService.getLocLatePaymentFee(loan);
                    if (locLatePaymentFee != null && locLatePaymentFee.compareTo(BigDecimal.ZERO) > 0) {

                        // For PAYABLE LOC, we need to convert the flat fee to a percentage-based charge
                        // First, get the amount the percentage should be applied to
                        BigDecimal amountToApplyTo = loan.getPrincipal().getAmount();
                        BigDecimal feePercentage = locLatePaymentFee.multiply(BigDecimal.valueOf(100)).divide(amountToApplyTo, 6,
                                java.math.RoundingMode.HALF_UP);

                        // Update the charge with the LOC late payment fee using the proper method for percentage-based
                        // charges
                        loanCharge.update(feePercentage, dueDate, amountToApplyTo, null, locLatePaymentFee);
                    }
                }
            } catch (Exception ignored) {

            }
        }

        return loanCharge;
    }
}
