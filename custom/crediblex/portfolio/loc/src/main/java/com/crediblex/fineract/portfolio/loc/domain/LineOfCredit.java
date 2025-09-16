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

import com.crediblex.fineract.portfolio.loc.charge.domain.LineOfCreditCharge;
import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;

@Entity
@Table(name = "m_line_of_credit")
@Getter
@Setter
public class LineOfCredit extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "product_type", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private LocProductType productType;

    @Column(name = "maximum_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal maximumAmount;

    @Column(name = "activation_status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private LocStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // New fields from migration
    @Column(name = "approved_credit_facility_amount", precision = 19, scale = 6)
    private BigDecimal approvedCreditFacilityAmount;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "advance_percentage", precision = 5, scale = 2)
    private BigDecimal advancePercentage;

    @Column(name = "tenor_days")
    private Integer tenorDays;

    @Column(name = "cash_margin_type", length = 50)
    private String cashMarginType;

    @Column(name = "cash_margin_value", precision = 19, scale = 6)
    private BigDecimal cashMarginValue;

    @Column(name = "interim_review_date")
    private LocalDate interimReviewDate;

    @Column(name = "rate_type", length = 50)
    private String rateType;

    @Column(name = "annual_interest_rate", precision = 5, scale = 2)
    private BigDecimal annualInterestRate;

    @Column(name = "is_interest_upfront_or_post_disbursal", length = 20)
    private Boolean isInterestUpfrontOrPostDisbursal;



    @Column(name = "va")
    private String virtualAccount;

    @Column(name = "special_conditions", columnDefinition = "TEXT")
    private String specialConditions;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_savings_account_id")
    private SavingsAccount settlementSavingsAccount;

    @OneToMany(mappedBy = "lineOfCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineOfCreditCharge> charges = new ArrayList<>();

    @OneToMany(mappedBy = "lineOfCredit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LineOfCreditApprovedBuyers> approvedBuyers = new ArrayList<>();
    
    @Embedded
    private LineOfCreditSummary summary;
    
    @Embedded
    private LineOfCreditStateChange lineOfCreditStateChange;

    @Embedded
    private LineOfCreditClientOptionalInfo lineOfCreditClientOptionalInfo;

    public void replaceCharges(List<LineOfCreditCharge> newCharges) {
        if (this.charges == null) { this.charges = new ArrayList<>(); }
        this.charges.clear();
        if (newCharges != null) {
            for (LineOfCreditCharge charge : newCharges) {
                charge.setLineOfCredit(this);
                this.charges.add(charge);
            }
        }
    }

    public LineOfCreditStateChange getLineOfCreditStateChange(){
        if(this.lineOfCreditStateChange == null){
            this.lineOfCreditStateChange = new LineOfCreditStateChange();
        }
        return this.lineOfCreditStateChange;
    }

    public void replaceApprovedBuyers(List<LineOfCreditApprovedBuyers> newApprovedBuyers) {
        if (this.approvedBuyers == null) {
            this.approvedBuyers = new ArrayList<>();
        }
        this.approvedBuyers.clear();
        if (newApprovedBuyers != null) {
            for (LineOfCreditApprovedBuyers buyer : newApprovedBuyers) {
                buyer.setLineOfCredit(this);
                this.approvedBuyers.add(buyer);
            }
        }
    }

    /**
     * Default constructor.
     */
    protected LineOfCredit() {}

    /**
     * Constructor for creating a new Line of Credit with all fields.
     */
    public LineOfCredit(Client client, String productType, BigDecimal maximumAmount, LocalDate startDate, LocalDate endDate,
            BigDecimal approvedCreditFacilityAmount, String externalId, String currency, BigDecimal advancePercentage,
            Integer tenorDays, String cashMarginType, BigDecimal cashMarginValue,
            LocalDate interimReviewDate, String rateType, BigDecimal annualInterestRate,
                        Boolean isInterestUpfrontOrPostDisbursal,String virtualAccount, String specialConditions,LineOfCreditClientOptionalInfo locOptionalClientInfo,
                        List<LineOfCreditApprovedBuyers>  approvedBuyers) {

        this.client = client;
        this.productType = LocProductType.valueOf(productType.toUpperCase(Locale.ENGLISH));
        this.maximumAmount = maximumAmount;
        this.status = LocStatus.SUBMITTED;
        this.startDate = startDate;
        this.endDate = endDate;
        this.approvedCreditFacilityAmount = approvedCreditFacilityAmount;
        this.externalId = externalId;
        this.currency = currency;
        this.advancePercentage = advancePercentage;
        this.tenorDays = tenorDays;
        this.cashMarginType = cashMarginType;
        this.cashMarginValue = cashMarginValue;
        this.interimReviewDate = interimReviewDate;
        this.rateType = rateType;
        this.annualInterestRate = annualInterestRate;
        this.isInterestUpfrontOrPostDisbursal = isInterestUpfrontOrPostDisbursal;
        this.virtualAccount = virtualAccount;
        this.specialConditions = specialConditions;
        this.lineOfCreditClientOptionalInfo = locOptionalClientInfo;
        // Use the helper method to properly establish bidirectional relationship
        this.replaceApprovedBuyers(approvedBuyers);

        this.summary = LineOfCreditSummary.getInitialState();
        this.summary.setAvailableBalance(maximumAmount);
    }


    /**
     * Deactivate the line of credit.
     */
    public void deactivate() {
        this.status = LocStatus.INACTIVE;
    }

    /**
     * Mark the line of credit as submitted.
     */
    public void markSubmitted() {
        this.status = LocStatus.SUBMITTED;
    }

    /**
     * Check if the line of credit can be activated.
     */
    public boolean canActivate() {
        return this.status == LocStatus.APPROVED;
    }

    /**
     * Check if the line of credit can be closed.
     */
    public boolean canClose() {
        return (this.status == LocStatus.ACTIVE || this.status == LocStatus.SUSPENDED) &&  hasNoConsumedAmount();
    }

    public boolean hasNoConsumedAmount(){
        return this.summary == null || (this.summary.getConsumedAmount() == null || this.summary.getConsumedAmount().compareTo(BigDecimal.ZERO) == 0);
    }


    /**
     * Update the line of credit with changes from command.
     */
    public Map<String, Object> update(JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>();

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

        if (command.isChangeInStringParameterNamed("va", this.virtualAccount)) {
            final String newValue = command.stringValueOfParameterNamed("va");
            actualChanges.put("va", newValue);
            this.virtualAccount = newValue;
        }

        if (command.isChangeInStringParameterNamed("specialConditions", this.specialConditions)) {
            final String newValue = command.stringValueOfParameterNamed("specialConditions");
            actualChanges.put("specialConditions", newValue);
            this.specialConditions = newValue;
        }

        return actualChanges;
    }


}
