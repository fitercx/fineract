/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.domain;

import com.crediblex.fineract.portfolio.loanaccount.data.LoanAccountAdditionalProperties;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;

@Entity
@Table(name = "m_loan_line_of_credit_params")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
public class LoanLineOfCreditParams {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false, unique = true)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_of_credit_id", nullable = false)
    private LineOfCredit lineOfCredit;

    // Invoice related fields
    @Column(name = "invoice_no", nullable = false, length = 100)
    private String invoiceNo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "invoice_due_date", nullable = false)
    private LocalDate invoiceDueDate;

    @Column(name = "invoice_currency", nullable = false, length = 3)
    private String invoiceCurrency;

    @Column(name = "invoice_amount", precision = 19, scale = 6)
    private BigDecimal invoiceAmount;

    // New LOC-related fields
    @Column(name = "disapproved_amount", precision = 19, scale = 6)
    private BigDecimal disapprovedAmount;

    @Column(name = "approved_receivable_amount", precision = 19, scale = 6)
    private BigDecimal approvedReceivableAmount;

    @Column(name = "advance_percentage", precision = 5, scale = 2)
    private BigDecimal advancePercentage;

    @Column(name = "amount_after_advance", precision = 19, scale = 6)
    private BigDecimal amountAfterAdvance;

    @Column(name = "buyer_details", columnDefinition = "TEXT")
    private String buyerDetails;

    // Additional LOC-related fields
    @Column(name = "exchange_rate", precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "markup", precision = 10, scale = 4)
    private BigDecimal markup;

    @Column(name = "amount_in_facility_currency", precision = 19, scale = 6)
    private BigDecimal amountInFacilityCurrency;

    @Column(name = "approved_payable_amount", precision = 19, scale = 6)
    private BigDecimal approvedPayableAmount;

    @Column(name = "supplier_details", columnDefinition = "TEXT")
    private String supplierDetails;

    // New AED currency related fields (for display/audit purposes)
    @Column(name = "invoice_amount_in_aed", precision = 19, scale = 6)
    private BigDecimal invoiceAmountInAED;

    @Column(name = "disapproved_amount_in_aed", precision = 19, scale = 6)
    private BigDecimal disapprovedAmountInAED;

    @Column(name = "approved_invoice_amount_in_aed", precision = 19, scale = 6)
    private BigDecimal approvedInvoiceAmountInAED;

    @Column(name = "amount_after_advance_in_aed", precision = 19, scale = 6)
    private BigDecimal amountAfterAdvanceInAED;

    @Column(name = "requested_amount_in_aed", precision = 19, scale = 6)
    private BigDecimal requestedAmountInAED;

    @Column(name = "funded_amount_in_invoice_currency", precision = 19, scale = 6)
    private BigDecimal fundedAmountInInvoiceCurrency;

    @Column(name = "requested_amount", precision = 19, scale = 6)
    private BigDecimal requestedAmount;

    // Auto-compute fields before persistence
    @PrePersist
    @PreUpdate
    private void computeFields() {
        computeApprovedReceivableAmount();
        computeAmountAfterAdvance();
        computeApprovedPayableAmount();
        computeAmountInFacilityCurrency();
    }

    // Business logic: Approved Receivable Amount = Invoice Amount - Disapproved Amount
    private void computeApprovedReceivableAmount() {
        if (this.loan != null && this.disapprovedAmount != null && lineOfCredit.getProductType().isReceivable()) {
            this.approvedReceivableAmount = invoiceAmount.subtract(this.disapprovedAmount);

        }
    }

    // Business logic: For receivable products, Amount After Advance = Amount in facility current which is
    // min(amountAfterAdvanceInAED, requestedAmountInAED, availableLimit) from Frontend Calculation
    private void computeAmountAfterAdvance() {
        if (this.amountInFacilityCurrency != null && this.lineOfCredit != null && lineOfCredit.getProductType().isReceivable()) {
            this.amountAfterAdvance = this.amountInFacilityCurrency;
        }
    }

    // Business logic: Amount In Facility Currency = Approved Receivable Amount * Exchange Rate
    private void computeAmountInFacilityCurrency() {
        // Only compute if not already provided from frontend
        // Frontend calculates: min(amountAfterAdvanceInAED, requestedAmountInAED, availableLimit)
        if (this.amountInFacilityCurrency == null && this.approvedPayableAmount != null && this.exchangeRate != null
                && lineOfCredit.getProductType().isPayable()) {
            this.amountInFacilityCurrency = this.approvedPayableAmount.multiply(this.exchangeRate.add(this.markup));
        }
    }

    // Business logic: Approved Payable Amount = Invoice amount - Disapproved Amount
    private void computeApprovedPayableAmount() {
        if (this.invoiceAmount != null && this.disapprovedAmount != null && lineOfCredit.getProductType().isPayable()) {
            this.approvedPayableAmount = this.invoiceAmount.subtract(this.disapprovedAmount);
        }
    }

    public static LoanLineOfCreditParams fromJson(Loan loan, LineOfCredit loc, JsonCommand jsonCommand) {
        LoanLineOfCreditParams params = new LoanLineOfCreditParams();

        // Set the entity relationships
        params.setLoan(loan);
        params.setLineOfCredit(loc);

        // Extract invoice details from JSON using constants
        if (jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_NO) != null) {
            params.setInvoiceNo(jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_NO));
        }

        if (jsonCommand.localDateValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_DATE) != null) {
            params.setInvoiceDate(jsonCommand.localDateValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_DATE));
        }

        if (jsonCommand.localDateValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_DUE_DATE) != null) {
            params.setInvoiceDueDate(jsonCommand.localDateValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_DUE_DATE));
        }

        if (jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_CURRENCY) != null) {
            params.setInvoiceCurrency(jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_CURRENCY));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_AMOUNT) != null) {
            params.setInvoiceAmount(jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_AMOUNT));
        }

        // Extract new LOC-related parameters
        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT) != null) {
            params.setDisapprovedAmount(jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.ADVANCE_PERCENTAGE) != null) {
            params.setAdvancePercentage(jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.ADVANCE_PERCENTAGE));
        }

        if (jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.BUYER_DETAILS) != null) {
            params.setBuyerDetails(jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.BUYER_DETAILS));
        }

        // Extract additional LOC-related parameters
        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.EXCHANGE_RATE) != null) {
            params.setExchangeRate(jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.EXCHANGE_RATE));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.MARKUP) != null) {
            params.setMarkup(jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.MARKUP));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.AMOUNT_IN_FACILITY_CURRENCY) != null) {
            params.setAmountInFacilityCurrency(
                    jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.AMOUNT_IN_FACILITY_CURRENCY));
        }

        if (jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.SUPPLIER_DETAILS) != null) {
            params.setSupplierDetails(jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.SUPPLIER_DETAILS));
        }

        // Extract new AED currency related parameters
        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_AMOUNT_IN_AED) != null) {
            params.setInvoiceAmountInAED(
                    jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_AMOUNT_IN_AED));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT_IN_AED) != null) {
            params.setDisapprovedAmountInAED(
                    jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT_IN_AED));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.APPROVED_INVOICE_AMOUNT_IN_AED) != null) {
            params.setApprovedInvoiceAmountInAED(
                    jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.APPROVED_INVOICE_AMOUNT_IN_AED));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.AMOUNT_AFTER_ADVANCE_IN_AED) != null) {
            params.setAmountAfterAdvanceInAED(
                    jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.AMOUNT_AFTER_ADVANCE_IN_AED));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.REQUESTED_AMOUNT_IN_AED) != null) {
            params.setRequestedAmountInAED(
                    jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.REQUESTED_AMOUNT_IN_AED));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.FUNDED_AMOUNT_IN_INVOICE_CURRENCY) != null) {
            params.setFundedAmountInInvoiceCurrency(
                    jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.FUNDED_AMOUNT_IN_INVOICE_CURRENCY));
        }

        if (jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.REQUESTED_AMOUNT) != null) {
            params.setRequestedAmount(jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.REQUESTED_AMOUNT));
        }

        // Note: amountInFacilityCurrency and approvedPayableAmount are auto-computed in @PrePersist

        return params;
    }

    public Map<String, Object> updateFromJson(JsonCommand jsonCommand) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>();

        // Handle LineOfCredit relationship change
        if (jsonCommand.hasParameter("lineOfCreditId")) {
            final Long newLineOfCreditId = jsonCommand.longValueOfParameterNamed("lineOfCreditId");
            final Long currentLineOfCreditId = this.lineOfCredit != null ? this.lineOfCredit.getId() : null;

            if (!Objects.equals(newLineOfCreditId, currentLineOfCreditId)) {
                actualChanges.put("lineOfCreditId", newLineOfCreditId);
            }
        }

        // Handle invoice-related parameters
        if (jsonCommand.isChangeInStringParameterNamed(LoanAccountAdditionalProperties.INVOICE_NO, this.invoiceNo)) {
            final String newValue = jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_NO);
            actualChanges.put(LoanAccountAdditionalProperties.INVOICE_NO, newValue);
            this.invoiceNo = newValue;
        }

        if (jsonCommand.isChangeInLocalDateParameterNamed(LoanAccountAdditionalProperties.INVOICE_DATE, this.invoiceDate)) {
            final LocalDate newValue = jsonCommand.localDateValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_DATE);
            actualChanges.put(LoanAccountAdditionalProperties.INVOICE_DATE, newValue);
            this.invoiceDate = newValue;
        }

        if (jsonCommand.isChangeInLocalDateParameterNamed(LoanAccountAdditionalProperties.INVOICE_DUE_DATE, this.invoiceDueDate)) {
            final LocalDate newValue = jsonCommand.localDateValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_DUE_DATE);
            actualChanges.put(LoanAccountAdditionalProperties.INVOICE_DUE_DATE, newValue);
            this.invoiceDueDate = newValue;
        }

        if (jsonCommand.isChangeInStringParameterNamed(LoanAccountAdditionalProperties.INVOICE_CURRENCY, this.invoiceCurrency)) {
            final String newValue = jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_CURRENCY);
            actualChanges.put(LoanAccountAdditionalProperties.INVOICE_CURRENCY, newValue);
            this.invoiceCurrency = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.INVOICE_AMOUNT, this.invoiceAmount)) {
            final BigDecimal newValue = jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_AMOUNT);
            actualChanges.put(LoanAccountAdditionalProperties.INVOICE_AMOUNT, newValue);
            this.invoiceAmount = newValue;
        }

        // Handle new LOC-related parameters
        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT, this.disapprovedAmount)) {
            final BigDecimal newValue = jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT);
            actualChanges.put(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT, newValue);
            this.disapprovedAmount = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.ADVANCE_PERCENTAGE, this.advancePercentage)) {
            final BigDecimal newValue = jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.ADVANCE_PERCENTAGE);
            actualChanges.put(LoanAccountAdditionalProperties.ADVANCE_PERCENTAGE, newValue);
            this.advancePercentage = newValue;
        }

        if (jsonCommand.isChangeInStringParameterNamed(LoanAccountAdditionalProperties.BUYER_DETAILS, this.buyerDetails)) {
            final String newValue = jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.BUYER_DETAILS);
            actualChanges.put(LoanAccountAdditionalProperties.BUYER_DETAILS, newValue);
            this.buyerDetails = newValue;
        }

        // Handle additional LOC-related parameters
        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.EXCHANGE_RATE, this.exchangeRate)) {
            final BigDecimal newValue = jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.EXCHANGE_RATE);
            actualChanges.put(LoanAccountAdditionalProperties.EXCHANGE_RATE, newValue);
            this.exchangeRate = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.MARKUP, this.markup)) {
            final BigDecimal newValue = jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.MARKUP);
            actualChanges.put(LoanAccountAdditionalProperties.MARKUP, newValue);
            this.markup = newValue;
        }

        if (jsonCommand.isChangeInStringParameterNamed(LoanAccountAdditionalProperties.SUPPLIER_DETAILS, this.supplierDetails)) {
            final String newValue = jsonCommand.stringValueOfParameterNamed(LoanAccountAdditionalProperties.SUPPLIER_DETAILS);
            actualChanges.put(LoanAccountAdditionalProperties.SUPPLIER_DETAILS, newValue);
            this.supplierDetails = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.AMOUNT_IN_FACILITY_CURRENCY,
                this.amountInFacilityCurrency)) {
            final BigDecimal newValue = jsonCommand
                    .bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.AMOUNT_IN_FACILITY_CURRENCY);
            actualChanges.put(LoanAccountAdditionalProperties.AMOUNT_IN_FACILITY_CURRENCY, newValue);
            this.amountInFacilityCurrency = newValue;
        }

        // Handle new AED currency related parameters
        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.INVOICE_AMOUNT_IN_AED,
                this.invoiceAmountInAED)) {
            final BigDecimal newValue = jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.INVOICE_AMOUNT_IN_AED);
            actualChanges.put(LoanAccountAdditionalProperties.INVOICE_AMOUNT_IN_AED, newValue);
            this.invoiceAmountInAED = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT_IN_AED,
                this.disapprovedAmountInAED)) {
            final BigDecimal newValue = jsonCommand
                    .bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT_IN_AED);
            actualChanges.put(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT_IN_AED, newValue);
            this.disapprovedAmountInAED = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.APPROVED_INVOICE_AMOUNT_IN_AED,
                this.approvedInvoiceAmountInAED)) {
            final BigDecimal newValue = jsonCommand
                    .bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.APPROVED_INVOICE_AMOUNT_IN_AED);
            actualChanges.put(LoanAccountAdditionalProperties.APPROVED_INVOICE_AMOUNT_IN_AED, newValue);
            this.approvedInvoiceAmountInAED = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.AMOUNT_AFTER_ADVANCE_IN_AED,
                this.amountAfterAdvanceInAED)) {
            final BigDecimal newValue = jsonCommand
                    .bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.AMOUNT_AFTER_ADVANCE_IN_AED);
            actualChanges.put(LoanAccountAdditionalProperties.AMOUNT_AFTER_ADVANCE_IN_AED, newValue);
            this.amountAfterAdvanceInAED = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.REQUESTED_AMOUNT_IN_AED,
                this.requestedAmountInAED)) {
            final BigDecimal newValue = jsonCommand
                    .bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.REQUESTED_AMOUNT_IN_AED);
            actualChanges.put(LoanAccountAdditionalProperties.REQUESTED_AMOUNT_IN_AED, newValue);
            this.requestedAmountInAED = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.FUNDED_AMOUNT_IN_INVOICE_CURRENCY,
                this.fundedAmountInInvoiceCurrency)) {
            final BigDecimal newValue = jsonCommand
                    .bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.FUNDED_AMOUNT_IN_INVOICE_CURRENCY);
            actualChanges.put(LoanAccountAdditionalProperties.FUNDED_AMOUNT_IN_INVOICE_CURRENCY, newValue);
            this.fundedAmountInInvoiceCurrency = newValue;
        }

        if (jsonCommand.isChangeInBigDecimalParameterNamed(LoanAccountAdditionalProperties.REQUESTED_AMOUNT, this.requestedAmount)) {
            final BigDecimal newValue = jsonCommand.bigDecimalValueOfParameterNamed(LoanAccountAdditionalProperties.REQUESTED_AMOUNT);
            actualChanges.put(LoanAccountAdditionalProperties.REQUESTED_AMOUNT, newValue);
            this.requestedAmount = newValue;
        }

        return actualChanges;
    }

    public void updateLineOfCredit(LineOfCredit lineOfCredit) {
        this.lineOfCredit = lineOfCredit;
    }
}
