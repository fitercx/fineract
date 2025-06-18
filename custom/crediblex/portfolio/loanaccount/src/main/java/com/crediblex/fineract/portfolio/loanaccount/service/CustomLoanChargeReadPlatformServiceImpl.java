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
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.LoanChargeData;
import org.apache.fineract.portfolio.loanaccount.data.LoanInstallmentChargeData;
import org.apache.fineract.portfolio.loanaccount.domain.*;
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
        List<LoanCharge> loanCharges = loanChargeRepository.findActiveByLoanIdWithOrder(loanId);

        List<LoanChargeData> result = new ArrayList<>(loanCharges.size());
        for (LoanCharge lc : loanCharges) {
            BigDecimal availableForAdjustment = calculateAvailableAmountForChargeAdjustment(lc);

            // Gather all required fields from LoanCharge and related entities
            EnumOptionData chargeTimeTypeData = new EnumOptionData((long) lc.getChargeTimeType().ordinal(),
                    lc.getChargeTimeType().getCode(), String.valueOf(lc.getChargeTimeType().getValue()));
            EnumOptionData chargeCalculationTypeData = new EnumOptionData((long) lc.getChargeCalculation().ordinal(),
                    lc.getChargeCalculation().getCode(), String.valueOf(lc.getChargeCalculation().getValue()));
            EnumOptionData chargePaymentModeData = new EnumOptionData((long) lc.getChargePaymentMode().ordinal(),
                    lc.getChargePaymentMode().getCode(), String.valueOf(lc.getChargePaymentMode().getValue()));
            List<LoanInstallmentChargeData> loanInstallmentChargeDataList = lc.installmentCharges().stream()
                    .map(LoanInstallmentCharge::toData).toList();

            LoanChargeData data = new LoanChargeData(lc.getId(), lc.getCharge().getId(), lc.getCharge().getName(),
                    lc.getCharge().toData().getCurrency(), lc.amount(), lc.getAmountPaid(), lc.getAmountWaived(), lc.getAmountWrittenOff(),
                    availableForAdjustment, chargeTimeTypeData, lc.getSubmittedOnDate(), lc.getDueDate(), chargeCalculationTypeData,
                    lc.getPercentage(), lc.getAmountPercentageAppliedTo(), lc.isPenaltyCharge(), chargePaymentModeData, lc.isPaid(),
                    lc.isWaived(), lc.getLoan().getId(), lc.getLoan().getExternalId(), lc.getMinCap(), lc.getMaxCap(),
                    lc.getAmountOrPercentage(), loanInstallmentChargeDataList, lc.getExternalId());
            result.add(data);
        }
        return result;
    }

    private static BigDecimal calculateAvailableAmountForChargeAdjustment(final LoanCharge loanCharge) {
        BigDecimal availableAmountForAdjustment = loanCharge.amount();
        for (LoanTransaction loanTransaction : loanCharge.getLoan().getLoanTransactions()) {
            if (loanTransaction.isNotReversed() && loanTransaction.getTypeOf().isChargeAdjustment()) {
                LoanTransactionRelation loanTransactionRelation = loanTransaction.getLoanTransactionRelations().stream()
                        .filter(e -> e.getToCharge() != null).findFirst().orElseThrow();
                if (loanCharge.equals(loanTransactionRelation.getToCharge())) {
                    availableAmountForAdjustment = availableAmountForAdjustment.subtract(loanTransaction.getAmount());
                }
            }
        }
        return availableAmountForAdjustment;
    }

}
