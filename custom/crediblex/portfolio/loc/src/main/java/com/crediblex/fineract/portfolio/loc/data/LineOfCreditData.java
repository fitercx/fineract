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

import com.crediblex.fineract.portfolio.loc.charge.data.LocChargeData;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.organisation.staff.data.StaffData;
import org.apache.fineract.portfolio.client.data.ClientData;

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

    private BigDecimal approvedCreditFacilityAmount;
    private String externalId;
    private String currency;
    private BigDecimal advancePercentage;
    private Integer tenorDays;

    private String approvedBuyers;
    private List<String> approvedBuyersList;

    private String cashMarginType;
    private BigDecimal cashMarginValue;
    private LocalDate interimReviewDate;
    private String rateType; // kept (enum name)
    private String interestChargeTime; // new enum name field
    private BigDecimal annualInterestRate;

    private String clientCompanyName;
    private String clientContactPersonName;
    private String clientContactPersonPhone;
    private String clientContactPersonEmail;
    private String authorizedSignatoryName;
    private String authorizedSignatoryPhone;
    private String authorizedSignatoryEmail;

    private String va;
    private String distributionPartner;
    private String specialConditions;

    private Integer reviewPeriod; // changed from String to Integer

    private Long loanOfficerId; // new
    private String loanOfficerName; // resolved display name

    private Long settlementSavingsAccountId;
    private String settlementSavingsAccountNo;
    private BigDecimal settlementSavingsAccountBalance;

    private Collection<EnumOptionData> statusOptions;
    private Collection<EnumOptionData> productTypeOptions;
    private Collection<EnumOptionData> reviewPeriodsOptions;
    private Collection<EnumOptionData> cashMarginTypeOptions;
    private Collection<EnumOptionData> interestChargeTimeOptions;

    private Collection<LocChargeData> charges;

    private transient Integer rowIndex;
    private String dateFormat;
    private String locale;

    Collection<StaffData> loanOfficers;

    private LineOfCreditTimeLineData timeLineData;

}
