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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request DTO for Line of Credit operations")
public class LineOfCreditRequest {

    @Schema(example = "34", description = "Client ID for the line of credit")
    private Long clientId;

    @Schema(example = "1", description = "Type of product for the line of credit")
    @NotNull(message = "Product type is required")
    @Min(value = 1, message = "Product type must be greater than 0")
    private Integer productType;

    @Schema(example = "AED", description = "Currency for the line of credit")
    @NotNull(message = "Currency code is required")
    private String currencyCode;

    @Schema(example = "Test this guy works", description = "Client company name")
    private String clientCompanyName;

    @Schema(example = "Test", description = "Client contact person name")
    private String clientContactPersonName;

    @Schema(example = "84788", description = "Client contact person phone")
    private String clientContactPersonPhone;

    @Schema(example = "gkkdk@gmail.com", description = "Client contact person email")
    private String clientContactPersonEmail;

    @Schema(example = "Jane Doe", description = "Authorized signatory name")
    private String authorizedSignatoryName;

    @Schema(example = "948994884", description = "Authorized signatory phone")
    private String authorizedSignatoryPhone;

    @Schema(example = "augho@gmail.com", description = "Authorized signatory email")
    private String authorizedSignatoryEmail;

    @Schema(example = "VA2348899", description = "Virtual account identifier")
    private String virtualAccount;

    @Schema(example = "34587998", description = "External ID for the line of credit")
    @NotBlank(message = "External ID is required")
    @Size(min = 1, message = "External ID must have at least 1 character")
    private String externalId;

    @Schema(example = "This is the beginning of the new order", description = "Special conditions")
    private String specialConditions;

    @Schema(example = "30000", description = "Maximum amount allowed for the line of credit")
    @NotNull(message = "Maximum credit limit is required")
    @Min(value = 1, message = "Maximum credit limit must be greater than 0")
    private BigDecimal maxCreditLimit;

    @Schema(example = "22 September 2025", description = "Start date of the line of credit")
    @NotNull(message = "Start date is required")
    private String startDate;

    @Schema(example = "22 September 2025", description = "Start date of the line of credit")
    private String endDate;

    @Schema(example = "12", description = "Review period for the line of credit")
    private Integer reviewPeriod;

    @Schema(example = "22 September 2026", description = "Interim review date")
    private String interimReviewDate;

    @Schema(description = "Interest payment timing")
    private String interestPaymentType;

    @Schema(example = "3", description = "Annual interest rate")
    @NotNull(message = "Annual interest rate is required")
    private Integer annualInterestRate;

    @Schema(example = "365", description = "Tenor in days")
    @NotNull(message = "Tenor days is required")
    @Min(value = 1, message = "Tenor days must be greater than 0")
    private Integer tenorDays;

    @Schema(example = "100", description = "Advance percentage")
    private String advancePercentage;

    @Schema(example = "1", description = "Cash margin type")
    private Integer cashMarginType;

    @Schema(example = "23", description = "Cash margin value")
    private Float cashMarginValue;

    @Schema(example = "1", description = "Interest charge time")
    private Integer interestChargeTime;

    @Schema(example = "1", description = "Loan officer assigned to the line of credit")
    private Long loanOfficerId;

    @Schema(example = "Test", description = "Distribution partner")
    private String distributionPartner;

    @Schema(description = "List of approved buyers")
    private List<ApprovedBuyer> approvedBuyers;

    @Schema(example = "3839", description = "Settlement savings account ID")
    private Long settlementSavingsAccountId;

    @Schema(description = "List of charges associated with the line of credit")
    private List<Object> charges;

    @Schema(example = "dd MMMM yyyy", description = "Format of the dates provided (e.g., 'dd MMMM yyyy' for '22 September 2025')")
    private String dateFormat;

    @Schema(example = "en", description = "Locale to interpret the date format")
    private String locale;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApprovedBuyer {

        @Schema(example = "Approver1", description = "Name of approved buyer")
        private String name;
    }
}
