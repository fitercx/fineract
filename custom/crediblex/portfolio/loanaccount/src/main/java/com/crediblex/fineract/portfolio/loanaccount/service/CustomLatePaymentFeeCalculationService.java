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
 * KIND, either express or implied. See the specific language
 * governing permissions and limitations under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;

import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for calculating late payment fees based on discounted amount instead of disbursed amount.
 * This service provides functionality to determine if a loan should use the net disbursal amount
 * (discounted amount) for late payment fee calculations.
 */
@Service
@Slf4j
public class CustomLatePaymentFeeCalculationService {

    static {
        System.out.println("🚀 CustomLatePaymentFeeCalculationService loaded!");
    }

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CustomLatePaymentFeeCalculationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Determines if the loan should use discounted amount (net disbursal amount) for late payment fee calculation.
     * This method checks if the loan is associated with a RECEIVABLE Line of Credit (LOC) and if the LOC has a late payment fee configured.
     * 
     * @param loan the loan entity
     * @return true if the loan should use discounted amount for late payment fees, false otherwise
     */
    public boolean shouldUseDiscountedAmountForLatePaymentFee(Loan loan) {
        log.info("=== CHECKING IF SHOULD USE DISCOUNTED AMOUNT ===");
        log.info("Loan ID: {}", loan.getId());
        
        try {
            // Check if the loan is associated with a Line of Credit
            Long lineOfCreditId = getLineOfCreditIdForLoan(loan.getId());
            log.info("Line of Credit ID: {}", lineOfCreditId);
            if (lineOfCreditId == null) {
                log.info("No Line of Credit associated with loan, returning false");
                return false;
            }

            // Check if the LOC is of type RECEIVABLE
            String locProductType = getLocProductType(lineOfCreditId);
            log.info("LOC Product Type: {}", locProductType);
            if (!LocProductType.RECEIVABLE.name().equalsIgnoreCase(locProductType)) {
                log.info("LOC is not RECEIVABLE type, returning false");
                return false;
            }

            // Check if the LOC has a late payment fee configured
            BigDecimal latePaymentFee = getLatePaymentFeeForLineOfCredit(lineOfCreditId);
            log.info("LOC Late Payment Fee: {}", latePaymentFee);
            if (latePaymentFee == null || latePaymentFee.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("LOC has no late payment fee configured, returning false");
                return false;
            }

            // Additional validation: ensure the loan has overdue penalty charges
            boolean hasOverduePenaltyCharges = loan.getCharges().stream()
                    .anyMatch(charge -> charge.isOverdueInstallmentCharge() &&
                             charge.isPenaltyCharge() &&
                             charge.isActive());
            log.info("Has overdue penalty charges: {}", hasOverduePenaltyCharges);
            
            log.info("Final result: {}", hasOverduePenaltyCharges);
            log.info("=== END CHECKING DISCOUNTED AMOUNT ===");
            return hasOverduePenaltyCharges;
        } catch (Exception e) {
            log.error("Error determining if loan {} should use discounted amount for late payment fee: {}",
                    loan.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Determines if the loan is associated with a PAYABLE Line of Credit and should use LOC late payment fee.
     * 
     * @param loan the loan entity
     * @return true if the loan is associated with a PAYABLE LOC, false otherwise
     */
    public boolean isPayableLocLoan(Loan loan) {
        try {
            // Check if the loan is associated with a Line of Credit
            Long lineOfCreditId = getLineOfCreditIdForLoan(loan.getId());
            if (lineOfCreditId == null) {
                return false;
            }

            // Check if the LOC is of type PAYABLE
            String locProductType = getLocProductType(lineOfCreditId);
            return LocProductType.PAYABLE.name().equalsIgnoreCase(locProductType);
        } catch (Exception e) {
            log.warn("Error determining if loan {} is PAYABLE LOC: {}", 
                    loan.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Gets the late payment fee amount from the LOC table for PAYABLE LOC loans.
     * 
     * @param loan the loan entity
     * @return the late payment fee amount from LOC, null if not applicable
     */
    public BigDecimal getLocLatePaymentFee(Loan loan) {
        try {
            // Check if the loan is associated with a PAYABLE Line of Credit
            if (!isPayableLocLoan(loan)) {
                return null;
            }

            Long lineOfCreditId = getLineOfCreditIdForLoan(loan.getId());
            return getLatePaymentFeeForLineOfCredit(lineOfCreditId);
        } catch (Exception e) {
            log.warn("Error getting LOC late payment fee for loan {}: {}", 
                    loan.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Gets the line of credit ID associated with the loan.
     * 
     * @param loanId the loan ID
     * @return the line of credit ID if found, null otherwise
     */
    private Long getLineOfCreditIdForLoan(Long loanId) {
        try {
            String sql = "SELECT line_of_credit_id FROM m_loan WHERE id = ?";
            Long result = jdbcTemplate.queryForObject(sql, Long.class, loanId);
            log.info("Query result for loan {} line_of_credit_id: {}", loanId, result);
            return result;
        } catch (EmptyResultDataAccessException e) {
            log.info("No line_of_credit_id found for loan {}", loanId);
            return null;
        }
    }

    /**
     * Gets the late payment fee configured for the line of credit.
     * 
     * @param lineOfCreditId the line of credit ID
     * @return the late payment fee amount if configured, null otherwise
     */
    private BigDecimal getLatePaymentFeeForLineOfCredit(Long lineOfCreditId) {
        try {
            String sql = "SELECT late_payment_fee FROM m_line_of_credit WHERE id = ?";
            BigDecimal result = jdbcTemplate.queryForObject(sql, BigDecimal.class, lineOfCreditId);
            log.info("Query result for LOC {} late_payment_fee: {}", lineOfCreditId, result);
            return result;
        } catch (EmptyResultDataAccessException e) {
            log.info("No late_payment_fee found for LOC {}", lineOfCreditId);
            return null;
        }
    }

    /**
     * Gets the product type of the line of credit.
     * 
     * @param lineOfCreditId the line of credit ID
     * @return the product type (RECEIVABLE or PAYABLE), null if not found
     */
    private String getLocProductType(Long lineOfCreditId) {
        try {
            String sql = "SELECT product_type FROM m_line_of_credit WHERE id = ?";
            String result = jdbcTemplate.queryForObject(sql, String.class, lineOfCreditId);
            log.info("Query result for LOC {} product_type: {}", lineOfCreditId, result);
            return result;
        } catch (EmptyResultDataAccessException e) {
            log.info("No product_type found for LOC {}", lineOfCreditId);
            return null;
        }
    }
}
