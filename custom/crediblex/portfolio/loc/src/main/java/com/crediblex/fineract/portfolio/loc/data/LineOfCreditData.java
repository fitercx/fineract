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

package com.crediblex.fineract.portfolio.loc.data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.useradministration.data.AppUserData;
import com.crediblex.fineract.portfolio.loc.charge.data.LocChargeData;

/**
 * Immutable data object representing Line of Credit data.
 */

@Getter
@Setter
@Builder
public final class LineOfCreditData implements Serializable {

    private Long id;
    private String accountNumber;
    private Long clientId;
    private ClientData client;
    private String productType;
    private BigDecimal maximumAmount;
    private BigDecimal availableBalance;
    private BigDecimal consumedAmount;
    private EnumOptionData status;
    private LocalDate startDate;
    private LocalDate endDate;

    // New fields from migration
    private BigDecimal approvedCreditFacilityAmount;
    private String externalId;
    private LocalDate activationDate;
    private String currency;
    private BigDecimal advancePercentage;
    private Integer tenorDays;
    private String approvedBuyers;
    private List<String> approvedBuyersList;
    private BigDecimal processingFeePctLoc;
    private String cashMarginType;
    private BigDecimal cashMarginValue;
    private LocalDate interimReviewDate;
    private String rateType;
    private BigDecimal annualInterestRate;
    private String isInterestUpfrontOrPostDisbursal;
    private String clientCompanyName;
    private String clientContactPersonName;
    private String clientContactPersonPhone;
    private String clientContactPersonEmail;
    private String authorizedSignatoryName;
    private String authorizedSignatoryPhone;
    private String authorizedSignatoryEmail;
    private String va;
    private String distributionPartner;
    private BigDecimal bankTransferFee;
    private String specialConditions;
    private BigDecimal latePaymentFee;

    private BigDecimal maxPerDrawdown;
    private String reviewPeriod;
    private BigDecimal interestRateOverride;
    private Long settlementSavingsAccountId;
    private String settlementSavingsAccountNo;

    private BigDecimal settlementSavingsAccountBalance;

    // Audit fields
    private LocalDate createdDate;
    private String createdByUsername;
    private LocalDate lastModifiedDate;
    private String lastModifiedByUsername;

    // Template fields
    private Collection<EnumOptionData> statusOptions;
    private Collection<EnumOptionData> productTypeOptions;
    private Collection<EnumOptionData> reviewPeriodsOptions;

    // Charges
    private Collection<LocChargeData> charges;

    // Import fields
    private transient Integer rowIndex;
    private String dateFormat;
    private String locale;

    // New fields for approver, activator, and closer
    private LocalDate activatedOnDate;
    private LocalDate approvedOnDate;
    private LocalDate closedOnDate;
    private AppUserData approver;
    private AppUserData activator;
    private AppUserData closer;

    public static LineOfCreditData template(Collection<EnumOptionData> statusOptions, Collection<EnumOptionData> productTypeOptions,
                                            Collection<EnumOptionData> reviewPeriodsOptionsData) {
        return LineOfCreditData.builder().statusOptions(statusOptions)
                .productTypeOptions(productTypeOptions).reviewPeriodsOptions(reviewPeriodsOptionsData).build();
    }


}
