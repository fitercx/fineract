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

package com.crediblex.fineract.portfolio.loc.domain;

import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.portfolio.client.domain.Client;

@Entity
@Table(name = "m_line_of_credit")
@Getter
@Setter
public class LineOfCredit extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "product_type", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private LocProductType productType;

    @Column(name = "maximum_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal maximumAmount;

    @Column(name = "available_balance", precision = 19, scale = 6, nullable = false)
    private BigDecimal availableBalance;

    @Column(name = "consumed_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal consumedAmount;

    @Column(name = "activation_status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private LocStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // New fields from migration
    @Column(name = "approved_credit_facility_amount", precision = 19, scale = 6, nullable = true)
    private BigDecimal approvedCreditFacilityAmount;

    @Column(name = "external_id", length = 100, nullable = true)
    private String externalId;

    @Column(name = "activation_date", nullable = true)
    private LocalDate activationDate;

    @Column(name = "currency", length = 10, nullable = true)
    private String currency;

    @Column(name = "advance_percentage", precision = 5, scale = 2, nullable = true)
    private BigDecimal advancePercentage;

    @Column(name = "tenor_days", nullable = true)
    private Integer tenorDays;

    @Column(name = "approved_buyers", columnDefinition = "TEXT", nullable = true)
    private String approvedBuyers;

    @Column(name = "processing_fee_pct_loc", precision = 5, scale = 2, nullable = true)
    private BigDecimal processingFeePctLoc;

    @Column(name = "cash_margin_type", length = 50, nullable = true)
    private String cashMarginType;

    @Column(name = "cash_margin_value", precision = 19, scale = 6, nullable = true)
    private BigDecimal cashMarginValue;

    @Column(name = "inv_handling_fee_basis", length = 50, nullable = true)
    private String invHandlingFeeBasis;

    @Column(name = "inv_handling_fee_pct", precision = 5, scale = 2, nullable = true)
    private BigDecimal invHandlingFeePct;

    @Column(name = "inv_handling_fee_min_amount", precision = 19, scale = 6, nullable = true)
    private BigDecimal invHandlingFeeMinAmount;

    @Column(name = "inv_handling_fee_currency", length = 10, nullable = true)
    private String invHandlingFeeCurrency;

    @Column(name = "interim_review_date", nullable = true)
    private LocalDate interimReviewDate;

    @Column(name = "rate_type", length = 50, nullable = true)
    private String rateType;

    @Column(name = "annual_interest_rate", precision = 5, scale = 2, nullable = true)
    private BigDecimal annualInterestRate;

    @Column(name = "is_interest_upfront_or_post_disbursal", length = 20, nullable = true)
    private String isInterestUpfrontOrPostDisbursal;

    @Column(name = "client_company_name", length = 255, nullable = true)
    private String clientCompanyName;

    @Column(name = "client_contact_person_name", length = 255, nullable = true)
    private String clientContactPersonName;

    @Column(name = "client_contact_person_phone", length = 50, nullable = true)
    private String clientContactPersonPhone;

    @Column(name = "client_contact_person_email", length = 255, nullable = true)
    private String clientContactPersonEmail;

    @Column(name = "authorized_signatory_name", length = 255, nullable = true)
    private String authorizedSignatoryName;

    @Column(name = "authorized_signatory_phone", length = 50, nullable = true)
    private String authorizedSignatoryPhone;

    @Column(name = "authorized_signatory_email", length = 255, nullable = true)
    private String authorizedSignatoryEmail;

    @Column(name = "va", length = 255, nullable = true)
    private String va;

    @Column(name = "distribution_partner", length = 255, nullable = true)
    private String distributionPartner;

    @Column(name = "bank_transfer_fee", precision = 19, scale = 6, nullable = true)
    private BigDecimal bankTransferFee;

    @Column(name = "special_conditions", columnDefinition = "TEXT", nullable = true)
    private String specialConditions;

    @Column(name = "late_payment_fee", precision = 19, scale = 6, nullable = true)
    private BigDecimal latePaymentFee;

    /**
     * Default constructor.
     */
    protected LineOfCredit() {}

    /**
     * Constructor for creating a new Line of Credit.
     */
    public LineOfCredit(Client client, String name, String productType, BigDecimal maximumAmount, LocalDate startDate, LocalDate endDate) {
        this.client = client;
        this.name = name;
        this.productType = LocProductType.valueOf(productType);
        this.maximumAmount = maximumAmount;
        this.availableBalance = maximumAmount;
        this.consumedAmount = BigDecimal.ZERO;
        this.status = LocStatus.INACTIVE;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Constructor for creating a new Line of Credit with all fields.
     */
    public LineOfCredit(Client client, String name, String productType, BigDecimal maximumAmount, LocalDate startDate, LocalDate endDate,
            BigDecimal approvedCreditFacilityAmount, String externalId, LocalDate activationDate, String currency, BigDecimal advancePercentage,
            Integer tenorDays, String approvedBuyers, BigDecimal processingFeePctLoc, String cashMarginType, BigDecimal cashMarginValue,
            String invHandlingFeeBasis, BigDecimal invHandlingFeePct, BigDecimal invHandlingFeeMinAmount, String invHandlingFeeCurrency,
            LocalDate interimReviewDate, String rateType, BigDecimal annualInterestRate, String isInterestUpfrontOrPostDisbursal,
            String clientCompanyName, String clientContactPersonName, String clientContactPersonPhone, String clientContactPersonEmail,
            String authorizedSignatoryName, String authorizedSignatoryPhone, String authorizedSignatoryEmail, String va,
            String distributionPartner, BigDecimal bankTransferFee, String specialConditions, BigDecimal latePaymentFee) {
        this.client = client;
        this.name = name;
        this.productType = LocProductType.valueOf(productType);
        this.maximumAmount = maximumAmount;
        this.availableBalance = maximumAmount;
        this.consumedAmount = BigDecimal.ZERO;
        this.status = LocStatus.INACTIVE;
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
    }

    /**
     * Activate the line of credit.
     */
    public void activate() {
        this.status = LocStatus.ACTIVE;
    }

    /**
     * Deactivate the line of credit.
     */
    public void deactivate() {
        this.status = LocStatus.INACTIVE;
    }

    /**
     * Update the line of credit with changes from command.
     */
    public Map<String, Object> update(JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>();

        if (command.isChangeInStringParameterNamed("name", this.name)) {
            final String newValue = command.stringValueOfParameterNamed("name");
            actualChanges.put("name", newValue);
            this.name = newValue;
        }

        if (command.isChangeInStringParameterNamed("productType", this.productType.name())) {
            final String newValue = command.stringValueOfParameterNamed("productType");
            actualChanges.put("productType", newValue);
            this.productType = LocProductType.valueOf(newValue);
        }

        if (command.isChangeInBigDecimalParameterNamed("maximumAmount", this.maximumAmount)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("maximumAmount");
            actualChanges.put("maximumAmount", newValue);
            this.maximumAmount = newValue;
        }

        if (command.isChangeInLocalDateParameterNamed("startDate", this.startDate)) {
            final LocalDate newValue = command.localDateValueOfParameterNamed("startDate");
            actualChanges.put("startDate", newValue);
            this.startDate = newValue;
        }

        if (command.isChangeInLocalDateParameterNamed("endDate", this.endDate)) {
            final LocalDate newValue = command.localDateValueOfParameterNamed("endDate");
            actualChanges.put("endDate", newValue);
            this.endDate = newValue;
        }

        // New fields update logic
        if (command.isChangeInBigDecimalParameterNamed("approvedCreditFacilityAmount", this.approvedCreditFacilityAmount)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("approvedCreditFacilityAmount");
            actualChanges.put("approvedCreditFacilityAmount", newValue);
            this.approvedCreditFacilityAmount = newValue;
        }

        if (command.isChangeInStringParameterNamed("externalId", this.externalId)) {
            final String newValue = command.stringValueOfParameterNamed("externalId");
            actualChanges.put("externalId", newValue);
            this.externalId = newValue;
        }

        if (command.isChangeInLocalDateParameterNamed("activationDate", this.activationDate)) {
            final LocalDate newValue = command.localDateValueOfParameterNamed("activationDate");
            actualChanges.put("activationDate", newValue);
            this.activationDate = newValue;
        }

        if (command.isChangeInStringParameterNamed("currency", this.currency)) {
            final String newValue = command.stringValueOfParameterNamed("currency");
            actualChanges.put("currency", newValue);
            this.currency = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("advancePercentage", this.advancePercentage)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("advancePercentage");
            actualChanges.put("advancePercentage", newValue);
            this.advancePercentage = newValue;
        }

        if (command.isChangeInIntegerParameterNamed("tenorDays", this.tenorDays)) {
            final Integer newValue = command.integerValueOfParameterNamed("tenorDays");
            actualChanges.put("tenorDays", newValue);
            this.tenorDays = newValue;
        }

        if (command.isChangeInStringParameterNamed("approvedBuyers", this.approvedBuyers)) {
            final String newValue = command.stringValueOfParameterNamed("approvedBuyers");
            actualChanges.put("approvedBuyers", newValue);
            this.approvedBuyers = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("processingFeePctLoc", this.processingFeePctLoc)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("processingFeePctLoc");
            actualChanges.put("processingFeePctLoc", newValue);
            this.processingFeePctLoc = newValue;
        }

        if (command.isChangeInStringParameterNamed("cashMarginType", this.cashMarginType)) {
            final String newValue = command.stringValueOfParameterNamed("cashMarginType");
            actualChanges.put("cashMarginType", newValue);
            this.cashMarginType = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("cashMarginValue", this.cashMarginValue)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("cashMarginValue");
            actualChanges.put("cashMarginValue", newValue);
            this.cashMarginValue = newValue;
        }

        if (command.isChangeInStringParameterNamed("invHandlingFeeBasis", this.invHandlingFeeBasis)) {
            final String newValue = command.stringValueOfParameterNamed("invHandlingFeeBasis");
            actualChanges.put("invHandlingFeeBasis", newValue);
            this.invHandlingFeeBasis = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("invHandlingFeePct", this.invHandlingFeePct)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("invHandlingFeePct");
            actualChanges.put("invHandlingFeePct", newValue);
            this.invHandlingFeePct = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("invHandlingFeeMinAmount", this.invHandlingFeeMinAmount)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("invHandlingFeeMinAmount");
            actualChanges.put("invHandlingFeeMinAmount", newValue);
            this.invHandlingFeeMinAmount = newValue;
        }

        if (command.isChangeInStringParameterNamed("invHandlingFeeCurrency", this.invHandlingFeeCurrency)) {
            final String newValue = command.stringValueOfParameterNamed("invHandlingFeeCurrency");
            actualChanges.put("invHandlingFeeCurrency", newValue);
            this.invHandlingFeeCurrency = newValue;
        }

        if (command.isChangeInLocalDateParameterNamed("interimReviewDate", this.interimReviewDate)) {
            final LocalDate newValue = command.localDateValueOfParameterNamed("interimReviewDate");
            actualChanges.put("interimReviewDate", newValue);
            this.interimReviewDate = newValue;
        }

        if (command.isChangeInStringParameterNamed("rateType", this.rateType)) {
            final String newValue = command.stringValueOfParameterNamed("rateType");
            actualChanges.put("rateType", newValue);
            this.rateType = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("annualInterestRate", this.annualInterestRate)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("annualInterestRate");
            actualChanges.put("annualInterestRate", newValue);
            this.annualInterestRate = newValue;
        }

        if (command.isChangeInStringParameterNamed("isInterestUpfrontOrPostDisbursal", this.isInterestUpfrontOrPostDisbursal)) {
            final String newValue = command.stringValueOfParameterNamed("isInterestUpfrontOrPostDisbursal");
            actualChanges.put("isInterestUpfrontOrPostDisbursal", newValue);
            this.isInterestUpfrontOrPostDisbursal = newValue;
        }

        if (command.isChangeInStringParameterNamed("clientCompanyName", this.clientCompanyName)) {
            final String newValue = command.stringValueOfParameterNamed("clientCompanyName");
            actualChanges.put("clientCompanyName", newValue);
            this.clientCompanyName = newValue;
        }

        if (command.isChangeInStringParameterNamed("clientContactPersonName", this.clientContactPersonName)) {
            final String newValue = command.stringValueOfParameterNamed("clientContactPersonName");
            actualChanges.put("clientContactPersonName", newValue);
            this.clientContactPersonName = newValue;
        }

        if (command.isChangeInStringParameterNamed("clientContactPersonPhone", this.clientContactPersonPhone)) {
            final String newValue = command.stringValueOfParameterNamed("clientContactPersonPhone");
            actualChanges.put("clientContactPersonPhone", newValue);
            this.clientContactPersonPhone = newValue;
        }

        if (command.isChangeInStringParameterNamed("clientContactPersonEmail", this.clientContactPersonEmail)) {
            final String newValue = command.stringValueOfParameterNamed("clientContactPersonEmail");
            actualChanges.put("clientContactPersonEmail", newValue);
            this.clientContactPersonEmail = newValue;
        }

        if (command.isChangeInStringParameterNamed("authorizedSignatoryName", this.authorizedSignatoryName)) {
            final String newValue = command.stringValueOfParameterNamed("authorizedSignatoryName");
            actualChanges.put("authorizedSignatoryName", newValue);
            this.authorizedSignatoryName = newValue;
        }

        if (command.isChangeInStringParameterNamed("authorizedSignatoryPhone", this.authorizedSignatoryPhone)) {
            final String newValue = command.stringValueOfParameterNamed("authorizedSignatoryPhone");
            actualChanges.put("authorizedSignatoryPhone", newValue);
            this.authorizedSignatoryPhone = newValue;
        }

        if (command.isChangeInStringParameterNamed("authorizedSignatoryEmail", this.authorizedSignatoryEmail)) {
            final String newValue = command.stringValueOfParameterNamed("authorizedSignatoryEmail");
            actualChanges.put("authorizedSignatoryEmail", newValue);
            this.authorizedSignatoryEmail = newValue;
        }

        if (command.isChangeInStringParameterNamed("va", this.va)) {
            final String newValue = command.stringValueOfParameterNamed("va");
            actualChanges.put("va", newValue);
            this.va = newValue;
        }

        if (command.isChangeInStringParameterNamed("distributionPartner", this.distributionPartner)) {
            final String newValue = command.stringValueOfParameterNamed("distributionPartner");
            actualChanges.put("distributionPartner", newValue);
            this.distributionPartner = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("bankTransferFee", this.bankTransferFee)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("bankTransferFee");
            actualChanges.put("bankTransferFee", newValue);
            this.bankTransferFee = newValue;
        }

        if (command.isChangeInStringParameterNamed("specialConditions", this.specialConditions)) {
            final String newValue = command.stringValueOfParameterNamed("specialConditions");
            actualChanges.put("specialConditions", newValue);
            this.specialConditions = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("latePaymentFee", this.latePaymentFee)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("latePaymentFee");
            actualChanges.put("latePaymentFee", newValue);
            this.latePaymentFee = newValue;
        }

        return actualChanges;
    }


}
