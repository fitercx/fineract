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

import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;

/**
 * Extension of LoanAccountData to include Line of Credit information.
 * This class adds line of credit specific fields to the standard loan account data.
 */
@Getter
@Setter
public class LocLoanAccountData extends LoanAccountData {

    // Line of Credit specific fields
    private Long lineOfCreditId;
    private String lineOfCreditName;
    private String lineOfCreditExternalId;
    private Boolean isAssociatedWithLineOfCredit;

    /**
     * Default constructor.
     */
    public LocLoanAccountData() {
        super();
    }

    /**
     * Constructor that takes a base LoanAccountData and adds line of credit information.
     *
     * @param baseData the base loan account data
     * @param lineOfCreditId the line of credit ID
     * @param lineOfCreditName the line of credit name
     * @param lineOfCreditExternalId the line of credit external ID
     */
    public LocLoanAccountData(LoanAccountData baseData, Long lineOfCreditId, String lineOfCreditName, String lineOfCreditExternalId) {
        // Copy basic fields from base data using reflection or manual copying
        if (baseData != null) {
            this.setId(baseData.getId());
            this.setAccountNo(baseData.getAccountNo());
            this.setExternalId(baseData.getExternalId());
            this.setClientId(baseData.getClientId());
            this.setClientAccountNo(baseData.getClientAccountNo());
            this.setClientName(baseData.getClientName());
            this.setClientExternalId(baseData.getClientExternalId());
            this.setClientOfficeId(baseData.getClientOfficeId());
            this.setGroup(baseData.getGroup());
            this.setLoanProductId(baseData.getLoanProductId());
            this.setLoanProductName(baseData.getLoanProductName());
            this.setLoanProductDescription(baseData.getLoanProductDescription());
            this.setLoanProductLinkedToFloatingRate(baseData.isLoanProductLinkedToFloatingRate());
            this.setFundId(baseData.getFundId());
            this.setFundName(baseData.getFundName());
            this.setLoanPurposeId(baseData.getLoanPurposeId());
            this.setLoanPurposeName(baseData.getLoanPurposeName());
            this.setLoanOfficerId(baseData.getLoanOfficerId());
            this.setLoanOfficerName(baseData.getLoanOfficerName());
            this.setLoanType(baseData.getLoanType());
            this.setCurrency(baseData.getCurrency());
            this.setPrincipal(baseData.getPrincipal());
            this.setApprovedPrincipal(baseData.getApprovedPrincipal());
            this.setProposedPrincipal(baseData.getProposedPrincipal());
            this.setNetDisbursalAmount(baseData.getNetDisbursalAmount());
            this.setTermFrequency(baseData.getTermFrequency());
            this.setTermPeriodFrequencyType(baseData.getTermPeriodFrequencyType());
            this.setNumberOfRepayments(baseData.getNumberOfRepayments());
            this.setRepaymentEvery(baseData.getRepaymentEvery());
            this.setFixedLength(baseData.getFixedLength());
            this.setRepaymentFrequencyType(baseData.getRepaymentFrequencyType());
            this.setRepaymentFrequencyNthDayType(baseData.getRepaymentFrequencyNthDayType());
            this.setRepaymentFrequencyDayOfWeekType(baseData.getRepaymentFrequencyDayOfWeekType());
            this.setInterestRatePerPeriod(baseData.getInterestRatePerPeriod());
            this.setInterestRateFrequencyType(baseData.getInterestRateFrequencyType());
            this.setAnnualInterestRate(baseData.getAnnualInterestRate());
            this.setFloatingInterestRate(baseData.isFloatingInterestRate());
            this.setInterestRateDifferential(baseData.getInterestRateDifferential());
            this.setAmortizationType(baseData.getAmortizationType());
            this.setInterestType(baseData.getInterestType());
            this.setInterestCalculationPeriodType(baseData.getInterestCalculationPeriodType());
            this.setAllowPartialPeriodInterestCalculation(baseData.getAllowPartialPeriodInterestCalculation());
            this.setInArrearsTolerance(baseData.getInArrearsTolerance());
            this.setTransactionProcessingStrategyCode(baseData.getTransactionProcessingStrategyCode());
            this.setTransactionProcessingStrategyName(baseData.getTransactionProcessingStrategyName());
            this.setGraceOnPrincipalPayment(baseData.getGraceOnPrincipalPayment());
            this.setRecurringMoratoriumOnPrincipalPeriods(baseData.getRecurringMoratoriumOnPrincipalPeriods());
            this.setGraceOnInterestPayment(baseData.getGraceOnInterestPayment());
            this.setGraceOnInterestCharged(baseData.getGraceOnInterestCharged());
            this.setGraceOnArrearsAgeing(baseData.getGraceOnArrearsAgeing());
            this.setInterestChargedFromDate(baseData.getInterestChargedFromDate());
            this.setExpectedFirstRepaymentOnDate(baseData.getExpectedFirstRepaymentOnDate());
            this.setSyncDisbursementWithMeeting(baseData.getSyncDisbursementWithMeeting());
            this.setDisallowExpectedDisbursements(baseData.getDisallowExpectedDisbursements());
            this.setLocale(baseData.getLocale());
            this.setDateFormat(baseData.getDateFormat());
        }
        
        // Set line of credit specific fields
        this.lineOfCreditId = lineOfCreditId;
        this.lineOfCreditName = lineOfCreditName;
        this.lineOfCreditExternalId = lineOfCreditExternalId;
        this.isAssociatedWithLineOfCredit = lineOfCreditId != null;
    }

    /**
     * Check if this loan is associated with a line of credit.
     *
     * @return true if associated with a line of credit, false otherwise
     */
    public boolean isAssociatedWithLineOfCredit() {
        return Boolean.TRUE.equals(isAssociatedWithLineOfCredit) && lineOfCreditId != null;
    }
}
