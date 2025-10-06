
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

package com.crediblex.fineract.portfolio.accountdetails.service;

import com.crediblex.fineract.portfolio.accountdetails.data.AccountDataAdditionalProperties;
import com.crediblex.fineract.portfolio.accountdetails.data.ExtendedLoanAccountSummaryData;
import com.crediblex.fineract.portfolio.accountdetails.data.ExtendedSavingsAccountSummaryData;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.accountdetails.data.LoanAccountSummaryData;
import org.apache.fineract.portfolio.accountdetails.data.SavingsAccountSummaryData;
import org.apache.fineract.portfolio.accountdetails.service.AccountDetailsReadPlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.accountdetails.service.AccountEnumerations;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.delinquency.service.DelinquencyReadPlatformService;
import org.apache.fineract.portfolio.group.service.GroupReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.CollectionData;
import org.apache.fineract.portfolio.loanaccount.data.LoanApplicationTimelineData;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.apache.fineract.portfolio.savings.data.SavingsAccountApplicationTimelineData;
import org.apache.fineract.portfolio.savings.data.SavingsAccountStatusEnumData;
import org.apache.fineract.portfolio.savings.data.SavingsAccountSubStatusEnumData;
import org.apache.fineract.portfolio.savings.service.SavingsEnumerations;
import org.apache.logging.log4j.util.Strings;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CredXAccountDetailsReadPlatformServiceJpaRepositoryImpl extends AccountDetailsReadPlatformServiceJpaRepositoryImpl {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DelinquencyReadPlatformService delinquencyReadPlatformService;

    public CredXAccountDetailsReadPlatformServiceJpaRepositoryImpl(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            ClientReadPlatformService clientReadPlatformService, GroupReadPlatformService groupReadPlatformService,
            ColumnValidator columnValidator, DelinquencyReadPlatformService delinquencyReadPlatformService) {
        super(namedParameterJdbcTemplate.getJdbcTemplate(), clientReadPlatformService, groupReadPlatformService, columnValidator);
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.delinquencyReadPlatformService = delinquencyReadPlatformService;
    }

    @Override
    protected List<LoanAccountSummaryData> retrieveLoanAccountDetails(String loanwhereClause, Object[] inputs) {
        final CredXLoanAccountSummaryDataMapper rm = new CredXLoanAccountSummaryDataMapper();

        // Convert positional parameters to named parameters in the where clause and build parameter map
        // We are doing this because the base fineract where clause uses positional parameters
        String namedWhereClause = loanwhereClause;
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (inputs != null) {
            String[] whereParts = loanwhereClause.split("\\?");
            for (int i = 0; i < inputs.length && i < whereParts.length; i++) {
                String paramName = "param" + i;
                if (whereParts[i].contains("client_id")) {
                    paramName = "clientId";
                } else if (whereParts[i].contains("group_id")) {
                    paramName = "groupId";
                } else if (whereParts[i].contains("loan_officer_id")) {
                    paramName = "loanOfficerId";
                } else if (whereParts[i].contains("loan_status_id")) {
                    paramName = "loanStatusId";
                } else if (whereParts[i].contains("loan_type_enum")) {
                    paramName = "loanTypeEnum";
                } else if (whereParts[i].contains("account_number")) {
                    paramName = "accountNumber";
                }

                namedWhereClause = namedWhereClause.replaceFirst("\\?", ":" + paramName);
                params.addValue(paramName, inputs[i]);
            }
        }

        namedWhereClause = namedWhereClause + " and mlcp.line_of_credit_id is null ";
        String currentDate = DateUtils.getLocalDateOfTenant().toString();
        params.addValue("currentDate", currentDate);

        final String sql = "select " + rm.loanAccountSummarySchema() + namedWhereClause;
        super.columnValidator.validateSqlInjection(rm.loanAccountSummarySchema(), namedWhereClause);

        List<LoanAccountSummaryData> result = this.namedParameterJdbcTemplate.query(sql, params, rm);
        for (LoanAccountSummaryData loan : result) {
            Long loanId = loan.getId();
            CollectionData collectionData = this.delinquencyReadPlatformService.calculateLoanCollectionData(loanId);
            Long daysPastDue = collectionData.getPastDueDays();

            if (loan instanceof ExtendedLoanAccountSummaryData extendedLoanAccountSummaryData) {
                extendedLoanAccountSummaryData.addCustomParameter(AccountDataAdditionalProperties.DAYS_PAST_DUE, daysPastDue);
            }
        }
        return result;
    }

    @Override
    protected List<SavingsAccountSummaryData> retrieveAccountDetails(final String savingswhereClause, final Object[] inputs) {
        final CredXSavingsAccountSummaryDataMapper savingsAccountSummaryDataMapper = new CredXSavingsAccountSummaryDataMapper();
        final String savingsSql = "select " + savingsAccountSummaryDataMapper.schema() + savingswhereClause;
        this.columnValidator.validateSqlInjection(savingsAccountSummaryDataMapper.schema(), savingswhereClause);
        return this.jdbcTemplate.query(savingsSql, savingsAccountSummaryDataMapper, inputs); // NOSONAR
    }

    /**
     * Public method to get savings account details with linked loan information Used by external services like
     * JournalEntryOdooTrackingService
     */
    public List<SavingsAccountSummaryData> getSavingsAccountDetails(final String whereClause, final Object[] inputs) {
        return retrieveAccountDetails(whereClause, inputs);
    }

    private static final class CredXLoanAccountSummaryDataMapper implements RowMapper<LoanAccountSummaryData> {

        public String loanAccountSummarySchema() {

            final StringBuilder accountsSummary = new StringBuilder("l.id as id, l.account_no as accountNo, l.external_id as externalId,");
            accountsSummary.append(" l.product_id as productId, lp.name as productName, lp.short_name as shortProductName,").append(
                    " l.loan_status_id as statusId, l.loan_type_enum as loanType, l.is_forced_closure as isForcedClosure, l.is_restructured as isRestructured,")

                    .append(" glim.account_number as parentAccountNumber,")

                    .append("l.principal_disbursed_derived as originalLoan,").append("l.total_outstanding_derived as loanBalance,")
                    .append("l.total_repayment_derived as amountPaid,")

                    .append(" l.loan_product_counter as loanCycle,")

                    .append("l.currency_code as currencyCode, l.currency_digits as currencyDigits, l.currency_multiplesof as inMultiplesOf,")

                    .append("curr.name as currencyName, curr.internationalized_name_code as currencyNameCode,")
                    .append("curr.display_symbol as currencyDisplaySymbol,")

                    .append(" l.submittedon_date as submittedOnDate,")
                    .append(" sbu.username as submittedByUsername, sbu.firstname as submittedByFirstname, sbu.lastname as submittedByLastname,")

                    .append(" l.rejectedon_date as rejectedOnDate,")
                    .append(" rbu.username as rejectedByUsername, rbu.firstname as rejectedByFirstname, rbu.lastname as rejectedByLastname,")

                    .append(" l.withdrawnon_date as withdrawnOnDate,")
                    .append(" wbu.username as withdrawnByUsername, wbu.firstname as withdrawnByFirstname, wbu.lastname as withdrawnByLastname,")

                    .append(" l.approvedon_date as approvedOnDate,")
                    .append(" abu.username as approvedByUsername, abu.firstname as approvedByFirstname, abu.lastname as approvedByLastname,")

                    .append(" l.expected_disbursedon_date as expectedDisbursementDate, l.disbursedon_date as actualDisbursementDate,")
                    .append(" dbu.username as disbursedByUsername, dbu.firstname as disbursedByFirstname, dbu.lastname as disbursedByLastname,")

                    .append(" l.closedon_date as closedOnDate,")
                    .append(" cbu.username as closedByUsername, cbu.firstname as closedByFirstname, cbu.lastname as closedByLastname,")
                    .append(" la.overdue_since_date_derived as overdueSinceDate, ")
                    .append(" l.writtenoffon_date as writtenOffOnDate, l.maturedon_date as actualMaturityDate, l.expected_maturedon_date as expectedMaturityDate, ")
                    .append(" l.charged_off_on_date as chargedOffOnDate, cobu.username as chargedOffByUsername, ")
                    .append(" cobu.firstname as chargedOffByFirstname, cobu.lastname as chargedOffByLastname, ")

                    // Adding new fields
                    .append(" l.net_disbursal_amount as netDisbursedAmount,").append(" l.fixed_emi_amount as installmentAmount, ")
                    .append(" CASE WHEN l.fixed_emi_amount IS NULL OR l.fixed_emi_amount = 0 THEN ")
                    .append("   (l.principal_amount + COALESCE(l.interest_charged_derived, 0)) / NULLIF(l.number_of_repayments, 0) ")
                    .append(" ELSE l.fixed_emi_amount END as calculatedInstallmentAmount, ")
                    .append(" (SELECT SUM(lc.amount_outstanding_derived) FROM m_loan_charge lc ")
                    .append("  WHERE lc.loan_id = l.id AND lc.is_penalty = true AND lc.is_active = true and due_for_collection_as_of_date < CAST(:currentDate AS DATE)) as totalLateFees,")
                    .append(" dlad.remitter_name as remitterName,").append(" dlad.dp_name as dpName")

                    .append(" from m_loan l ").append("LEFT JOIN m_product_loan AS lp ON lp.id = l.product_id")
                    .append(" left join m_currency curr on curr.code = l.currency_code")
                    .append(" left join m_appuser sbu on sbu.id = l.created_by")
                    .append(" left join m_appuser rbu on rbu.id = l.rejectedon_userid")
                    .append(" left join m_appuser wbu on wbu.id = l.withdrawnon_userid")
                    .append(" left join m_appuser abu on abu.id = l.approvedon_userid")
                    .append(" left join m_appuser dbu on dbu.id = l.disbursedon_userid")
                    .append(" left join m_appuser cbu on cbu.id = l.closedon_userid")
                    .append(" left join m_appuser cobu on cobu.id = l.charged_off_by_userid")
                    .append(" left join m_loan_arrears_aging la on la.loan_id = l.id")
                    .append(" left join glim_accounts glim on glim.id=l.glim_id")
                    .append(" left join dt_loan_additional_data dlad on dlad.loan_id = l.id")
                    .append(" left join m_loan_line_of_credit_params  mlcp on mlcp.loan_id = l.id");

            return accountsSummary.toString();
        }

        @Override
        public LoanAccountSummaryData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final Long id = JdbcSupport.getLong(rs, "id");
            final String accountNo = rs.getString("accountNo");
            final String parentAccountNumber = rs.getString("parentAccountNumber");
            final String externalId = rs.getString("externalId");
            final Long productId = JdbcSupport.getLong(rs, "productId");
            final String loanProductName = rs.getString("productName");
            final String shortLoanProductName = rs.getString("shortProductName");
            final Integer loanStatusId = JdbcSupport.getInteger(rs, "statusId");
            final LoanStatusEnumData loanStatus = LoanEnumerations.status(loanStatusId);
            final Integer loanTypeId = JdbcSupport.getInteger(rs, "loanType");
            final EnumOptionData loanType = AccountEnumerations.loanType(loanTypeId);
            final Integer loanCycle = JdbcSupport.getInteger(rs, "loanCycle");

            final String currencyCode = rs.getString("currencyCode");
            final String currencyName = rs.getString("currencyName");
            final String currencyNameCode = rs.getString("currencyNameCode");
            final String currencyDisplaySymbol = rs.getString("currencyDisplaySymbol");
            final Integer currencyDigits = JdbcSupport.getInteger(rs, "currencyDigits");
            final Integer inMultiplesOf = JdbcSupport.getInteger(rs, "inMultiplesOf");
            final CurrencyData currency = new CurrencyData(currencyCode, currencyName, currencyDigits, inMultiplesOf, currencyDisplaySymbol,
                    currencyNameCode);

            final LocalDate submittedOnDate = JdbcSupport.getLocalDate(rs, "submittedOnDate");
            final String submittedByUsername = rs.getString("submittedByUsername");
            final String submittedByFirstname = rs.getString("submittedByFirstname");
            final String submittedByLastname = rs.getString("submittedByLastname");

            final LocalDate rejectedOnDate = JdbcSupport.getLocalDate(rs, "rejectedOnDate");
            final String rejectedByUsername = rs.getString("rejectedByUsername");
            final String rejectedByFirstname = rs.getString("rejectedByFirstname");
            final String rejectedByLastname = rs.getString("rejectedByLastname");

            final LocalDate withdrawnOnDate = JdbcSupport.getLocalDate(rs, "withdrawnOnDate");
            final String withdrawnByUsername = rs.getString("withdrawnByUsername");
            final String withdrawnByFirstname = rs.getString("withdrawnByFirstname");
            final String withdrawnByLastname = rs.getString("withdrawnByLastname");

            final LocalDate approvedOnDate = JdbcSupport.getLocalDate(rs, "approvedOnDate");
            final String approvedByUsername = rs.getString("approvedByUsername");
            final String approvedByFirstname = rs.getString("approvedByFirstname");
            final String approvedByLastname = rs.getString("approvedByLastname");

            final LocalDate expectedDisbursementDate = JdbcSupport.getLocalDate(rs, "expectedDisbursementDate");
            final LocalDate actualDisbursementDate = JdbcSupport.getLocalDate(rs, "actualDisbursementDate");
            final String disbursedByUsername = rs.getString("disbursedByUsername");
            final String disbursedByFirstname = rs.getString("disbursedByFirstname");
            final String disbursedByLastname = rs.getString("disbursedByLastname");

            final LocalDate closedOnDate = JdbcSupport.getLocalDate(rs, "closedOnDate");
            final String closedByUsername = rs.getString("closedByUsername");
            final String closedByFirstname = rs.getString("closedByFirstname");
            final String closedByLastname = rs.getString("closedByLastname");

            final BigDecimal originalLoan = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "originalLoan");
            final BigDecimal loanBalance = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "loanBalance");
            final BigDecimal amountPaid = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "amountPaid");

            final LocalDate writtenOffOnDate = JdbcSupport.getLocalDate(rs, "writtenOffOnDate");

            final LocalDate expectedMaturityDate = JdbcSupport.getLocalDate(rs, "expectedMaturityDate");
            final LocalDate actualMaturityDate = JdbcSupport.getLocalDate(rs, "actualMaturityDate");

            final LocalDate overdueSinceDate = JdbcSupport.getLocalDate(rs, "overdueSinceDate");

            final LocalDate chargedOffOnDate = JdbcSupport.getLocalDate(rs, "chargedOffOnDate");
            final String chargedOffByUsername = rs.getString("chargedOffByUsername");
            final String chargedOffByFirstname = rs.getString("chargedOffByFirstname");
            final String chargedOffByLastname = rs.getString("chargedOffByLastname");

            Boolean inArrears = (overdueSinceDate != null);

            // New fields
            final BigDecimal installmentAmount = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "installmentAmount");
            final BigDecimal calculatedInstallmentAmount = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "calculatedInstallmentAmount");
            final BigDecimal totalLateFees = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "totalLateFees");
            final BigDecimal netDisbursedAmount = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "netDisbursedAmount");
            final String remitterName = rs.getString("remitterName");
            final String dpName = rs.getString("dpName");
            final Boolean isForcedClosure = rs.getBoolean("isForcedClosure");
            final Boolean isRestructured = rs.getBoolean("isRestructured");

            // Use calculated installment amount if fixed EMI is not set
            final BigDecimal effectiveInstallmentAmount = (installmentAmount != null && installmentAmount.compareTo(BigDecimal.ZERO) > 0)
                    ? installmentAmount
                    : calculatedInstallmentAmount;

            final LoanApplicationTimelineData timeline = new LoanApplicationTimelineData(submittedOnDate, submittedByUsername,
                    submittedByFirstname, submittedByLastname, rejectedOnDate, rejectedByUsername, rejectedByFirstname, rejectedByLastname,
                    withdrawnOnDate, withdrawnByUsername, withdrawnByFirstname, withdrawnByLastname, approvedOnDate, approvedByUsername,
                    approvedByFirstname, approvedByLastname, expectedDisbursementDate, actualDisbursementDate, disbursedByUsername,
                    disbursedByFirstname, disbursedByLastname, closedOnDate, closedByUsername, closedByFirstname, closedByLastname,
                    actualMaturityDate, expectedMaturityDate, writtenOffOnDate, closedByUsername, closedByFirstname, closedByLastname,
                    chargedOffOnDate, chargedOffByUsername, chargedOffByFirstname, chargedOffByLastname);

            // Note: You'll need to modify the LoanAccountSummaryData constructor to accept the new fields
            // or use a builder pattern if available. The exact implementation depends on your LoanAccountSummaryData
            // class.
            ExtendedLoanAccountSummaryData extendedLoanAccountSummaryData = new ExtendedLoanAccountSummaryData(id, accountNo,
                    parentAccountNumber, externalId, productId, loanProductName, shortLoanProductName, loanStatus, currency, loanType,
                    loanCycle, timeline, inArrears, originalLoan, loanBalance, amountPaid);

            extendedLoanAccountSummaryData.addCustomParameter(AccountDataAdditionalProperties.EFFECTIVE_INSTALLMENT_AMOUNT,
                    effectiveInstallmentAmount);
            extendedLoanAccountSummaryData.addCustomParameter(AccountDataAdditionalProperties.TOTAL_LATE_FEES, totalLateFees);
            extendedLoanAccountSummaryData.addCustomParameter(AccountDataAdditionalProperties.NET_DISBURSED_AMOUNT, netDisbursedAmount);
            extendedLoanAccountSummaryData.addCustomParameter(AccountDataAdditionalProperties.REMITTER_NAME, remitterName);
            extendedLoanAccountSummaryData.addCustomParameter(AccountDataAdditionalProperties.DP_NAME, dpName);
            extendedLoanAccountSummaryData.addCustomParameter(AccountDataAdditionalProperties.IS_FORCED_CLOSURE, isForcedClosure);
            extendedLoanAccountSummaryData.addCustomParameter(AccountDataAdditionalProperties.IS_RESTRUCTURED, isRestructured);

            return extendedLoanAccountSummaryData;
        }

    }

    private static final class CredXSavingsAccountSummaryDataMapper implements RowMapper<SavingsAccountSummaryData> {

        final String schemaSql;

        CredXSavingsAccountSummaryDataMapper() {
            final StringBuilder accountsSummary = new StringBuilder();
            accountsSummary.append("sa.id as id, sa.account_no as accountNo, sa.external_id as externalId, sa.status_enum as statusEnum, ");
            accountsSummary.append("sa.account_type_enum as accountType, ");
            accountsSummary.append("sa.account_balance_derived as accountBalance, ");

            accountsSummary.append("sa.submittedon_date as submittedOnDate,");
            accountsSummary.append("sbu.username as submittedByUsername,");
            accountsSummary.append("sbu.firstname as submittedByFirstname, sbu.lastname as submittedByLastname,");

            accountsSummary.append("sa.rejectedon_date as rejectedOnDate,");
            accountsSummary.append("rbu.username as rejectedByUsername,");
            accountsSummary.append("rbu.firstname as rejectedByFirstname, rbu.lastname as rejectedByLastname,");

            accountsSummary.append("sa.withdrawnon_date as withdrawnOnDate,");
            accountsSummary.append("wbu.username as withdrawnByUsername,");
            accountsSummary.append("wbu.firstname as withdrawnByFirstname, wbu.lastname as withdrawnByLastname,");

            accountsSummary.append("sa.approvedon_date as approvedOnDate,");
            accountsSummary.append("abu.username as approvedByUsername,");
            accountsSummary.append("abu.firstname as approvedByFirstname, abu.lastname as approvedByLastname,");

            accountsSummary.append("sa.activatedon_date as activatedOnDate,");
            accountsSummary.append("avbu.username as activatedByUsername,");
            accountsSummary.append("avbu.firstname as activatedByFirstname, avbu.lastname as activatedByLastname,");

            accountsSummary.append("sa.sub_status_enum as subStatusEnum, ");
            accountsSummary.append("(select coalesce(max(sat.transaction_date),sa.activatedon_date) ");
            accountsSummary.append("from m_savings_account_transaction as sat ");
            accountsSummary.append("where sat.is_reversed = false ");
            accountsSummary.append("and sat.transaction_type_enum in (1,2) ");
            accountsSummary.append("and sat.savings_account_id = sa.id) as lastActiveTransactionDate, ");

            accountsSummary.append("sa.closedon_date as closedOnDate,");
            accountsSummary.append("cbu.username as closedByUsername,");
            accountsSummary.append("cbu.firstname as closedByFirstname, cbu.lastname as closedByLastname,");

            accountsSummary.append(
                    "sa.currency_code as currencyCode, sa.currency_digits as currencyDigits, sa.currency_multiplesof as inMultiplesOf, ");
            accountsSummary.append("curr.name as currencyName, curr.internationalized_name_code as currencyNameCode, ");
            accountsSummary.append("curr.display_symbol as currencyDisplaySymbol, ");
            accountsSummary.append("sa.product_id as productId, p.name as productName, p.short_name as shortProductName, ");
            accountsSummary.append("sa.deposit_type_enum as depositType, loc.external_id as locExternalId, ");
            accountsSummary.append("ml.id as fromLoanAccountId, ");
            accountsSummary.append("ml.account_no as fromLoanAccountNumber ");
            accountsSummary.append("from m_savings_account sa ");
            accountsSummary.append("join m_savings_product as p on p.id = sa.product_id ");
            accountsSummary.append("join m_currency curr on curr.code = sa.currency_code ");
            accountsSummary.append("left join m_appuser sbu on sbu.id = sa.submittedon_userid ");
            accountsSummary.append("left join m_appuser rbu on rbu.id = sa.rejectedon_userid ");
            accountsSummary.append("left join m_appuser wbu on wbu.id = sa.withdrawnon_userid ");
            accountsSummary.append("left join m_appuser abu on abu.id = sa.approvedon_userid ");
            accountsSummary.append("left join m_appuser avbu on rbu.id = sa.activatedon_userid ");
            accountsSummary.append("left join m_appuser cbu on cbu.id = sa.closedon_userid ");
            accountsSummary
                    .append("""
                                    left join (
                                        select
                                            act.to_savings_account_id,
                                                    act.from_loan_account_id
                                        from
                                            m_account_transfer_details act inner join
                                            m_account_transfer_transaction att on att.account_transfer_details_id  = act.id and att.is_reversed = false
                                            where act.transfer_type = 1 and act.from_loan_account_id is not null) as transfer_info
                                            on sa.id = transfer_info.to_savings_account_id
                            """);
            accountsSummary.append(" left join m_loan ml on ml.id = transfer_info.from_loan_account_id ");
            accountsSummary.append(" left join m_line_of_credit loc on sa.id = loc.settlement_savings_account_id ");

            this.schemaSql = accountsSummary.toString();
        }

        public String schema() {
            return this.schemaSql;
        }

        @Override
        public SavingsAccountSummaryData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final Long id = JdbcSupport.getLong(rs, "id");
            final String accountNo = rs.getString("accountNo");
            final String externalId = rs.getString("externalId");
            final Long productId = JdbcSupport.getLong(rs, "productId");
            final String productName = rs.getString("productName");
            final String shortProductName = rs.getString("shortProductName");
            final Integer statusId = JdbcSupport.getInteger(rs, "statusEnum");
            final BigDecimal accountBalance = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "accountBalance");
            final SavingsAccountStatusEnumData status = SavingsEnumerations.status(statusId);
            final Integer accountType = JdbcSupport.getInteger(rs, "accountType");
            final EnumOptionData accountTypeData = AccountEnumerations.loanType(accountType);
            final Integer depositTypeId = JdbcSupport.getInteger(rs, "depositType");
            final EnumOptionData depositTypeData = SavingsEnumerations.depositType(depositTypeId);

            final String currencyCode = rs.getString("currencyCode");
            final String currencyName = rs.getString("currencyName");
            final String currencyNameCode = rs.getString("currencyNameCode");
            final String currencyDisplaySymbol = rs.getString("currencyDisplaySymbol");
            final Integer currencyDigits = JdbcSupport.getInteger(rs, "currencyDigits");
            final Integer inMultiplesOf = JdbcSupport.getInteger(rs, "inMultiplesOf");
            final CurrencyData currency = new CurrencyData(currencyCode, currencyName, currencyDigits, inMultiplesOf, currencyDisplaySymbol,
                    currencyNameCode);

            final LocalDate submittedOnDate = JdbcSupport.getLocalDate(rs, "submittedOnDate");
            final String submittedByUsername = rs.getString("submittedByUsername");
            final String submittedByFirstname = rs.getString("submittedByFirstname");
            final String submittedByLastname = rs.getString("submittedByLastname");

            final LocalDate rejectedOnDate = JdbcSupport.getLocalDate(rs, "rejectedOnDate");
            final String rejectedByUsername = rs.getString("rejectedByUsername");
            final String rejectedByFirstname = rs.getString("rejectedByFirstname");
            final String rejectedByLastname = rs.getString("rejectedByLastname");

            final LocalDate withdrawnOnDate = JdbcSupport.getLocalDate(rs, "withdrawnOnDate");
            final String withdrawnByUsername = rs.getString("withdrawnByUsername");
            final String withdrawnByFirstname = rs.getString("withdrawnByFirstname");
            final String withdrawnByLastname = rs.getString("withdrawnByLastname");

            final LocalDate approvedOnDate = JdbcSupport.getLocalDate(rs, "approvedOnDate");
            final String approvedByUsername = rs.getString("approvedByUsername");
            final String approvedByFirstname = rs.getString("approvedByFirstname");
            final String approvedByLastname = rs.getString("approvedByLastname");

            final LocalDate activatedOnDate = JdbcSupport.getLocalDate(rs, "activatedOnDate");
            final String activatedByUsername = rs.getString("activatedByUsername");
            final String activatedByFirstname = rs.getString("activatedByFirstname");
            final String activatedByLastname = rs.getString("activatedByLastname");

            final LocalDate closedOnDate = JdbcSupport.getLocalDate(rs, "closedOnDate");
            final String closedByUsername = rs.getString("closedByUsername");
            final String closedByFirstname = rs.getString("closedByFirstname");
            final String closedByLastname = rs.getString("closedByLastname");
            final Integer subStatusEnum = JdbcSupport.getInteger(rs, "subStatusEnum");
            final SavingsAccountSubStatusEnumData subStatus = SavingsEnumerations.subStatus(subStatusEnum);

            final LocalDate lastActiveTransactionDate = JdbcSupport.getLocalDate(rs, "lastActiveTransactionDate");

            final Long fromLoanAccountId = JdbcSupport.getLong(rs, "fromLoanAccountId");
            String fromLoanAccountNumber = rs.getString("fromLoanAccountNumber");

            if (Strings.isBlank(fromLoanAccountNumber)) {
                fromLoanAccountNumber = rs.getString("locExternalId");
            }

            final SavingsAccountApplicationTimelineData timeline = new SavingsAccountApplicationTimelineData(submittedOnDate,
                    submittedByUsername, submittedByFirstname, submittedByLastname, rejectedOnDate, rejectedByUsername, rejectedByFirstname,
                    rejectedByLastname, withdrawnOnDate, withdrawnByUsername, withdrawnByFirstname, withdrawnByLastname, approvedOnDate,
                    approvedByUsername, approvedByFirstname, approvedByLastname, activatedOnDate, activatedByUsername, activatedByFirstname,
                    activatedByLastname, closedOnDate, closedByUsername, closedByFirstname, closedByLastname);

            ExtendedSavingsAccountSummaryData smd = new ExtendedSavingsAccountSummaryData(id, accountNo, externalId, productId, productName,
                    shortProductName, status, currency, accountBalance, accountTypeData, timeline, depositTypeData, subStatus,
                    lastActiveTransactionDate);

            smd.addCustomParameter(AccountDataAdditionalProperties.LINKED_LOAN_ACCOUNT_ID, fromLoanAccountId);
            smd.addCustomParameter(AccountDataAdditionalProperties.LINKED_LOAN_ACCOUNT_NUMBER, fromLoanAccountNumber);

            return smd;
        }

    }

    /**
     * Public method to expose JdbcTemplate for direct queries
     */
    public org.springframework.jdbc.core.JdbcTemplate getJdbcTemplate() {
        return this.jdbcTemplate;
    }

}
