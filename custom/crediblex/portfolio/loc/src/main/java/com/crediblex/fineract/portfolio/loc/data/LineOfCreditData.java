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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.client.data.ClientData;

/**
 * Immutable data object representing Line of Credit data.
 */
@NoArgsConstructor
@Getter
@Setter
public final class LineOfCreditData implements Serializable {

    private Long id;
    private Long clientId;
    private ClientData client;
    private String name;
    private String productType;
    private BigDecimal maximumAmount;
    private BigDecimal availableBalance;
    private BigDecimal consumedAmount;
    private EnumOptionData activationStatus;
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
    private BigDecimal processingFeePctLoc;
    private String cashMarginType;
    private BigDecimal cashMarginValue;
    private String invHandlingFeeBasis;
    private BigDecimal invHandlingFeePct;
    private BigDecimal invHandlingFeeMinAmount;
    private String invHandlingFeeCurrency;
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

    // Audit fields
    private LocalDate createdDate;
    private String createdByUsername;
    private LocalDate lastModifiedDate;
    private String lastModifiedByUsername;

    // Template fields
    private Collection<EnumOptionData> activationStatusOptions;
    private Collection<EnumOptionData> productTypeOptions;
    private Collection<EnumOptionData> reviewPeriodsOptions;

    // Import fields
    private transient Integer rowIndex;
    private String dateFormat;
    private String locale;

    public static LineOfCreditData importInstance(Long clientId, String name, String productType, BigDecimal maximumAmount,
            LocalDate startDate, LocalDate endDate, Integer rowIndex, String dateFormat, String locale) {
        return new LineOfCreditData(clientId, name, productType, maximumAmount, startDate, endDate, rowIndex, dateFormat, locale);
    }

    public static LineOfCreditData template(Collection<EnumOptionData> activationStatusOptions, Collection<EnumOptionData> productTypeOptions,
                                            Collection<EnumOptionData> reviewPeriodsOptionsData) {
        return new LineOfCreditData(activationStatusOptions, productTypeOptions,reviewPeriodsOptionsData);
    }

    public static LineOfCreditData instance(Long id, Long clientId, ClientData client, String name, String productType,
            BigDecimal maximumAmount, BigDecimal availableBalance, BigDecimal consumedAmount, EnumOptionData activationStatus,
            LocalDate startDate, LocalDate endDate, BigDecimal approvedCreditFacilityAmount, String externalId, LocalDate activationDate,
            String currency, BigDecimal advancePercentage, Integer tenorDays, String approvedBuyers, BigDecimal processingFeePctLoc,
            String cashMarginType, BigDecimal cashMarginValue, String invHandlingFeeBasis, BigDecimal invHandlingFeePct,
            BigDecimal invHandlingFeeMinAmount, String invHandlingFeeCurrency, LocalDate interimReviewDate, String rateType,
            BigDecimal annualInterestRate, String isInterestUpfrontOrPostDisbursal, String clientCompanyName,
            String clientContactPersonName, String clientContactPersonPhone, String clientContactPersonEmail,
            String authorizedSignatoryName, String authorizedSignatoryPhone, String authorizedSignatoryEmail, String va,
            String distributionPartner, BigDecimal bankTransferFee, String specialConditions, BigDecimal latePaymentFee,
            LocalDate createdDate, String createdByUsername, LocalDate lastModifiedDate, String lastModifiedByUsername) {
        return new LineOfCreditData(id, clientId, client, name, productType, maximumAmount, availableBalance, consumedAmount,
                activationStatus, startDate, endDate, approvedCreditFacilityAmount, externalId, activationDate, currency,
                advancePercentage, tenorDays, approvedBuyers, processingFeePctLoc, cashMarginType, cashMarginValue,
                invHandlingFeeBasis, invHandlingFeePct, invHandlingFeeMinAmount, invHandlingFeeCurrency, interimReviewDate,
                rateType, annualInterestRate, isInterestUpfrontOrPostDisbursal, clientCompanyName, clientContactPersonName,
                clientContactPersonPhone, clientContactPersonEmail, authorizedSignatoryName, authorizedSignatoryPhone,
                authorizedSignatoryEmail, va, distributionPartner, bankTransferFee, specialConditions, latePaymentFee,
                createdDate, createdByUsername, lastModifiedDate, lastModifiedByUsername);
    }

    private LineOfCreditData(Long clientId, String name, String productType, BigDecimal maximumAmount, LocalDate startDate,
            LocalDate endDate, Integer rowIndex, String dateFormat, String locale) {
        this.clientId = clientId;
        this.name = name;
        this.productType = productType;
        this.maximumAmount = maximumAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.rowIndex = rowIndex;
        this.dateFormat = dateFormat;
        this.locale = locale;
    }

    private LineOfCreditData(Collection<EnumOptionData> activationStatusOptions, Collection<EnumOptionData> productTypeOptions,
                             Collection<EnumOptionData> reviewPeriodsOptionsData)  {
        this.activationStatusOptions = activationStatusOptions;
        this.productTypeOptions = productTypeOptions;
        this.reviewPeriodsOptions = reviewPeriodsOptionsData;
    }

    private LineOfCreditData(Long id, Long clientId, ClientData client, String name, String productType, BigDecimal maximumAmount,
            BigDecimal availableBalance, BigDecimal consumedAmount, EnumOptionData activationStatus, LocalDate startDate, LocalDate endDate,
            BigDecimal approvedCreditFacilityAmount, String externalId, LocalDate activationDate, String currency, BigDecimal advancePercentage,
            Integer tenorDays, String approvedBuyers, BigDecimal processingFeePctLoc, String cashMarginType, BigDecimal cashMarginValue,
            String invHandlingFeeBasis, BigDecimal invHandlingFeePct, BigDecimal invHandlingFeeMinAmount, String invHandlingFeeCurrency,
            LocalDate interimReviewDate, String rateType, BigDecimal annualInterestRate, String isInterestUpfrontOrPostDisbursal,
            String clientCompanyName, String clientContactPersonName, String clientContactPersonPhone, String clientContactPersonEmail,
            String authorizedSignatoryName, String authorizedSignatoryPhone, String authorizedSignatoryEmail, String va,
            String distributionPartner, BigDecimal bankTransferFee, String specialConditions, BigDecimal latePaymentFee,
            LocalDate createdDate, String createdByUsername, LocalDate lastModifiedDate, String lastModifiedByUsername) {
        this.id = id;
        this.clientId = clientId;
        this.client = client;
        this.name = name;
        this.productType = productType;
        this.maximumAmount = maximumAmount;
        this.availableBalance = availableBalance;
        this.consumedAmount = consumedAmount;
        this.activationStatus = activationStatus;
        this.startDate = startDate;
        this.endDate = endDate;
        this.approvedCreditFacilityAmount = approvedCreditFacilityAmount;
        this.externalId = externalId;
        this.activationDate = activationDate;
        this.currency = currency;
        this.advancePercentage = advancePercentage;
        this.tenorDays = tenorDays;
        this.approvedBuyers = approvedBuyers;
        this.processingFeePctLoc = processingFeePctLoc;
        this.cashMarginType = cashMarginType;
        this.cashMarginValue = cashMarginValue;
        this.invHandlingFeeBasis = invHandlingFeeBasis;
        this.invHandlingFeePct = invHandlingFeePct;
        this.invHandlingFeeMinAmount = invHandlingFeeMinAmount;
        this.invHandlingFeeCurrency = invHandlingFeeCurrency;
        this.interimReviewDate = interimReviewDate;
        this.rateType = rateType;
        this.annualInterestRate = annualInterestRate;
        this.isInterestUpfrontOrPostDisbursal = isInterestUpfrontOrPostDisbursal;
        this.clientCompanyName = clientCompanyName;
        this.clientContactPersonName = clientContactPersonName;
        this.clientContactPersonPhone = clientContactPersonPhone;
        this.clientContactPersonEmail = clientContactPersonEmail;
        this.authorizedSignatoryName = authorizedSignatoryName;
        this.authorizedSignatoryPhone = authorizedSignatoryPhone;
        this.authorizedSignatoryEmail = authorizedSignatoryEmail;
        this.va = va;
        this.distributionPartner = distributionPartner;
        this.bankTransferFee = bankTransferFee;
        this.specialConditions = specialConditions;
        this.latePaymentFee = latePaymentFee;
        this.createdDate = createdDate;
        this.createdByUsername = createdByUsername;
        this.lastModifiedDate = lastModifiedDate;
        this.lastModifiedByUsername = lastModifiedByUsername;
    }
}
