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
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditWithLoansData;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.accountdetails.data.LoanAccountSummaryData;
import org.apache.fineract.portfolio.accountdetails.service.AccountEnumerations;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.LoanApplicationTimelineData;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.apache.fineract.useradministration.data.AppUserData;
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
                    loc.activation_date as activationDate,
                    loc.currency as currency,
                    loc.advance_percentage as advancePercentage,
                    loc.tenor_days as tenorDays,
                    loc.cash_margin_type as cashMarginType,
                    loc.cash_margin_value as cashMarginValue,
                    loc.interim_review_date as interimReviewDate,
                    loc.rate_type as rateType,
                    loc.annual_interest_rate as annualInterestRate,
                    loc.is_interest_upfront_or_post_disbursal as isInterestUpfrontOrPostDisbursal,
                    loc.client_company_name as clientCompanyName,
                    loc.client_contact_person_name as clientContactPersonName,
                    loc.client_contact_person_phone as clientContactPersonPhone,
                    loc.client_contact_person_email as clientContactPersonEmail,
                    loc.authorized_signatory_name as authorizedSignatoryName,
                    loc.authorized_signatory_phone as authorizedSignatoryPhone,
                    loc.authorized_signatory_email as authorizedSignatoryEmail,
                    loc.va as virtualAccount,
                    loc.special_conditions as specialConditions,
                    loc.annual_interest_rate  as annualInterestRate,
                    loc.settlement_savings_account_id as settlementSavingsAccountId,
                    ssa.account_no as settlementSavingsAccountNo,
                    ssa.account_balance_derived as settlementSavingsAccountBalance,
                    loc.created_on_utc as createdDate,
                    loc.created_by as createdBy,
                    loc.last_modified_on_utc as lastModifiedDate,
                    loc.last_modified_by as lastModifiedBy,
                    mlocab.name as approvedBuyerName,
                    loc.activated_on_date as activatedOnDate,
                    loc.approved_on_date as approvedOnDate,
                    loc.closed_on_date as closedOnDate,
                    ap.firstname as approverFirstName,
                    ap.lastname as approverLastName,
                    ac.firstname as activatorFirstName,
                    ac.lastname as activatorLastName,
                    cl.firstname as closerFirstName,
                    cl.lastname as closerLastName
                    from
                    m_line_of_credit loc
                    left join m_savings_account ssa on
                    ssa.id = loc.settlement_savings_account_id
                    left join m_line_of_credit_approved_buyers mlocab on mlocab.line_of_credit_id = loc.id
                    left join m_appuser ac on ac.id = loc.activated_by_user_id
                    left join m_appuser ap on ap.id = loc.approved_by_user_id
                    left join m_appuser cl on cl.id = loc.closed_by_user_id
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
            final LocalDate activationDate = JdbcSupport.getLocalDate(rs, "activationDate");
            final String currency = rs.getString("currency");
            final BigDecimal advancePercentage = rs.getBigDecimal("advancePercentage");
            final Integer tenorDays = JdbcSupport.getInteger(rs, "tenorDays");
            final String cashMarginType = rs.getString("cashMarginType");
            final BigDecimal cashMarginValue = rs.getBigDecimal("cashMarginValue");
            final LocalDate interimReviewDate = JdbcSupport.getLocalDate(rs, "interimReviewDate");
            final String rateType = rs.getString("rateType");
            final BigDecimal annualInterestRate = rs.getBigDecimal("annualInterestRate");
            final String isInterestUpfrontOrPostDisbursal = rs.getString("isInterestUpfrontOrPostDisbursal");
            final String clientCompanyName = rs.getString("clientCompanyName");
            final String clientContactPersonName = rs.getString("clientContactPersonName");
            final String clientContactPersonPhone = rs.getString("clientContactPersonPhone");
            final String clientContactPersonEmail = rs.getString("clientContactPersonEmail");
            final String authorizedSignatoryName = rs.getString("authorizedSignatoryName");
            final String authorizedSignatoryPhone = rs.getString("authorizedSignatoryPhone");
            final String authorizedSignatoryEmail = rs.getString("authorizedSignatoryEmail");
            final String virtualAccount = rs.getString("virtualAccount");
            final String specialConditions = rs.getString("specialConditions");
            final Long settlementSavingsAccountId = JdbcSupport.getLong(rs, "settlementSavingsAccountId");
            final String settlementSavingsAccountNo = rs.getString("settlementSavingsAccountNo");
            final BigDecimal settlementSavingsAccountBalance = rs.getBigDecimal("settlementSavingsAccountBalance");
            final LocalDate createdDate = JdbcSupport.getLocalDate(rs, "createdDate");
            final String createdBy = rs.getString("createdBy");
            final LocalDate lastModifiedDate = JdbcSupport.getLocalDate(rs, "lastModifiedDate");
            final String lastModifiedBy = rs.getString("lastModifiedBy");
            final LocalDate activatedOnDate = JdbcSupport.getLocalDate(rs, "activatedOnDate");
            final LocalDate approvedOnDate = JdbcSupport.getLocalDate(rs, "approvedOnDate");
            final LocalDate closedOnDate = JdbcSupport.getLocalDate(rs, "closedOnDate");

            final AppUserData approver = createAppUserData(rs.getString("approverFirstName"), rs.getString("approverLastName"));
            final AppUserData activator = createAppUserData(rs.getString("activatorFirstName"), rs.getString("activatorLastName"));
            final AppUserData closer = createAppUserData(rs.getString("closerFirstName"), rs.getString("closerLastName"));

            return LineOfCreditData.builder().id(id).clientId(clientId).client(null).productType(productType).maximumAmount(maximumAmount)
                    .availableBalance(availableBalance).consumedAmount(consumedAmount)
                    .status(getActivationStatusEnumOptionData(activationStatus)).startDate(startDate).endDate(endDate)
                    .approvedCreditFacilityAmount(approvedCreditFacilityAmount).externalId(externalId).activationDate(activationDate)
                    .currency(currency).advancePercentage(advancePercentage).tenorDays(tenorDays).cashMarginType(cashMarginType)
                    .cashMarginValue(cashMarginValue).interimReviewDate(interimReviewDate).rateType(rateType)
                    .annualInterestRate(annualInterestRate).isInterestUpfrontOrPostDisbursal(isInterestUpfrontOrPostDisbursal)
                    .clientCompanyName(clientCompanyName).clientContactPersonName(clientContactPersonName)
                    .clientContactPersonPhone(clientContactPersonPhone).clientContactPersonEmail(clientContactPersonEmail)
                    .authorizedSignatoryName(authorizedSignatoryName).authorizedSignatoryPhone(authorizedSignatoryPhone)
                    .authorizedSignatoryEmail(authorizedSignatoryEmail).va(virtualAccount).specialConditions(specialConditions)
                    .settlementSavingsAccountId(settlementSavingsAccountId).settlementSavingsAccountNo(settlementSavingsAccountNo)
                    .settlementSavingsAccountBalance(settlementSavingsAccountBalance).createdDate(createdDate).createdByUsername(createdBy)
                    .lastModifiedDate(lastModifiedDate).lastModifiedByUsername(lastModifiedBy).activatedOnDate(activatedOnDate)
                    .approvedOnDate(approvedOnDate).closedOnDate(closedOnDate).approver(approver).activator(activator).closer(closer);
        }

        private AppUserData createAppUserData(String firstName, String lastName) {
            if (firstName != null || lastName != null) {
                String fullName = ((firstName != null ? firstName : "") + (lastName != null ? " " + lastName : "")).trim();
                return AppUserData.dropdown(null, fullName);
            }
            return null;
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

        private final boolean isSimpleQuery;

        LineOfCreditWithLoansMapper(boolean isSimpleQuery) {
            this.isSimpleQuery = isSimpleQuery;
        }

        public String schema() {
            if (isSimpleQuery) {
                return getSimpleSchema();
            } else {
                return getExhaustiveSchema();
            }
        }

        /**
         * Simple schema for listing LOCs - contains only the most important fields
         */
        private String getSimpleSchema() {
            final StringBuilder sql = new StringBuilder();

            // Essential LOC fields
            sql.append("loc.id as locId,loc.external_id as locExternalId, ")
                    .append("loc.maximum_amount as locCreditLimit, loc.available_balance as locBalance, ")
                    .append("loc.consumed_amount as locUtilizationAmount, loc.product_type as locType, loc.va as accountNumber, ")
                    .append("loc.activation_status as locActivationStatus, ")

                    // Essential Loan fields
                    .append("l.id as loanId, l.account_no as loanAccountNo, lp.name as loanProductName, ")
                    .append("l.principal_disbursed_derived as loanAmount, l.total_outstanding_derived as loanOutstandingBalance, ")
                    .append("l.total_repayment_derived as loanAmountPaid, l.loan_status_id as loanStatusId ")

                    .append("FROM m_line_of_credit loc ").append("LEFT JOIN m_loan l ON l.line_of_credit_id = loc.id ")
                    .append("LEFT JOIN m_product_loan lp ON lp.id = l.product_id ");

            return sql.toString();
        }

        /**
         * Exhaustive schema for detailed single LOC view - contains all fields
         */
        private String getExhaustiveSchema() {
            final StringBuilder sql = new StringBuilder();

            // LOC fields
            sql.append("loc.id as locId, loc.client_id as locClientId, ")
                    .append("loc.product_type as locProductType, loc.maximum_amount as locMaximumAmount, ")
                    .append("loc.available_balance as locAvailableBalance, loc.consumed_amount as locConsumedAmount, ")
                    .append("loc.activation_status as locActivationStatus, loc.start_date as locStartDate, ")
                    .append("loc.end_date as locEndDate, loc.approved_credit_facility_amount as locApprovedCreditFacilityAmount, ")
                    .append("loc.external_id as locExternalId, loc.activation_date as locActivationDate, ")
                    .append("loc.currency as locCurrency, loc.advance_percentage as locAdvancePercentage, ")
                    .append("loc.tenor_days as locTenorDays, loc.approved_buyers as locApprovedBuyers, ")
                    .append("loc.processing_fee_pct_loc as locProcessingFeePctLoc, loc.cash_margin_type as locCashMarginType, ")
                    .append("loc.cash_margin_value as locCashMarginValue, loc.inv_handling_fee_basis as locInvHandlingFeeBasis, ")
                    .append("loc.inv_handling_fee_pct as locInvHandlingFeePct, loc.inv_handling_fee_min_amount as locInvHandlingFeeMinAmount, ")
                    .append("loc.inv_handling_fee_currency as locInvHandlingFeeCurrency, loc.interim_review_date as locInterimReviewDate, ")
                    .append("loc.rate_type as locRateType, loc.annual_interest_rate as locAnnualInterestRate, ")
                    .append("loc.is_interest_upfront_or_post_disbursal as locIsInterestUpfrontOrPostDisbursal, ")
                    .append("loc.client_company_name as locClientCompanyName, loc.client_contact_person_name as locClientContactPersonName, ")
                    .append("loc.client_contact_person_phone as locClientContactPersonPhone, loc.client_contact_person_email as locClientContactPersonEmail, ")
                    .append("loc.authorized_signatory_name as locAuthorizedSignatoryName, loc.authorized_signatory_phone as locAuthorizedSignatoryPhone, ")
                    .append("loc.authorized_signatory_email as locAuthorizedSignatoryEmail, loc.va as locVa, ")
                    .append("loc.distribution_partner as locDistributionPartner, loc.bank_transfer_fee as locBankTransferFee, ")
                    .append("loc.special_conditions as locSpecialConditions, loc.late_payment_fee as locLatePaymentFee, ")
                    .append("loc.created_on_utc as locCreatedDate, loc.created_by as locCreatedBy, ")
                    .append("loc.last_modified_on_utc as locLastModifiedDate, loc.last_modified_by as locLastModifiedBy, loc.va as accountNumber, ")

                    // Loan fields
                    .append("l.id as loanId, l.account_no as loanAccountNo, l.external_id as loanExternalId, ")
                    .append("l.product_id as loanProductId, lp.name as loanProductName, lp.short_name as loanShortProductName, ")
                    .append("l.loan_status_id as loanStatusId, l.loan_type_enum as loanType, l.principal_disbursed_derived as loanOriginalLoan, ")
                    .append("l.total_outstanding_derived as loanBalance, l.total_repayment_derived as loanAmountPaid, ")
                    .append("l.loan_product_counter as loanCycle, l.currency_code as loanCurrencyCode, ")
                    .append("l.currency_digits as loanCurrencyDigits, l.currency_multiplesof as loanInMultiplesOf, ")
                    .append("curr.name as loanCurrencyName, curr.internationalized_name_code as loanCurrencyNameCode, ")
                    .append("curr.display_symbol as loanCurrencyDisplaySymbol, l.submittedon_date as loanSubmittedOnDate, ")
                    .append("l.approvedon_date as loanApprovedOnDate, l.expected_disbursedon_date as loanExpectedDisbursementDate, ")
                    .append("l.disbursedon_date as loanActualDisbursementDate, l.closedon_date as loanClosedOnDate, ")
                    .append("l.net_disbursal_amount as loanNetDisbursedAmount, l.fixed_emi_amount as loanInstallmentAmount ")

                    .append("FROM m_line_of_credit loc ").append("LEFT JOIN m_loan l ON l.line_of_credit_id = loc.id ")
                    .append("LEFT JOIN m_product_loan lp ON lp.id = l.product_id ")
                    .append("LEFT JOIN m_currency curr ON curr.code = l.currency_code ");

            return sql.toString();
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
                    LineOfCreditData locData = isSimpleQuery ? extractSimpleLineOfCreditData(rs) : extractExhaustiveLineOfCreditData(rs);
                    locWithLoans = new LineOfCreditWithLoansData(locData, new ArrayList<>());
                    locMap.put(locId, locWithLoans);
                }

                // Extract loan data if exists
                Long loanId = rs.getLong("loanId");
                if (loanId != null && loanId > 0) {
                    LoanAccountSummaryData loanData = isSimpleQuery ? extractSimpleLoanData(rs) : extractExhaustiveLoanData(rs);
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

            // Create simplified LOC data with essential fields only, including the activation status
            return LineOfCreditData.builder().id(id).productType(productType).maximumAmount(creditLimit).availableBalance(balance)
                    .consumedAmount(utilizationAmount).status(getActivationStatusEnumOptionData(activationStatus)).externalId(externalId)
                    .accountNumber(accountNumber).build();
        }

        /**
         * Extract complete LOC data for detailed view
         */
        private LineOfCreditData extractExhaustiveLineOfCreditData(ResultSet rs) throws SQLException {
            final Long id = rs.getLong("locId");
            final Long clientId = rs.getLong("locClientId");
            final String productType = rs.getString("locProductType");
            final BigDecimal maximumAmount = rs.getBigDecimal("locMaximumAmount");
            final BigDecimal availableBalance = rs.getBigDecimal("locAvailableBalance");
            final BigDecimal consumedAmount = rs.getBigDecimal("locConsumedAmount");
            final String activationStatus = rs.getString("locActivationStatus");
            final LocalDate startDate = rs.getDate("locStartDate") != null ? rs.getDate("locStartDate").toLocalDate() : null;
            final LocalDate endDate = rs.getDate("locEndDate") != null ? rs.getDate("locEndDate").toLocalDate() : null;
            final String externalId = rs.getString("locExternalId");
            final LocalDate activationDate = rs.getDate("locActivationDate") != null ? rs.getDate("locActivationDate").toLocalDate() : null;
            final String currency = rs.getString("locCurrency");
            final BigDecimal advancePercentage = rs.getBigDecimal("locAdvancePercentage");
            final Integer tenorDays = rs.getObject("locTenorDays") != null ? rs.getInt("locTenorDays") : null;
            final String approvedBuyers = rs.getString("locApprovedBuyers");
            final BigDecimal processingFeePctLoc = rs.getBigDecimal("locProcessingFeePctLoc");
            final String cashMarginType = rs.getString("locCashMarginType");
            final BigDecimal cashMarginValue = rs.getBigDecimal("locCashMarginValue");
            final LocalDate interimReviewDate = rs.getDate("locInterimReviewDate") != null
                    ? rs.getDate("locInterimReviewDate").toLocalDate()
                    : null;
            final String rateType = rs.getString("locRateType");
            final BigDecimal annualInterestRate = rs.getBigDecimal("locAnnualInterestRate");
            final String isInterestUpfrontOrPostDisbursal = rs.getString("locIsInterestUpfrontOrPostDisbursal");
            final String clientCompanyName = rs.getString("locClientCompanyName");
            final String clientContactPersonName = rs.getString("locClientContactPersonName");
            final String clientContactPersonPhone = rs.getString("locClientContactPersonPhone");
            final String clientContactPersonEmail = rs.getString("locClientContactPersonEmail");
            final String authorizedSignatoryName = rs.getString("locAuthorizedSignatoryName");
            final String authorizedSignatoryPhone = rs.getString("locAuthorizedSignatoryPhone");
            final String authorizedSignatoryEmail = rs.getString("locAuthorizedSignatoryEmail");
            final String distributionPartner = rs.getString("locDistributionPartner");
            final BigDecimal bankTransferFee = rs.getBigDecimal("locBankTransferFee");
            final String specialConditions = rs.getString("locSpecialConditions");
            final BigDecimal latePaymentFee = rs.getBigDecimal("locLatePaymentFee");
            final BigDecimal maxPerDrawdown = rs.getBigDecimal("locMaxPerDrawdown");
            final String reviewPeriod = rs.getString("reviewPeriod");
            final BigDecimal interestRateOverride = rs.getBigDecimal("interestRateOverride");
            final Long settlementSavingsAccountId = rs.getObject("settlementSavingsAccountId") != null
                    ? rs.getLong("settlementSavingsAccountId")
                    : null;
            final String settlementSavingsAccountNo = rs.getString("settlementSavingsAccountNo");
            final BigDecimal settlementSavingsAccountBalance = rs.getBigDecimal("settlementSavingsAccountBalance");

            final LocalDate createdDate = rs.getTimestamp("locCreatedDate") != null
                    ? rs.getTimestamp("locCreatedDate").toLocalDateTime().toLocalDate()
                    : null;
            final String createdBy = rs.getString("locCreatedBy");
            final LocalDate lastModifiedDate = rs.getTimestamp("lastModifiedDate") != null
                    ? rs.getTimestamp("lastModifiedDate").toLocalDateTime().toLocalDate()
                    : null;
            final String lastModifiedBy = rs.getString("locLastModifiedBy");
            final String accountNumber = rs.getString("accountNumber");

            return LineOfCreditData.builder().id(id).clientId(clientId).client(null).productType(productType).maximumAmount(maximumAmount)
                    .availableBalance(availableBalance).consumedAmount(consumedAmount)
                    .status(getActivationStatusEnumOptionData(activationStatus)).startDate(startDate).endDate(endDate)
                    .externalId(externalId).activationDate(activationDate).currency(currency).advancePercentage(advancePercentage)
                    .tenorDays(tenorDays).approvedBuyers(approvedBuyers).processingFeePctLoc(processingFeePctLoc)
                    .cashMarginType(cashMarginType).cashMarginValue(cashMarginValue).interimReviewDate(interimReviewDate).rateType(rateType)
                    .annualInterestRate(annualInterestRate).isInterestUpfrontOrPostDisbursal(isInterestUpfrontOrPostDisbursal)
                    .clientCompanyName(clientCompanyName).clientContactPersonName(clientContactPersonName)
                    .clientContactPersonPhone(clientContactPersonPhone).clientContactPersonEmail(clientContactPersonEmail)
                    .authorizedSignatoryName(authorizedSignatoryName).authorizedSignatoryPhone(authorizedSignatoryPhone)
                    .authorizedSignatoryEmail(authorizedSignatoryEmail).va(accountNumber).distributionPartner(distributionPartner)
                    .bankTransferFee(bankTransferFee).specialConditions(specialConditions).latePaymentFee(latePaymentFee)
                    .maxPerDrawdown(maxPerDrawdown).reviewPeriod(reviewPeriod).interestRateOverride(interestRateOverride)
                    .settlementSavingsAccountId(settlementSavingsAccountId).settlementSavingsAccountNo(settlementSavingsAccountNo)
                    .settlementSavingsAccountBalance(settlementSavingsAccountBalance).createdDate(createdDate).createdByUsername(createdBy)
                    .lastModifiedDate(lastModifiedDate).lastModifiedByUsername(lastModifiedBy).build();
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

            // Create loan status enum
            final LoanStatusEnumData loanStatus = loanStatusId != null ? LoanEnumerations.status(loanStatusId) : null;

            // Create simplified loan summary data with essential fields only
            return new LoanAccountSummaryData(id, accountNo, null, null, loanProductName, null, loanStatus, null, null, null, null, false,
                    loanAmount, outstandingBalance, amountPaid);
        }

        /**
         * Extract complete loan data for detailed view
         */
        private LoanAccountSummaryData extractExhaustiveLoanData(ResultSet rs) throws SQLException {
            final Long id = rs.getLong("loanId");
            final String accountNo = rs.getString("loanAccountNo");
            final String externalId = rs.getString("loanExternalId");
            final Long productId = rs.getLong("loanProductId");
            final String loanProductName = rs.getString("loanProductName");
            final String shortLoanProductName = rs.getString("loanShortProductName");
            final Integer loanStatusId = JdbcSupport.getInteger(rs, "loanStatusId");
            final Integer loanTypeId = JdbcSupport.getInteger(rs, "loanType");
            final Integer loanCycle = JdbcSupport.getInteger(rs, "loanCycle");

            final String currencyCode = rs.getString("loanCurrencyCode");
            final String currencyName = rs.getString("loanCurrencyName");
            final String currencyNameCode = rs.getString("loanCurrencyNameCode");
            final String currencyDisplaySymbol = rs.getString("loanCurrencyDisplaySymbol");
            final Integer currencyDigits = JdbcSupport.getInteger(rs, "loanCurrencyDigits");
            final Integer inMultiplesOf = JdbcSupport.getInteger(rs, "loanInMultiplesOf");

            final BigDecimal originalLoan = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "loanOriginalLoan");
            final BigDecimal loanBalance = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "loanBalance");
            final BigDecimal amountPaid = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "loanAmountPaid");

            final LocalDate submittedOnDate = JdbcSupport.getLocalDate(rs, "loanSubmittedOnDate");
            final LocalDate approvedOnDate = JdbcSupport.getLocalDate(rs, "loanApprovedOnDate");
            final LocalDate expectedDisbursementDate = JdbcSupport.getLocalDate(rs, "loanExpectedDisbursementDate");
            final LocalDate actualDisbursementDate = JdbcSupport.getLocalDate(rs, "loanActualDisbursementDate");
            final LocalDate closedOnDate = JdbcSupport.getLocalDate(rs, "loanClosedOnDate");

            // Create currency data
            final CurrencyData currency = currencyCode != null
                    ? new CurrencyData(currencyCode, currencyName, currencyDigits, inMultiplesOf, currencyDisplaySymbol, currencyNameCode)
                    : null;

            // Create loan status enum
            final LoanStatusEnumData loanStatus = loanStatusId != null ? LoanEnumerations.status(loanStatusId) : null;

            // Create loan type enum
            final EnumOptionData loanType = loanTypeId != null ? AccountEnumerations.loanType(loanTypeId) : null;

            // Create simplified timeline - you may want to expand this
            final LoanApplicationTimelineData timeline = new LoanApplicationTimelineData(submittedOnDate, null, null, null, null, null,
                    null, null, null, null, null, null, approvedOnDate, null, null, null, expectedDisbursementDate, actualDisbursementDate,
                    null, null, null, closedOnDate, null, null, null, null, null, null, null, null, null, null, null, null, null);
            return new LoanAccountSummaryData(id, accountNo, externalId, productId, loanProductName, shortLoanProductName, loanStatus,
                    currency, loanType, loanCycle, timeline, false, originalLoan, loanBalance, amountPaid);
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
    public LineOfCreditData retrieveTemplate() {
        this.context.authenticatedUser();
        final Collection<EnumOptionData> activationStatusOptions = getActivationStatusOptions();
        final Collection<EnumOptionData> productTypeOptions = getProductTypeOptions();
        final Collection<EnumOptionData> reviewPeriodsOptions = getReviewPeriodsOptions();

        return LineOfCreditData.template(activationStatusOptions, productTypeOptions, reviewPeriodsOptions);
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

    private Collection<EnumOptionData> getActivationStatusOptions() {
        return Arrays.stream(LocStatus.values()).map(LocStatus::getEnumOptionData).toList();
    }

    private Collection<EnumOptionData> getProductTypeOptions() {
        return Arrays.stream(LocProductType.values()).map(LocProductType::getEnumOptionsData).collect(Collectors.toList());
    }

    private Collection<EnumOptionData> getReviewPeriodsOptions() {
        return Arrays.stream(LocReviewPeriods.values()).map(LocReviewPeriods::getEnumOptionsData).collect(Collectors.toList());
    }

    /**
     * Retrieve all Line of Credits with loans for a specific client using simple query
     */
    @Override
    public List<LineOfCreditWithLoansData> retrieveLineOfCreditWithLoansForClient(Long clientId) {

        final LineOfCreditWithLoansMapper mapper = new LineOfCreditWithLoansMapper(true); // Simple query for listing
        final String sql = "SELECT " + mapper.schema() + " WHERE loc.client_id = ? ORDER BY loc.id, l.id";

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

}
