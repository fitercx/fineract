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

package com.crediblex.fineract.portfolio.loc.service;

import com.crediblex.fineract.portfolio.loc.charge.data.LocChargeData;
import com.crediblex.fineract.portfolio.loc.charge.service.LineOfCreditChargeReadService;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditData;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditSummary;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditTimeLineData;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditWithLoansData;
import com.crediblex.fineract.portfolio.loc.data.LocCashMarginType;
import com.crediblex.fineract.portfolio.loc.data.LocInterestChargeTime;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import com.crediblex.fineract.portfolio.loc.data.LocReviewPeriods;
import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.staff.data.StaffData;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.portfolio.accountdetails.data.LoanAccountSummaryData;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.LoanApplicationTimelineData;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LineOfCreditReadPlatformServiceImpl implements LineOfCreditReadPlatformService {

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;
    private final ClientReadPlatformService clientReadPlatformService;
    private final LineOfCreditChargeReadService chargeReadService;
    private final StaffReadPlatformService staffReadPlatformService;

    private static final class LineOfCreditExtractor implements ResultSetExtractor<LineOfCreditData> {

        public String schema() {
            return """
                    loc.id as id,
                     loc.client_id as clientId,
                     loc.external_id as externalId,
                     loc.product_type as productType,
                     loc.maximum_amount as maximumAmount,
                     loc.available_balance as availableBalance,
                     loc.consumed_amount as consumedAmount,
                     loc.total_draw_down_count_derived as totalDrawDownCountDerived,
                     loc.total_of_fees_derived as totalOfFeesDerived,
                     loc.net_outstanding_amount_derived as netOutstandingAmountDerived,
                     loc.activation_status as activationStatus,
                     loc.start_date as startDate,
                     loc.end_date as endDate,
                     loc.approved_credit_facility_amount as approvedCreditFacilityAmount,
                     loc.currency as currency,
                     loc.advance_percentage as advancePercentage,
                     loc.tenor_days as tenorDays,
                     loc.cash_margin_type as cashMarginType,
                     loc.cash_margin_value as cashMarginValue,
                     loc.interim_review_date as interimReviewDate,
                     loc.rate_type as rateType,
                     loc.annual_interest_rate as annualInterestRate,
                     loc.client_company_name as clientCompanyName,
                     loc.client_contact_person_name as clientContactPersonName,
                     loc.client_contact_person_phone as clientContactPersonPhone,
                     loc.client_contact_person_email as clientContactPersonEmail,
                     loc.authorized_signatory_name as authorizedSignatoryName,
                     loc.authorized_signatory_phone as authorizedSignatoryPhone,
                     loc.authorized_signatory_email as authorizedSignatoryEmail,
                     loc.va as virtualAccount,
                     loc.special_conditions as specialConditions,
                     loc.review_period as reviewPeriod,
                     loc.loan_officer_id as loanOfficerId,
                     loc.distribution_partner as distributionPartner,
                     loc.interest_charge_time as interestChargeTime,
                     loc.settlement_savings_account_id as settlementSavingsAccountId,
                     ssa.account_no as settlementSavingsAccountNo,
                     ssa.account_balance_derived as settlementSavingsAccountBalance,
                     mlocab.name as approvedBuyerName,
                     loc.closed_on_date as closedOnDate,
                     lo.display_name as loanOfficerName,
                     creator.firstname as locCreatorFirstName, creator.lastname as locCreatorLastname,  loc.created_on_utc as locCreatedOn,
                     lastmodifier.firstname as locLastModifierFirstName, lastmodifier.lastname as locLastModifierLastName,loc.last_modified_on_utc as locModifiedOn,
                     activator.firstname as locActivatorFirstName, activator.lastname as locActivatorLastName, loc.activated_on_date as locActivatedOnDate,
                     approver.firstname as locApproverFirstName, approver.lastname as locApproverLastName, loc.approved_on_date as locApprovedOnDate,
                     closer.firstname as locCloserFirstName, closer.lastname as locCloserLastName,loc.closed_on_date as locClosedOnDate
                     from
                     m_line_of_credit loc
                     left join m_savings_account ssa on ssa.id = loc.settlement_savings_account_id
                     left join m_line_of_credit_approved_buyers mlocab on mlocab.line_of_credit_id = loc.id
                     left join m_staff lo on lo.id = loc.loan_officer_id
                     LEFT JOIN m_appuser creator ON creator.id = loc.created_by
                     LEFT JOIN m_appuser lastmodifier ON lastmodifier.id = loc.last_modified_by
                     LEFT JOIN m_appuser activator ON activator.id = loc.activated_by_user_id
                     LEFT JOIN m_appuser approver ON approver.id = loc.approved_by_user_id
                     LEFT JOIN m_appuser closer ON closer.id = loc.closed_by_user_id
                    """;
        }

        @Override
        public LineOfCreditData extractData(ResultSet rs) throws SQLException, DataAccessException {
            if (!rs.next()) {
                return null;
            }

            List<String> approvedBuyersList = new ArrayList<>();
            LineOfCreditData.LineOfCreditDataBuilder builder = null;

            do {
                if (builder == null) {
                    builder = createLineOfCreditBuilder(rs);
                }

                final String approvedBuyerName = rs.getString("approvedBuyerName");
                if (approvedBuyerName != null && !approvedBuyersList.contains(approvedBuyerName)) {
                    approvedBuyersList.add(approvedBuyerName);
                }

            } while (rs.next());

            return builder.approvedBuyersList(approvedBuyersList).build();
        }

        private LineOfCreditData.LineOfCreditDataBuilder createLineOfCreditBuilder(ResultSet rs) throws SQLException {
            final Long id = rs.getLong("id");
            final Long clientId = rs.getLong("clientId");
            final String productType = rs.getString("productType");
            final BigDecimal maximumAmount = rs.getBigDecimal("maximumAmount");
            final BigDecimal availableBalance = rs.getBigDecimal("availableBalance");
            final BigDecimal consumedAmount = rs.getBigDecimal("consumedAmount");
            final String activationStatus = rs.getString("activationStatus");
            final LocalDate startDate = JdbcSupport.getLocalDate(rs, "startDate");
            final LocalDate endDate = JdbcSupport.getLocalDate(rs, "endDate");
            final BigDecimal approvedCreditFacilityAmount = rs.getBigDecimal("approvedCreditFacilityAmount");
            final String externalId = rs.getString("externalId");

            final String currency = rs.getString("currency");
            final BigDecimal advancePercentage = rs.getBigDecimal("advancePercentage");
            final Integer tenorDays = JdbcSupport.getInteger(rs, "tenorDays");
            final String cashMarginType = rs.getString("cashMarginType");
            final BigDecimal cashMarginValue = rs.getBigDecimal("cashMarginValue");
            final LocalDate interimReviewDate = JdbcSupport.getLocalDate(rs, "interimReviewDate");
            final String rateType = rs.getString("rateType");
            final BigDecimal annualInterestRate = rs.getBigDecimal("annualInterestRate");

            final String clientCompanyName = rs.getString("clientCompanyName");
            final String clientContactPersonName = rs.getString("clientContactPersonName");
            final String clientContactPersonPhone = rs.getString("clientContactPersonPhone");
            final String clientContactPersonEmail = rs.getString("clientContactPersonEmail");
            final String authorizedSignatoryName = rs.getString("authorizedSignatoryName");
            final String authorizedSignatoryPhone = rs.getString("authorizedSignatoryPhone");
            final String authorizedSignatoryEmail = rs.getString("authorizedSignatoryEmail");
            final String virtualAccount = rs.getString("virtualAccount");
            final String specialConditions = rs.getString("specialConditions");

            final Integer reviewPeriod = JdbcSupport.getInteger(rs, "reviewPeriod");
            final Long loanOfficerId = JdbcSupport.getLong(rs, "loanOfficerId");
            final String distributionPartner = rs.getString("distributionPartner");
            final String interestChargeTime = rs.getString("interestChargeTime");
            final String loanOfficerName = rs.getString("loanOfficerName");

            final Long settlementSavingsAccountId = JdbcSupport.getLong(rs, "settlementSavingsAccountId");
            final String settlementSavingsAccountNo = rs.getString("settlementSavingsAccountNo");
            final BigDecimal settlementSavingsAccountBalance = rs.getBigDecimal("settlementSavingsAccountBalance");

            final LocalDate createdDate = JdbcSupport.getLocalDate(rs, "locCreatedOn");
            final String createdByFirstName = rs.getString("locCreatorFirstName");
            final String createdByLastName = rs.getString("locCreatorLastname");

            final LocalDate lastModifiedDate = JdbcSupport.getLocalDate(rs, "locModifiedOn");
            final String lastModifiedByFirstName = rs.getString("locLastModifierFirstName");
            final String lastModifiedByLastName = rs.getString("locLastModifierLastName");

            final String activatorFirstName = rs.getString("locActivatorFirstName");
            final String activatorLastName = rs.getString("locActivatorLastName");
            final LocalDate activatedOnDate = JdbcSupport.getLocalDate(rs, "locActivatedOnDate");
            final String approverFirstName = rs.getString("locApproverFirstName");
            final String approverLastName = rs.getString("locApproverLastName");
            final LocalDate approvedOnDate = JdbcSupport.getLocalDate(rs, "locApprovedOnDate");
            final String closerFirstName = rs.getString("locCloserFirstName");
            final String closerLastName = rs.getString("locCloserLastName");
            final LocalDate closedOnDate = JdbcSupport.getLocalDate(rs, "locClosedOnDate");

            LineOfCreditTimeLineData timeLineData = LineOfCreditTimeLineData.builder().submittedOnDate(createdDate)
                    .submittedByFirstname(createdByFirstName).submittedByLastname(createdByLastName).activatedByLastname(activatorLastName)
                    .activatedByFirstname(activatorFirstName).activatedOnDate(activatedOnDate).approvedByFirstname(approverFirstName)
                    .approvedByLastname(approverLastName).approvedOnDate(approvedOnDate).closedByFirstname(closerFirstName)
                    .closedByLastname(closerLastName).closedOnDate(closedOnDate).updatedByFirstname(lastModifiedByFirstName)
                    .updatedByLastname(lastModifiedByLastName).updatedOnDate(lastModifiedDate).build();

            return LineOfCreditData.builder().id(id).clientId(clientId).client(null).productType(productType).maximumAmount(maximumAmount)
                    .availableBalance(availableBalance).consumedAmount(consumedAmount)
                    .status(getActivationStatusEnumOptionData(activationStatus)).startDate(startDate).endDate(endDate)
                    .approvedCreditFacilityAmount(approvedCreditFacilityAmount).externalId(externalId).currency(currency)
                    .advancePercentage(advancePercentage).tenorDays(tenorDays).cashMarginType(cashMarginType)
                    .cashMarginValue(cashMarginValue).interimReviewDate(interimReviewDate).rateType(rateType)
                    .interestChargeTime(interestChargeTime).annualInterestRate(annualInterestRate).clientCompanyName(clientCompanyName)
                    .clientContactPersonName(clientContactPersonName).clientContactPersonPhone(clientContactPersonPhone)
                    .clientContactPersonEmail(clientContactPersonEmail).authorizedSignatoryName(authorizedSignatoryName)
                    .authorizedSignatoryPhone(authorizedSignatoryPhone).authorizedSignatoryEmail(authorizedSignatoryEmail)
                    .va(virtualAccount).specialConditions(specialConditions).reviewPeriod(reviewPeriod).loanOfficerId(loanOfficerId)
                    .loanOfficerName(loanOfficerName).distributionPartner(distributionPartner)
                    .settlementSavingsAccountId(settlementSavingsAccountId).settlementSavingsAccountNo(settlementSavingsAccountNo)
                    .settlementSavingsAccountBalance(settlementSavingsAccountBalance).timeLineData(timeLineData);
        }

        private EnumOptionData getActivationStatusEnumOptionData(String status) {
            if (status == null) {
                return null;
            }
            return LocStatus.valueOf(status).getEnumOptionData();
        }
    }

    /**
     * New mapper that uses ResultSetExtractor to group Line of Credit with its associated loans Supports both simple
     * and exhaustive queries based on the use case
     */
    private static final class LineOfCreditWithLoansMapper implements ResultSetExtractor<List<LineOfCreditWithLoansData>> {

        public String schema() {

            return """
                    loc.id as locId,
                    loc.external_id as locExternalId,
                    loc.maximum_amount as locCreditLimit,
                    loc.available_balance as locBalance,
                    loc.consumed_amount as locUtilizationAmount,
                    loc.product_type as locType,
                    loc.va as accountNumber,
                    loc.activation_status as locActivationStatus,
                    loc.cash_margin_value as locCashMarginValue,
                    loc.start_date as startDate,
                    loc.end_date as endDate,
                    loc.currency as currency,
                    loc.tenor_days as tenorDays,
                    l.id as loanId,
                    l.account_no as loanAccountNo,
                    lp.name as loanProductName,
                    l.principal_disbursed_derived as loanAmount,
                    l.total_outstanding_derived as loanOutstandingBalance,
                    l.total_repayment_derived as loanAmountPaid,
                    l.loan_status_id as loanStatusId,
                    l.external_id as loanExternalId,
                    l.currency_digits as loanCurrencyDigits,
                    l.currency_multiplesof as loanInMultiplesOf,
                    l.submittedon_date as loanSubmittedOnDate,
                    l.approvedon_date as loanApprovedOnDate,
                    l.expected_disbursedon_date as loanExpectedDisbursementDate,
                    l.disbursedon_date as loanActualDisbursementDate,
                    l.closedon_date as loanClosedOnDate,
                    l.net_disbursal_amount as loanNetDisbursedAmount,
                    l.fixed_emi_amount as loanInstallmentAmount,
                    l.total_overpaid_derived as totalOverpaidDerived,
                    l.annual_nominal_interest_rate as loanAnnualNominalInterestRate,
                    la.overdue_since_date_derived as overdueSinceDate,
                    mlcp.invoice_no as invoiceNumber,
                    mlcp.approved_receivable_amount as approvedReceivableAmount,
                    mlcp.amount_after_advance as amountAfterAdvance,
                    mlcp.approved_payable_amount as approvedPayableAmount,
                    mlcp.amount_in_facility_currency as amountInFacilityCurrency,
                    mlcp.invoice_amount as invoiceAmount,
                    mlcp.advance_percentage as advancePercentage,
                    STRING_AGG(DISTINCT mlocab_loc.name, ', ') as buyerSupplierLoc,
                    STRING_AGG(DISTINCT mlocab.name, ', ') as buyerSupplierLoan
                    FROM m_line_of_credit loc
                    LEFT JOIN m_loan_line_of_credit_params mlcp ON mlcp.line_of_credit_id = loc.id
                    LEFT JOIN m_loan l ON l.id = mlcp.loan_id
                    LEFT JOIN m_loan_arrears_aging la on la.loan_id = l.id
                    LEFT JOIN m_product_loan lp ON lp.id = l.product_id
                    LEFT JOIN m_loan_approver_buyers_suppliers kcp ON kcp.loan_id = l.id
                    LEFT JOIN m_line_of_credit_approved_buyers mlocab ON mlocab.id = kcp.buyer_supplier_id
                    LEFT JOIN m_line_of_credit_approved_buyers mlocab_loc ON mlocab_loc.line_of_credit_id = loc.id

                    """;
        }

        public String groupBy() {
            return """
                      GROUP BY
                    loc.id, loc.external_id, loc.maximum_amount, loc.available_balance,
                    loc.consumed_amount, loc.product_type, loc.va, loc.activation_status,
                    l.id, l.account_no, lp.name, l.principal_disbursed_derived,
                    l.total_outstanding_derived, l.total_repayment_derived, l.loan_status_id,
                    l.external_id, l.currency_digits, l.currency_multiplesof,
                    l.submittedon_date, l.approvedon_date, l.expected_disbursedon_date,
                    l.disbursedon_date, l.closedon_date, l.net_disbursal_amount,
                    l.fixed_emi_amount, mlcp.invoice_no, l.total_overpaid_derived, l.annual_nominal_interest_rate, la.overdue_since_date_derived,
                    mlcp.approved_receivable_amount, mlcp.amount_after_advance, mlcp.approved_payable_amount, mlcp.invoice_amount,
                    mlcp.amount_in_facility_currency, mlcp.advance_percentage,
                    loc.start_date, loc.end_date, loc.currency, loc.cash_margin_value,
                    loc.tenor_days
                    """;
        }

        @Override
        public List<LineOfCreditWithLoansData> extractData(ResultSet rs) throws SQLException, DataAccessException {
            Map<Long, LineOfCreditWithLoansData> locMap = new HashMap<>();

            while (rs.next()) {
                Long locId = rs.getLong("locId");

                // Get or create LOC data
                LineOfCreditWithLoansData locWithLoans = locMap.get(locId);
                if (locWithLoans == null) {
                    // Extract LOC data based on query type
                    LineOfCreditData locData = extractSimpleLineOfCreditData(rs);
                    locWithLoans = new LineOfCreditWithLoansData(locData, new ArrayList<>());
                    locMap.put(locId, locWithLoans);
                }

                // Extract loan data if exists
                Long loanId = rs.getLong("loanId");
                if (loanId != null && loanId > 0) {
                    LoanAccountSummaryData loanData = extractSimpleLoanData(rs);
                    locWithLoans.getLoans().add(loanData);
                }
            }

            return new ArrayList<>(locMap.values());
        }

        /**
         * Extract essential LOC data for listing purposes
         */
        private LineOfCreditData extractSimpleLineOfCreditData(ResultSet rs) throws SQLException {
            final Long id = rs.getLong("locId");
            final String externalId = rs.getString("locExternalId");
            final BigDecimal creditLimit = rs.getBigDecimal("locCreditLimit");
            final BigDecimal balance = rs.getBigDecimal("locBalance");
            final BigDecimal utilizationAmount = rs.getBigDecimal("locUtilizationAmount");
            final String productType = rs.getString("locType");
            final String activationStatus = rs.getString("locActivationStatus");
            final String accountNumber = rs.getString("accountNumber");
            final LocalDate startDate = JdbcSupport.getLocalDate(rs, "startDate");
            final LocalDate endDate = JdbcSupport.getLocalDate(rs, "endDate");
            final String currency = rs.getString("currency");
            final Integer tenorDays = rs.getInt("tenorDays");
            final BigDecimal cashMarginValue = rs.getBigDecimal("locCashMarginValue");
            final String buyerSupplier = rs.getString("buyerSupplierLoc");
            List<String> buyerSupplierList = buyerSupplier == null || buyerSupplier.isBlank() ? Collections.emptyList()
                    : Arrays.stream(buyerSupplier.split(",\\s*")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();

            LineOfCreditData.LineOfCreditDataBuilder builder = LineOfCreditData.builder().id(id).productType(productType)
                    .maximumAmount(creditLimit).availableBalance(balance).consumedAmount(utilizationAmount)
                    .status(getActivationStatusEnumOptionData(activationStatus)).externalId(externalId).accountNumber(accountNumber)
                    .startDate(startDate).endDate(endDate).currency(currency).cashMarginValue(cashMarginValue).tenorDays(tenorDays);

            // Populate buyers vs suppliers based on product type
            if (LocProductType.PAYABLE.name().equalsIgnoreCase(productType)) {
                builder.approvedSuppliers(buyerSupplierList);
            } else if (LocProductType.RECEIVABLE.name().equalsIgnoreCase(productType)) {
                builder.approvedBuyers(buyerSupplierList);
            }

            return builder.build();
        }

        /**
         * Extract essential loan data for listing purposes
         */
        private LoanAccountSummaryData extractSimpleLoanData(ResultSet rs) throws SQLException {
            final Long id = rs.getLong("loanId");
            final String accountNo = rs.getString("loanAccountNo");
            final String loanProductName = rs.getString("loanProductName");
            final BigDecimal loanAmount = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "loanAmount");
            final BigDecimal outstandingBalance = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "loanOutstandingBalance");
            final BigDecimal amountPaid = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "loanAmountPaid");
            final Integer loanStatusId = JdbcSupport.getInteger(rs, "loanStatusId");

            final LocalDate submittedOnDate = JdbcSupport.getLocalDate(rs, "loanSubmittedOnDate");
            final LocalDate approvedOnDate = JdbcSupport.getLocalDate(rs, "loanApprovedOnDate");
            final LocalDate expectedDisbursementDate = JdbcSupport.getLocalDate(rs, "loanExpectedDisbursementDate");
            final LocalDate actualDisbursementDate = JdbcSupport.getLocalDate(rs, "loanActualDisbursementDate");
            final LocalDate closedOnDate = JdbcSupport.getLocalDate(rs, "loanClosedOnDate");
            // Create loan status enum
            final LoanStatusEnumData loanStatus = loanStatusId != null ? LoanEnumerations.status(loanStatusId) : null;

            final String invoiceNumber = rs.getString("invoiceNumber");
            final BigDecimal totalOverpaidDerived = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "totalOverpaidDerived");
            final String buyerSupplierDetail = rs.getString("buyerSupplierLoan");

            final LocalDate overdueSinceDate = JdbcSupport.getLocalDate(rs, "overdueSinceDate");
            Boolean inArrears = (overdueSinceDate != null);

            final BigDecimal approvedReceivableAmount = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "approvedReceivableAmount");
            final BigDecimal amountAfterAdvance = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "amountAfterAdvance");
            final BigDecimal approvedPayableAmount = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "approvedPayableAmount");
            final BigDecimal invoiceAmount = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "invoiceAmount");
            final BigDecimal amountInFacilityCurrency = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "amountInFacilityCurrency");
            final BigDecimal advancePercentage = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "advancePercentage");
            final BigDecimal annualNominalInterestRate = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "loanAnnualNominalInterestRate");

            final LoanApplicationTimelineData timeline = new LoanApplicationTimelineData(submittedOnDate, null, null, null, null, null,
                    null, null, null, null, null, null, approvedOnDate, null, null, null, expectedDisbursementDate, actualDisbursementDate,
                    null, null, null, closedOnDate, null, null, null, null, null, null, null, null, null, null, null, null, null);

            // Create simplified loan summary data with essential fields only
            LoanAccountSummaryData summaryData = new LoanAccountSummaryData(id, accountNo, null, null, loanProductName, null, loanStatus,
                    null, null, null, timeline, inArrears, loanAmount, outstandingBalance, amountPaid);
            summaryData.setInvoiceNumber(invoiceNumber);
            summaryData.setTotalOverPaidDerived(totalOverpaidDerived);
            summaryData.setSupplierBuyerName(buyerSupplierDetail);

            summaryData.getAdditionalProperties().put("approvedReceivableAmount", approvedReceivableAmount);
            summaryData.getAdditionalProperties().put("amountAfterAdvance", amountAfterAdvance);
            summaryData.getAdditionalProperties().put("amountInFacilityCurrency", amountInFacilityCurrency);
            summaryData.getAdditionalProperties().put("approvedPayableAmount", approvedPayableAmount);
            summaryData.getAdditionalProperties().put("invoiceAmount", invoiceAmount);
            summaryData.getAdditionalProperties().put("advancePercentage", advancePercentage);
            summaryData.getAdditionalProperties().put("interestRate", annualNominalInterestRate);
            return summaryData;
        }

        private EnumOptionData getActivationStatusEnumOptionData(String status) {
            if (status == null) {
                return null;
            }
            return LocStatus.valueOf(status).getEnumOptionData();
        }
    }

    @Override
    public LineOfCreditData retrieveOne(Long lineOfCreditId, Long clientId) {
        this.context.authenticatedUser();
        try {
            final LineOfCreditExtractor extractor = new LineOfCreditExtractor();
            String sql = "select " + extractor.schema() + " where loc.id = ?";

            Object[] queryParams = new Object[] { lineOfCreditId };
            if (clientId != null) {
                sql = sql.concat(" and loc.client_id = ?");
                queryParams = new Object[] { lineOfCreditId, clientId };
            }

            final LineOfCreditData lineOfCredit = this.jdbcTemplate.query(sql, extractor, queryParams); // NOSONAR

            return enrichWithClientData(lineOfCredit);
        } catch (final EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public LineOfCreditData retrieveTemplate(Long clientId) {
        final Collection<EnumOptionData> activationStatusOptions = Arrays.stream(LocStatus.values()).map(LocStatus::getEnumOptionData)
                .toList();

        final Collection<EnumOptionData> productTypeOptions = Arrays.stream(LocProductType.values()).map(LocProductType::getEnumOptionsData)
                .collect(Collectors.toList());
        final Collection<EnumOptionData> reviewPeriodsOptions = Arrays.stream(LocReviewPeriods.values())
                .map(LocReviewPeriods::getEnumOptionsData).collect(Collectors.toList());
        final Collection<EnumOptionData> cashMarginTypeOptions = Arrays.stream(LocCashMarginType.values())
                .map(LocCashMarginType::getEnumOptionsData).toList();
        final Collection<EnumOptionData> interestChargeTime = Arrays.stream(LocInterestChargeTime.values())
                .map(LocInterestChargeTime::getEnumOptionsData).toList();

        Collection<StaffData> loanOfficers = null;
        Long officeId = this.context.authenticatedUser().getOffice().getId();

        if (officeId != null) {
            loanOfficers = this.staffReadPlatformService.retrieveAllLoanOfficersInOfficeById(officeId);
        }

        String virtualAccountNumber = getVirtualAccountNumber(clientId);

        return LineOfCreditData.builder().statusOptions(activationStatusOptions).productTypeOptions(productTypeOptions)
                .reviewPeriodsOptions(reviewPeriodsOptions).cashMarginTypeOptions(cashMarginTypeOptions).loanOfficers(loanOfficers)
                .va(virtualAccountNumber).interestChargeTimeOptions(interestChargeTime).build();
    }

    private String getVirtualAccountNumber(Long clientId) {
        String sql = "select virtual_account_number from dt_client_additional_data  where client_id = ?";

        try {
            return jdbcTemplate.queryForObject(sql, String.class, clientId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public Collection<LineOfCreditData> retrieveAllLineOfCreditsForClient(Long clientId) {
        this.context.authenticatedUser();

        final LineOfCreditExtractor extractor = new LineOfCreditExtractor();
        final String sql = "select " + extractor.schema() + " where loc.client_id = ? order by loc.id";

        return Collections.singletonList(this.jdbcTemplate.query(sql, extractor, clientId));
    }

    private LineOfCreditData enrichWithClientData(LineOfCreditData lineOfCredit) {
        if (lineOfCredit.getClientId() != null) {
            final ClientData clientData = this.clientReadPlatformService.retrieveOne(lineOfCredit.getClientId());
            lineOfCredit.setClient(clientData);
        }
        return lineOfCredit;
    }

    /**
     * Retrieve all Line of Credits with loans for a specific client using simple query
     */
    @Override
    public List<LineOfCreditWithLoansData> retrieveLineOfCreditWithLoansForClient(Long clientId) {

        final LineOfCreditWithLoansMapper mapper = new LineOfCreditWithLoansMapper(); // Simple query for listing
        final String sql = "SELECT " + mapper.schema() + " WHERE loc.client_id = ? " + mapper.groupBy() + " ORDER BY loc.id, l.id";

        return this.jdbcTemplate.query(sql, mapper, clientId);
    }

    @Override
    public LineOfCreditData retrieveOneWithCharges(Long lineOfCreditId, Long clientId) {

        try {
            LineOfCreditData lineOfCredit = retrieveOne(lineOfCreditId, clientId);
            Collection<LocChargeData> charges = chargeReadService.listActive(lineOfCreditId);
            lineOfCredit.setCharges(charges);

            return lineOfCredit;

        } catch (final EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public Integer getTotalOfActiveLoans(Long lineOfCreditId) {

        try {
            final String sql = "SELECT COUNT(*) FROM m_loan_line_of_credit_params mlcp " + "JOIN m_loan l ON l.id = mlcp.loan_id "
                    + "WHERE mlcp.line_of_credit_id = ? AND (l.loan_status_id = 300 or (l.loan_status_id = 600 and l.loan_sub_status_id is null))"; // 300:
                                                                                                                                                    // Active,
                                                                                                                                                    // 600:
            // Overpaid

            return this.jdbcTemplate.queryForObject(sql, Integer.class, lineOfCreditId);
        } catch (final EmptyResultDataAccessException e) {
            return 0;
        }
    }

    @Override
    public List<LineOfCreditSummary> retrieveSummary(String currencyCode, Long clientId, LocProductType locProductType) {
        final String sql = """
                SELECT
                    l.id,
                    l.external_id as externalId,
                    l.product_type as productType,
                    l.annual_interest_rate as annualInterestRate,
                    l.available_balance as availableBalance,
                    l.advance_percentage as advancePercentage,
                    l.tenor_days as tenorInDays,
                    l.loan_officer_id as loanOfficerId,
                    ab.name AS approvedBuyerName,
                    ab.id as approvedBuyerId
                FROM m_line_of_credit l
                LEFT JOIN m_line_of_credit_approved_buyers ab ON ab.line_of_credit_id = l.id
                WHERE l.activation_status = 'ACTIVE'
                  AND l.currency  = ?
                  AND l.client_id = ?
                  AND l.product_type = ?
                ORDER BY l.id
                """;

        return this.jdbcTemplate.query(sql, ps -> {
            ps.setString(1, currencyCode);
            ps.setLong(2, clientId);
            ps.setString(3, locProductType.name());
        }, rs -> {
            Map<Long, LineOfCreditSummary> map = new LinkedHashMap<>();

            while (rs.next()) {
                Long id = rs.getLong("id");
                LineOfCreditSummary builder = map.computeIfAbsent(id, k -> {

                    try {
                        String externalId = rs.getString("externalId");
                        String productTypeStr = rs.getString("productType");
                        LocProductType productType = productTypeStr != null ? LocProductType.valueOf(productTypeStr) : null;

                        return LineOfCreditSummary.builder().id(id).externalId(externalId).productType(productType)
                                .interestRate(rs.getBigDecimal("annualInterestRate")).availableBalance(rs.getBigDecimal("availableBalance"))
                                .advancePercentage(rs.getBigDecimal("advancePercentage")).tenorDays((Integer) rs.getObject("tenorInDays"))
                                .loanOfficerId((Long) rs.getObject("loanOfficerId")).approvedBuyersOrSellers(new ArrayList<>()).build();
                    } catch (SQLException e) {
                        throw new DataAccessException("Error building LineOfCreditSummary", e) {};
                    }
                });

                String buyerName = rs.getString("approvedBuyerName");
                if (buyerName != null && !buyerName.isBlank()) {
                    Long approvedBuyerId = rs.getLong("approvedBuyerId");
                    builder.getApprovedBuyersOrSellers().add(new LineOfCreditSummary.ApprovedBuyerOrSeller(approvedBuyerId, buyerName));
                }
            }

            return map.values().stream().toList();
        });
    }
}
