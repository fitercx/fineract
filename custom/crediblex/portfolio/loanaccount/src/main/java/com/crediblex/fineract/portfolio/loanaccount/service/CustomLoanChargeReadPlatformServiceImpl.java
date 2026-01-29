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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loanaccount.repository.CustomLoanChargeRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.charge.service.ChargeDropdownReadPlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeEnumerations;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.data.LoanInstallmentChargeData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanInstallmentCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRelation;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeReadPlatformServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanChargeReadPlatformServiceImpl extends LoanChargeReadPlatformServiceImpl {

    private final CustomLoanChargeRepository loanChargeRepository;

    public CustomLoanChargeReadPlatformServiceImpl(JdbcTemplate jdbcTemplate,
            ChargeDropdownReadPlatformService chargeDropdownReadPlatformService, DropdownReadPlatformService dropdownReadPlatformService,
            LoanChargeRepository loanChargeRepository, CustomLoanChargeRepository customLoanChargeRepository) {
        super(jdbcTemplate, chargeDropdownReadPlatformService, dropdownReadPlatformService, loanChargeRepository);
        this.loanChargeRepository = customLoanChargeRepository;
    }

    @Override
    public Collection<LoanChargeData> retrieveLoanCharges(final Long loanId) {
        // Use findAllByLoanIdWithOrder to include inactive charges (for reversed charges display)
        List<LoanCharge> loanCharges = loanChargeRepository.findAllByLoanIdWithOrder(loanId);

        List<LoanChargeData> result = new ArrayList<>(loanCharges.size());
        for (LoanCharge lc : loanCharges) {
            boolean isReversedCharge = isReversedCharge(lc);
            if (!lc.isActive() && !isReversedCharge) {
                continue;
            }

            BigDecimal availableForAdjustment = calculateAvailableAmountForChargeAdjustment(lc);

            EnumOptionData chargeTimeTypeData = ChargeEnumerations.chargeTimeType(lc.getChargeTimeType().getValue());
            EnumOptionData chargeCalculationTypeData = ChargeEnumerations.chargeCalculationType(lc.getChargeCalculation().getValue());
            EnumOptionData chargePaymentModeData = ChargeEnumerations.chargePaymentMode(lc.getChargePaymentMode().getValue());

            List<LoanInstallmentChargeData> loanInstallmentChargeDataList = lc.installmentCharges().stream()
                    .map(LoanInstallmentCharge::toData).toList();

            BigDecimal amountPaid = lc.getAmountPaid();
            BigDecimal amountWaived = lc.getAmountWaived();
            BigDecimal amountWrittenOff = lc.getAmountWrittenOff();
            BigDecimal amountOutstanding = lc.getAmountOutstanding();
            boolean paid = lc.isPaid();
            boolean waived = lc.isWaived();

            if (isReversedCharge) {
                // Calculate the original paid amount from the CHARGE_ADJUSTMENT transaction
                BigDecimal originalAmountPaid = calculateOriginalPaidAmount(lc);
                // Preserve the original amount for display purposes (will be shown in red in UI)
                amountPaid = originalAmountPaid;
                amountWaived = BigDecimal.ZERO;
                amountWrittenOff = BigDecimal.ZERO;
                amountOutstanding = BigDecimal.ZERO;
                paid = false;
                waived = false;
            }

            LoanChargeData data = new LoanChargeData(lc.getId(), lc.getCharge().getId(), lc.getCharge().getName(),
                    lc.getCharge().toData().getCurrency(), lc.amount(), amountPaid, amountWaived, amountWrittenOff, amountOutstanding,
                    chargeTimeTypeData, lc.getSubmittedOnDate(), lc.getDueDate(), chargeCalculationTypeData, lc.getPercentage(),
                    lc.getAmountPercentageAppliedTo(), lc.isPenaltyCharge(), chargePaymentModeData, paid, waived, lc.getLoan().getId(),
                    lc.getLoan().getExternalId(), lc.getMinCap(), lc.getMaxCap(), lc.getAmountOrPercentage(), loanInstallmentChargeDataList,
                    lc.getExternalId());
            result.add(data);
        }
        return result;
    }

    private static BigDecimal calculateAvailableAmountForChargeAdjustment(final LoanCharge loanCharge) {
        BigDecimal availableAmountForAdjustment = loanCharge.amount();
        for (LoanTransaction loanTransaction : loanCharge.getLoan().getLoanTransactions()) {
            if (loanTransaction.isNotReversed() && loanTransaction.getTypeOf().isChargeAdjustment()) {
                for (LoanTransactionRelation loanTransactionRelation : loanTransaction.getLoanTransactionRelations()) {
                    if (loanTransactionRelation.getToCharge() != null && loanCharge.equals(loanTransactionRelation.getToCharge())) {
                        availableAmountForAdjustment = availableAmountForAdjustment.subtract(loanTransaction.getAmount());
                    }
                }
            }
        }
        return availableAmountForAdjustment;
    }

    private static boolean isReversedCharge(final LoanCharge loanCharge) {
        if (loanCharge.isActive()) {
            return false;
        }
        for (LoanTransaction loanTransaction : loanCharge.getLoan().getLoanTransactions()) {
            if (loanTransaction.isNotReversed() && loanTransaction.getTypeOf().isChargeAdjustment()) {
                if (loanTransaction.getLoanTransactionRelations() != null) {
                    for (LoanTransactionRelation relation : loanTransaction.getLoanTransactionRelations()) {
                        if (relation.getToCharge() != null && loanCharge.equals(relation.getToCharge())) {
                            return true;
                        }
                    }
                }
                if (loanTransaction.getLoanChargesPaid() != null) {
                    for (LoanChargePaidBy chargePaidBy : loanTransaction.getLoanChargesPaid()) {
                        if (chargePaidBy.getLoanCharge() != null && loanCharge.equals(chargePaidBy.getLoanCharge())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Calculates the original paid amount for a reversed charge by finding the CHARGE_ADJUSTMENT transaction and
     * getting the amount from LoanChargePaidBy.
     */
    private static BigDecimal calculateOriginalPaidAmount(final LoanCharge loanCharge) {
        for (LoanTransaction loanTransaction : loanCharge.getLoan().getLoanTransactions()) {
            if (loanTransaction.isNotReversed() && loanTransaction.getTypeOf().isChargeAdjustment()) {
                if (loanTransaction.getLoanChargesPaid() != null) {
                    for (LoanChargePaidBy chargePaidBy : loanTransaction.getLoanChargesPaid()) {
                        if (chargePaidBy.getLoanCharge() != null && loanCharge.equals(chargePaidBy.getLoanCharge())) {
                            // Return the absolute value (the original amount that was reversed)
                            return chargePaidBy.getAmount().abs();
                        }
                    }
                }
            }
        }
        // Fallback: if no CHARGE_ADJUSTMENT found, return zero
        return BigDecimal.ZERO;
    }
}
