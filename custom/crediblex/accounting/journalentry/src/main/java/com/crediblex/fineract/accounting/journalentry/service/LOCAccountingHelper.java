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
package com.crediblex.fineract.accounting.journalentry.service;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Shared utility class for LOC (Line of Credit) accounting operations. Contains common methods used by both
 * CustomCashBasedAccountingProcessorForSavings and CustomAccrualBasedAccountingProcessorForSavings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LOCAccountingHelper {

    // LOC Product Short Names
    public static final String LOC_ACTIVATION_PRODUCT_SHORT_NAME = "LAA";
    public static final String LOC_RECEIVABLE_PRODUCT_SHORT_NAME = "LRL";
    public static final String RBF_PRODUCT_SHORT_NAME = "RBF";

    // Payment Type IDs
    public static final Long PROCESSING_FEE_PAYMENT_TYPE_ID = 1L;
    public static final Long RBF_PAYMENT_TYPE_ID = 5L;
    public static final Long LOC_RECEIVABLE_PAYMENT_TYPE_ID = 73L;

    // GL Codes
    public static final String LOC_ACTIVATION_DEBIT_GL_CODE = "100062";
    public static final String LOC_ACTIVATION_FEE_INCOME_GL_CODE = "300004";
    public static final String LOC_ACTIVATION_VAT_GL_CODE = "200065";
    public static final String LOC_RECEIVABLE_DEBIT_GL_CODE = "100062";
    public static final String LOC_RECEIVABLE_CREDIT_GL_CODE = "200086";
    public static final String LOC_RECEIVABLE_LOAN_PAYABLE_GL_CODE = "200041";
    public static final String RBF_GL_CODE = "200040";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Check if savings product is LOC Activation. Queries product short_name from database to identify LOC Activation
     * products.
     *
     * @param savingsProductId
     *            The savings product ID
     * @return true if it's an LOC Activation savings product
     */
    public boolean isLOCActivationSavingsProduct(Long savingsProductId) {
        if (savingsProductId == null) {
            return false;
        }

        try {
            String sql = "SELECT short_name FROM m_savings_product WHERE id = ?";
            String shortName = jdbcTemplate.queryForObject(sql, String.class, savingsProductId);
            return LOC_ACTIVATION_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            log.debug("LOCAccountingHelper: Error checking LOC Activation savings product for savingsProductId {}: {}", savingsProductId,
                    e.getMessage());
            return false;
        }
    }

    /**
     * Check if loan product is LOC Receivable. Queries product short_name from database to identify LOC Receivable
     * products.
     *
     * @param loanProductId
     *            The loan product ID
     * @return true if it's an LOC Receivable loan product
     */
    public boolean isLOCReceivableLoanProduct(Long loanProductId) {
        if (loanProductId == null) {
            return false;
        }

        try {
            String sql = "SELECT short_name FROM m_product_loan WHERE id = ?";
            String shortName = jdbcTemplate.queryForObject(sql, String.class, loanProductId);
            return LOC_RECEIVABLE_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            log.debug("LOCAccountingHelper: Error checking LOC Receivable loan product for loanProductId {}: {}", loanProductId,
                    e.getMessage());
            return false;
        }
    }

    /**
     * Check if loan product is RBF (Revenue Based Financing). Queries product short_name from database to identify RBF
     * products.
     *
     * @param loanProductId
     *            The loan product ID
     * @return true if it's an RBF loan product
     */
    public boolean isRBFLoanProduct(Long loanProductId) {
        if (loanProductId == null) {
            return false;
        }

        try {
            String sql = "SELECT short_name FROM m_product_loan WHERE id = ?";
            String shortName = jdbcTemplate.queryForObject(sql, String.class, loanProductId);
            return RBF_PRODUCT_SHORT_NAME.equals(shortName);
        } catch (Exception e) {
            log.debug("LOCAccountingHelper: Error checking RBF loan product for loanProductId {}: {}", loanProductId, e.getMessage());
            return false;
        }
    }

    /**
     * Get the tax amount from the LOC charge linked to this savings account. This queries the LOC directly using the
     * settlement savings account ID (more reliable approach), similar to how the LOC API returns charge information.
     *
     * @param savingsAccountId
     *            The savings account ID
     * @return The tax amount from the LOC charges, or ZERO if not found
     */
    public BigDecimal getLOCChargeTaxAmountBySavingsAccount(Long savingsAccountId) {
        try {
            // Query the LOC charges directly using the settlement savings account ID
            // This is more reliable than querying through the paid_by table
            String sql = "SELECT COALESCE(SUM(lc.tax_amount), 0) " + "FROM m_line_of_credit loc "
                    + "JOIN m_line_of_credit_charge lc ON lc.line_of_credit_id = loc.id " + "WHERE loc.settlement_savings_account_id = ? "
                    + "AND lc.is_active = true " + "AND lc.is_paid_derived = false " + "AND lc.waived = false";

            BigDecimal taxAmount = jdbcTemplate.queryForObject(sql, BigDecimal.class, savingsAccountId);

            if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                log.info("LOCAccountingHelper: Found LOC charge tax amount {} for savings account {}", taxAmount, savingsAccountId);
                return taxAmount;
            }

            // If no unpaid charges found, try to get from all active charges
            // This handles the case where the charge was just paid but journal entries are being created
            String sqlPaid = "SELECT COALESCE(SUM(lc.tax_amount), 0) " + "FROM m_line_of_credit loc "
                    + "JOIN m_line_of_credit_charge lc ON lc.line_of_credit_id = loc.id " + "WHERE loc.settlement_savings_account_id = ? "
                    + "AND lc.is_active = true";

            taxAmount = jdbcTemplate.queryForObject(sqlPaid, BigDecimal.class, savingsAccountId);

            if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                log.info("LOCAccountingHelper: Found LOC charge tax amount {} (from all charges) for savings account {}", taxAmount,
                        savingsAccountId);
                return taxAmount;
            }

            return BigDecimal.ZERO;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.debug("LOCAccountingHelper: No LOC found for savings account {}", savingsAccountId);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("LOCAccountingHelper: Error getting LOC charge tax amount for savings account {}: {}", savingsAccountId,
                    e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get the tax amount from the LOC charge linked to this savings transaction. This queries the
     * m_line_of_credit_charge_paid_by table to find the linked LOC charge, then gets the tax_amount from the
     * m_line_of_credit_charge table.
     *
     * @param transactionId
     *            The savings transaction ID (e.g., "S12345")
     * @return The tax amount from the LOC charge, or ZERO if not found
     */
    public BigDecimal getLOCChargeTaxAmount(String transactionId) {
        try {
            // Extract numeric transaction ID from string like "S14119"
            String numericId = transactionId.replace("S", "").trim();
            Long transactionNumericId = Long.parseLong(numericId);

            // Query the LOC charge tax amount via the paid_by link table
            String sql = "SELECT COALESCE(SUM(lc.tax_amount), 0) " + "FROM m_line_of_credit_charge_paid_by pb "
                    + "JOIN m_line_of_credit_charge lc ON lc.id = pb.line_of_credit_charge_id "
                    + "WHERE pb.savings_account_transaction_id = ?";

            BigDecimal taxAmount = jdbcTemplate.queryForObject(sql, BigDecimal.class, transactionNumericId);

            if (taxAmount != null && taxAmount.compareTo(BigDecimal.ZERO) > 0) {
                log.info("LOCAccountingHelper: Found LOC charge tax amount {} for savings transaction {}", taxAmount, transactionId);
                return taxAmount;
            }

            return BigDecimal.ZERO;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            log.debug("LOCAccountingHelper: No LOC charge found for savings transaction {}", transactionId);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("LOCAccountingHelper: Error getting LOC charge tax amount for transaction {}: {}", transactionId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
