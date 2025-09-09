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

    @Schema(example = "Business Credit Line", description = "Name of the line of credit")
    private String name;

    @Schema(example = "PAYABLE", description = "Type of product for the line of credit")
    private String productType;

    @Schema(example = "3000000", description = "Maximum amount allowed for the line of credit (can be string or number)")
    private String maximumAmount;

    @Schema(example = "29 August 2025", description = "Start date of the line of credit")
    private String startDate;

    @Schema(example = "29 October 2025", description = "End date of the line of credit")
    private String endDate;

    @Schema(example = "dd MMMM yyyy", description = "Format of the dates provided (e.g., 'dd MMMM yyyy' for '29 August 2025')")
    private String dateFormat;

    @Schema(example = "en", description = "Locale to interpret the date format")
    private String locale;

    @Schema(example = "2,500,000", description = "Approved credit facility amount")
    private String approvedCreditFacilityAmount;

    @Schema(example = "LOC-2025-001", description = "External ID for the line of credit")
    private String externalId;

    @Schema(example = "30 August 2025", description = "Activation date of the line of credit")
    private String activationDate;

    @Schema(example = "USD", description = "Currency for the line of credit")
    private String currencyCode;

    @Schema(example = "85.50", description = "Advance percentage")
    private String advancePercentage;

    @Schema(example = "60", description = "Tenor in days")
    private Integer tenorDays;

    @Schema(example = "ABC Corp, XYZ Ltd, DEF Industries", description = "List of approved buyers")
    private String approvedBuyers;

    @Schema(example = "2.50", description = "Processing fee percentage for LOC")
    private String processingFeePctLoc;

    @Schema(example = "PERCENTAGE", description = "Cash margin type")
    private String cashMarginType;

    @Schema(example = "150,000", description = "Cash margin value")
    private String cashMarginValue;

    @Schema(example = "PER_INVOICE", description = "Invoice handling fee basis")
    private String invHandlingFeeBasis;

    @Schema(example = "1.25", description = "Invoice handling fee percentage")
    private String invHandlingFeePct;

    @Schema(example = "5,000", description = "Minimum invoice handling fee amount")
    private String invHandlingFeeMinAmount;

    @Schema(example = "USD", description = "Invoice handling fee currency")
    private String invHandlingFeeCurrency;

    @Schema(example = "15 September 2025", description = "Interim review date")
    private String interimReviewDate;

    @Schema(example = "FLOATING", description = "Rate type")
    private String rateType;

    @Schema(example = "12.50", description = "Annual interest rate")
    private String annualInterestRate;

    @Schema(example = "POST_DISBURSAL", description = "Interest payment timing")
    private String isInterestUpfrontOrPostDisbursal;

    @Schema(example = "CredibleX Solutions Ltd", description = "Client company name")
    private String clientCompanyName;

    @Schema(example = "John Smith", description = "Client contact person name")
    private String clientContactPersonName;

    @Schema(example = "+1-555-0123", description = "Client contact person phone")
    private String clientContactPersonPhone;

    @Schema(example = "john.smith@crediblex.com", description = "Client contact person email")
    private String clientContactPersonEmail;

    @Schema(example = "Jane Doe", description = "Authorized signatory name")
    private String authorizedSignatoryName;

    @Schema(example = "+1-555-0456", description = "Authorized signatory phone")
    private String authorizedSignatoryPhone;

    @Schema(example = "jane.doe@crediblex.com", description = "Authorized signatory email")
    private String authorizedSignatoryEmail;

    @Schema(example = "VA123456789", description = "Virtual account identifier")
    private String va;

    @Schema(example = "Global Finance Partners", description = "Distribution partner")
    private String distributionPartner;

    @Schema(example = "25.00", description = "Bank transfer fee")
    private String bankTransferFee;

    @Schema(example = "Subject to quarterly review. Early repayment allowed with 30 days notice.", description = "Special conditions")
    private String specialConditions;

    @Schema(example = "500.00", description = "Late payment fee")
    private String latePaymentFee;

}
