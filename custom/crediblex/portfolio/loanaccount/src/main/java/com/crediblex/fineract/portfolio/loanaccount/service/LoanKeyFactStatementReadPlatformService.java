/*
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
package com.crediblex.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanKeyFactStatementReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> retrieveKeyFactStatement(final Long loanId) {
        LoanKfsLoanData loan = retrieveLoan(loanId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", true);

        String productType = productType(loan);
        response.put("template", retrieveTemplate(productType));
        response.put("loan", loan.toMap(productType));
        response.put("charges", retrieveCharges(loanId));
        response.put("repaymentSchedule", retrieveSchedule(loanId, loan.principal != null ? loan.principal : loan.approvedPrincipal));
        return response;
    }

    private LoanKfsLoanData retrieveLoan(final Long loanId) {
        final String sql = """
                SELECT
                    l.id,
                    l.account_no,
                    l.external_id,
                    COALESCE(c.display_name, g.display_name) AS borrower_name,
                    pl.name AS product_name,
                    l.currency_code,
                    l.approved_principal,
                    l.principal_amount,
                    l.net_disbursal_amount,
                    l.approvedon_date,
                    COALESCE(l.disbursedon_date, l.expected_disbursedon_date) AS disbursement_date,
                    l.expected_maturedon_date,
                    l.number_of_repayments,
                    l.repay_every,
                    l.repayment_period_frequency_enum,
                    l.annual_nominal_interest_rate,
                    l.nominal_interest_rate_per_period,
                    l.factor_rate
                FROM m_loan l
                LEFT JOIN m_client c ON c.id = l.client_id
                LEFT JOIN m_group g ON g.id = l.group_id
                LEFT JOIN m_product_loan pl ON pl.id = l.product_id
                WHERE l.id = ?
                """;
        return jdbcTemplate.queryForObject(sql, new LoanKfsLoanDataMapper(), loanId);
    }

    private Map<String, Object> retrieveTemplate(final String productType) {
        try {
            final String sql = """
                    SELECT id, product_type, name, version, regulatory_footer, issued_by, template_json
                    FROM m_kfs_template
                    WHERE product_type = ? AND status = 'ACTIVE'
                    ORDER BY version DESC
                    LIMIT 1
                    """;
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Map<String, Object> template = new LinkedHashMap<>();
                template.put("id", rs.getLong("id"));
                template.put("productType", rs.getString("product_type"));
                template.put("name", rs.getString("name"));
                template.put("version", rs.getInt("version"));
                template.put("regulatoryFooter", rs.getString("regulatory_footer"));
                template.put("issuedBy", rs.getString("issued_by"));
                template.put("templateJson", rs.getString("template_json"));
                return template;
            }, productType);
        } catch (EmptyResultDataAccessException e) {
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("productType", productType);
            template.put("name", "Key Fact Statement");
            template.put("version", 1);
            template.put("issuedBy", "CredibleX Limited");
            template.put("regulatoryFooter",
                    "Regulatory Status - Category 2 Credit Provider regulated by the Financial Services Regulatory Authority (FSRA) of the Abu Dhabi Global Market (ADGM) under License No. 14218.");
            template.put("templateJson",
                    "{\"title\":\"Key Fact Statement\",\"logoUrl\":\"assets/images/crediblex-light-logo.png\",\"sections\":[]}");
            return template;
        }
    }

    private Map<String, Object> retrieveCharges(final Long loanId) {
        final String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN mc.is_penalty = FALSE THEN lc.amount ELSE 0 END), 0) AS fee_amount,
                    COALESCE(SUM(CASE WHEN mc.is_penalty = TRUE THEN lc.amount ELSE 0 END), 0) AS penalty_amount,
                    COALESCE(SUM(COALESCE(lc.tax_amount, 0)), 0) AS tax_amount
                FROM m_loan_charge lc
                JOIN m_charge mc ON mc.id = lc.charge_id
                WHERE lc.loan_id = ? AND lc.is_active = TRUE
                """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            Map<String, Object> charges = new LinkedHashMap<>();
            charges.put("feeAmount", rs.getBigDecimal("fee_amount"));
            charges.put("penaltyAmount", rs.getBigDecimal("penalty_amount"));
            charges.put("taxAmount", rs.getBigDecimal("tax_amount"));
            return charges;
        }, loanId);
    }

    private List<Map<String, Object>> retrieveSchedule(final Long loanId, final BigDecimal openingPrincipal) {
        final String sql = """
                SELECT
                    installment,
                    duedate,
                    principal_amount,
                    interest_amount,
                    fee_charges_amount,
                    penalty_charges_amount,
                    principal_completed_derived,
                    interest_completed_derived,
                    fee_charges_completed_derived,
                    penalty_charges_completed_derived,
                    completed_derived
                FROM m_loan_repayment_schedule
                WHERE loan_id = ?
                ORDER BY installment
                """;
        final BigDecimal[] outstandingPrincipal = { nvl(openingPrincipal) };
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            BigDecimal principal = nvl(rs.getBigDecimal("principal_amount"));
            BigDecimal interest = nvl(rs.getBigDecimal("interest_amount"));
            BigDecimal fees = nvl(rs.getBigDecimal("fee_charges_amount"));
            BigDecimal penalties = nvl(rs.getBigDecimal("penalty_charges_amount"));
            BigDecimal total = principal.add(interest).add(fees).add(penalties);
            outstandingPrincipal[0] = outstandingPrincipal[0].subtract(principal).max(BigDecimal.ZERO);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", rs.getInt("installment"));
            row.put("dueDate", rs.getDate("duedate") != null ? rs.getDate("duedate").toLocalDate().toString() : null);
            row.put("outstandingPrincipal", outstandingPrincipal[0]);
            row.put("principalDue", principal);
            row.put("interestDue", interest);
            row.put("feeChargesDue", fees);
            row.put("penaltyChargesDue", penalties);
            row.put("totalDue", total);
            row.put("paid",
                    nvl(rs.getBigDecimal("principal_completed_derived")).add(nvl(rs.getBigDecimal("interest_completed_derived")))
                            .add(nvl(rs.getBigDecimal("fee_charges_completed_derived")))
                            .add(nvl(rs.getBigDecimal("penalty_charges_completed_derived"))));
            row.put("status", rs.getBoolean("completed_derived") ? "PAID" : "SCHEDULED");
            return row;
        }, loanId);
    }

    private String productType(final LoanKfsLoanData loan) {
        String productName = loan.productName == null ? "" : loan.productName.toLowerCase(Locale.ROOT);
        if (loan.factorRate != null && loan.factorRate.compareTo(BigDecimal.ZERO) > 0) {
            return "FACTOR_RATE";
        }
        if (productName.contains("invoice")) {
            return "INVOICE_DISCOUNTING";
        }
        if (productName.contains("payable")) {
            return "PAYABLES_FINANCE";
        }
        return "RBF";
    }

    private static BigDecimal nvl(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static final class LoanKfsLoanData {

        private Long id;
        private String accountNo;
        private String externalId;
        private String borrowerName;
        private String productName;
        private String currencyCode;
        private BigDecimal approvedPrincipal;
        private BigDecimal principal;
        private BigDecimal netDisbursalAmount;
        private LocalDate approvedOnDate;
        private LocalDate disbursementDate;
        private LocalDate expectedMaturityDate;
        private Integer numberOfRepayments;
        private Integer repayEvery;
        private Integer repaymentFrequencyType;
        private BigDecimal annualInterestRate;
        private BigDecimal interestRatePerPeriod;
        private BigDecimal factorRate;

        private Map<String, Object> toMap(final String productType) {
            Map<String, Object> loan = new LinkedHashMap<>();
            loan.put("id", id);
            loan.put("accountNo", accountNo);
            loan.put("externalId", externalId);
            loan.put("borrowerName", borrowerName);
            loan.put("productName", productName);
            loan.put("productType", productType);
            loan.put("currencyCode", currencyCode);
            loan.put("approvedPrincipal", approvedPrincipal);
            loan.put("principal", principal);
            loan.put("netDisbursalAmount", netDisbursalAmount);
            loan.put("approvedOnDate", approvedOnDate != null ? approvedOnDate.toString() : null);
            loan.put("disbursementDate", disbursementDate != null ? disbursementDate.toString() : null);
            loan.put("expectedMaturityDate", expectedMaturityDate != null ? expectedMaturityDate.toString() : null);
            loan.put("numberOfRepayments", numberOfRepayments);
            loan.put("repayEvery", repayEvery);
            loan.put("repaymentFrequency", repaymentFrequency(repaymentFrequencyType));
            loan.put("annualInterestRate", annualInterestRate);
            loan.put("interestRatePerPeriod", interestRatePerPeriod);
            loan.put("factorRate", factorRate);
            loan.put("interestRateBasisLabel", interestRateBasisLabel(productType));
            loan.put("interestRateBasisValue", interestRateBasisValue());
            return loan;
        }

        private String interestRateBasisLabel(final String productType) {
            return "RBF".equals(productType) ? "Reducing Interest Rate" : "Interest Rate";
        }

        private String interestRateBasisValue() {
            if (annualInterestRate == null) {
                return "N/A";
            }
            return annualInterestRate.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "% per annum";
        }

        private String repaymentFrequency(final Integer frequencyType) {
            if (frequencyType == null) {
                return "";
            }
            return switch (frequencyType) {
                case 0 -> "Days";
                case 1 -> "Weeks";
                case 2 -> "Months";
                case 3 -> "Years";
                default -> frequencyType.toString();
            };
        }
    }

    private static final class LoanKfsLoanDataMapper implements RowMapper<LoanKfsLoanData> {

        @Override
        public LoanKfsLoanData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            LoanKfsLoanData loan = new LoanKfsLoanData();
            loan.id = rs.getLong("id");
            loan.accountNo = rs.getString("account_no");
            loan.externalId = rs.getString("external_id");
            loan.borrowerName = rs.getString("borrower_name");
            loan.productName = rs.getString("product_name");
            loan.currencyCode = rs.getString("currency_code");
            loan.approvedPrincipal = rs.getBigDecimal("approved_principal");
            loan.principal = rs.getBigDecimal("principal_amount");
            loan.netDisbursalAmount = rs.getBigDecimal("net_disbursal_amount");
            loan.approvedOnDate = rs.getDate("approvedon_date") != null ? rs.getDate("approvedon_date").toLocalDate() : null;
            loan.disbursementDate = rs.getDate("disbursement_date") != null ? rs.getDate("disbursement_date").toLocalDate() : null;
            loan.expectedMaturityDate = rs.getDate("expected_maturedon_date") != null ? rs.getDate("expected_maturedon_date").toLocalDate()
                    : null;
            loan.numberOfRepayments = rs.getObject("number_of_repayments", Integer.class);
            loan.repayEvery = rs.getObject("repay_every", Integer.class);
            loan.repaymentFrequencyType = rs.getObject("repayment_period_frequency_enum", Integer.class);
            loan.annualInterestRate = rs.getBigDecimal("annual_nominal_interest_rate");
            loan.interestRatePerPeriod = rs.getBigDecimal("nominal_interest_rate_per_period");
            loan.factorRate = rs.getBigDecimal("factor_rate");
            return loan;
        }
    }
}
