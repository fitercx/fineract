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

package com.crediblex.fineract.portfolio.loanaccount.service;

import static org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations.interestType;

import com.crediblex.fineract.portfolio.loanaccount.data.ExtendedLoanAccountData;
import com.crediblex.fineract.portfolio.loanaccount.data.ExtendedLoanSchedulePeriodData;
import com.crediblex.fineract.portfolio.loanaccount.data.LoanAccountAdditionalProperties;
import com.crediblex.fineract.portfolio.loanaccount.data.LoanInterestVariationsData;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.crediblex.fineract.portfolio.loanaccount.queries.LoanQueries.RapaymentStatusQuery;
import com.crediblex.fineract.portfolio.loanaccount.repository.CredXLoanTransactionRepository;
import com.crediblex.fineract.portfolio.loanproduct.data.ExtendedLoanProductData;
import com.crediblex.fineract.portfolio.loc.charge.data.LineOfCreditApprovedBuyerSupplierData;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditSummary;
import com.crediblex.fineract.portfolio.loc.data.LocProductType;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditReadPlatformService;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.ApiFacingEnum;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.data.StringEnumOptionData;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.portfolio.accountdetails.data.LoanAccountSummaryData;
import org.apache.fineract.portfolio.accountdetails.domain.AccountType;
import org.apache.fineract.portfolio.accountdetails.service.AccountDetailsReadPlatformService;
import org.apache.fineract.portfolio.accountdetails.service.AccountEnumerations;
import org.apache.fineract.portfolio.calendar.data.CalendarData;
import org.apache.fineract.portfolio.calendar.service.CalendarReadPlatformService;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService;
import org.apache.fineract.portfolio.client.domain.ClientEnumerations;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.common.domain.DaysInYearCustomStrategyType;
import org.apache.fineract.portfolio.common.service.CommonEnumerations;
import org.apache.fineract.portfolio.delinquency.data.DelinquencyRangeData;
import org.apache.fineract.portfolio.delinquency.service.DelinquencyReadPlatformService;
import org.apache.fineract.portfolio.floatingrates.service.FloatingRatesReadPlatformService;
import org.apache.fineract.portfolio.fund.data.FundData;
import org.apache.fineract.portfolio.fund.service.FundReadPlatformService;
import org.apache.fineract.portfolio.group.data.GroupGeneralData;
import org.apache.fineract.portfolio.group.service.GroupReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementData;
import org.apache.fineract.portfolio.loanaccount.data.LoanAccountData;
import org.apache.fineract.portfolio.loanaccount.data.LoanApplicationTimelineData;
import org.apache.fineract.portfolio.loanaccount.data.LoanApprovalData;
import org.apache.fineract.portfolio.loanaccount.data.LoanInterestRecalculationData;
import org.apache.fineract.portfolio.loanaccount.data.LoanStatusEnumData;
import org.apache.fineract.portfolio.loanaccount.data.LoanSummaryData;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.apache.fineract.portfolio.loanaccount.data.RepaymentScheduleRelatedLoanData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCapitalizedIncomeCalculationType;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCapitalizedIncomeStrategy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeOffBehaviour;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSubStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleProcessingType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTransactionMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanForeclosureValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargePaidByReadService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceImpl;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanTransactionRelationReadService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.loanproduct.data.TransactionProcessingStrategyData;
import org.apache.fineract.portfolio.loanproduct.domain.InterestMethod;
import org.apache.fineract.portfolio.loanproduct.service.LoanDropdownReadPlatformService;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.apache.fineract.portfolio.loanproduct.service.LoanProductReadPlatformService;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CredXLoanReadPlatformServiceImpl extends LoanReadPlatformServiceImpl {

    private final CredXLoanTransactionRepository credXLoanTransactionRepository;

    private final PaymentTypeReadPlatformService paymentTypeReadPlatformService;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final LineOfCreditReadPlatformService lineOfCreditReadPlatformService;
    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;

    public CredXLoanReadPlatformServiceImpl(JdbcTemplate jdbcTemplate, PlatformSecurityContext context,
            LoanRepositoryWrapper loanRepositoryWrapper, ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository,
            LoanProductReadPlatformService loanProductReadPlatformService, ClientReadPlatformService clientReadPlatformService,
            GroupReadPlatformService groupReadPlatformService, LoanDropdownReadPlatformService loanDropdownReadPlatformService,
            FundReadPlatformService fundReadPlatformService, ChargeReadPlatformService chargeReadPlatformService,
            CodeValueReadPlatformService codeValueReadPlatformService, CalendarReadPlatformService calendarReadPlatformService,
            StaffReadPlatformService staffReadPlatformService, PaginationHelper paginationHelper,
            PaymentTypeReadPlatformService paymentTypeReadPlatformService,
            LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
            FloatingRatesReadPlatformService floatingRatesReadPlatformService, LoanUtilService loanUtilService,
            ConfigurationDomainService configurationDomainService, AccountDetailsReadPlatformService accountDetailsReadPlatformService,
            ColumnValidator columnValidator, DatabaseSpecificSQLGenerator sqlGenerator,
            DelinquencyReadPlatformService delinquencyReadPlatformService, LoanTransactionRepository loanTransactionRepository,
            LoanChargePaidByReadService loanChargePaidByReadService, LoanTransactionRelationReadService loanTransactionRelationReadService,
            LoanForeclosureValidator loanForeclosureValidator, LoanTransactionMapper loanTransactionMapper,
            org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper loanMapper,
            LoanTransactionProcessingService loadTransactionProcessingService,
            CredXLoanTransactionRepository credXLoanTransactionRepository, LineOfCreditReadPlatformService lineOfCreditReadPlatformService,
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        super(jdbcTemplate, context, loanRepositoryWrapper, applicationCurrencyRepository, loanProductReadPlatformService,
                clientReadPlatformService, groupReadPlatformService, loanDropdownReadPlatformService, fundReadPlatformService,
                chargeReadPlatformService, codeValueReadPlatformService, calendarReadPlatformService, staffReadPlatformService,
                paginationHelper, paymentTypeReadPlatformService, loanRepaymentScheduleTransactionProcessorFactory,
                floatingRatesReadPlatformService, loanUtilService, configurationDomainService, accountDetailsReadPlatformService,
                columnValidator, sqlGenerator, delinquencyReadPlatformService, loanTransactionRepository, loanChargePaidByReadService,
                loanTransactionRelationReadService, loanForeclosureValidator, loanTransactionMapper, loanMapper,
                loadTransactionProcessingService);
        this.credXLoanTransactionRepository = credXLoanTransactionRepository;
        this.paymentTypeReadPlatformService = paymentTypeReadPlatformService;
        this.jdbcTemplate = jdbcTemplate;
        this.sqlGenerator = sqlGenerator;
        this.lineOfCreditReadPlatformService = lineOfCreditReadPlatformService;
        this.loanRepositoryWrapper = loanRepositoryWrapper;
        this.applicationCurrencyRepository = applicationCurrencyRepository;
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
    }

    @Override
    public LoanTransactionData retrieveLoanTransactionTemplate(Long loanId) {
        RapaymentStatusQuery.Result result = credXLoanTransactionRepository.retrieveLoanRepaymentTemplate(loanId);

        CurrencyData currencyData = new CurrencyData(result.getCurrencyCode(), result.getCurrencyName(), result.getCurrencyDigits(),
                result.getInMultiplesOf(), result.getCurrencyDisplaySymbol(), result.getCurrencyNameCode());

        final LoanTransactionEnumData transactionType = LoanEnumerations.transactionType(LoanTransactionType.REPAYMENT);
        final LocalDate date = ((java.sql.Date) result.getTransactionDate()).toLocalDate();
        final BigDecimal principalPortion = result.getPrincipalDue();
        final BigDecimal interestDue = result.getInterestDue();
        final BigDecimal feeDue = result.getFeeDue();
        final BigDecimal penaltyDue = result.getPenaltyDue();
        final BigDecimal totalDue = principalPortion.add(interestDue).add(feeDue).add(penaltyDue);
        final BigDecimal netDisbursalAmount = result.getNetDisbursalAmount();
        boolean manuallyReversed = false;
        final Collection<PaymentTypeData> paymentTypeOptions = paymentTypeReadPlatformService.retrieveAllPaymentTypes();

        return new LoanTransactionData(null, null, null, transactionType, null, currencyData, date, totalDue, netDisbursalAmount,
                principalPortion, interestDue, feeDue, penaltyDue, null, null, paymentTypeOptions, ExternalId.empty(), null, null, null,
                manuallyReversed, loanId, ExternalId.empty());
    }

    @Override
    public LoanScheduleData retrieveRepaymentSchedule(Long loanId, RepaymentScheduleRelatedLoanData repaymentScheduleRelatedLoanData,
            Collection<DisbursementData> disbursementData, boolean isInterestRecalculationEnabled, LoanScheduleType loanScheduleType) {
        LoanScheduleData loanScheduleData = super.retrieveRepaymentSchedule(loanId, repaymentScheduleRelatedLoanData, disbursementData,
                isInterestRecalculationEnabled, loanScheduleType);

        CurrencyData currency = loanScheduleData.getCurrency();
        Collection<LoanSchedulePeriodData> periods = loanScheduleData.getPeriods();

        Collection<ExtendedLoanSchedulePeriodData> periodDataWithStatus = periods.stream()
                .map(p -> new ExtendedLoanSchedulePeriodData(p, resolvePeriodStatus(currency, p))).toList();

        Collection<LoanSchedulePeriodData> periodDataCollection = new ArrayList<>(periodDataWithStatus);
        return loanScheduleData.withPeriods(periodDataCollection);
    }

    ExtendedLoanSchedulePeriodData.Status resolvePeriodStatus(CurrencyData currencyData, LoanSchedulePeriodData period) {
        if (Objects.isNull(period.getPeriod())) {
            // This is a disbursement period has null period value
            return ExtendedLoanSchedulePeriodData.Status.DISBURSEMENT;
        }

        if (Boolean.TRUE.equals(period.getComplete())) {
            return ExtendedLoanSchedulePeriodData.Status.PAID;
        }

        boolean isOverdue = period.getDueDate().isBefore(DateUtils.getLocalDateOfTenant());

        Money penaltyAmount = Money.of(currencyData, period.getPenaltyChargesDue());
        Money totalPaidAmount = Money.of(currencyData, period.getTotalPaidForPeriod());

        if (isOverdue && totalPaidAmount.isLessThan(penaltyAmount)) {
            return ExtendedLoanSchedulePeriodData.Status.LATE_FEE_APPLIED;
        }

        if (isOverdue && totalPaidAmount.isGreaterThanOrEqualTo(penaltyAmount)) {
            return ExtendedLoanSchedulePeriodData.Status.OVERDUE;
        }

        if (Money.of(currencyData, period.getTotalOutstandingForPeriod()).isGreaterThanZero()
                && Money.of(currencyData, period.getTotalPaidForPeriod()).isGreaterThanZero()) {
            return ExtendedLoanSchedulePeriodData.Status.PARTIAL_PAID;
        }

        if (period.getDueDate().equals(DateUtils.getLocalDateOfTenant())) {
            return ExtendedLoanSchedulePeriodData.Status.DUE;
        }

        return ExtendedLoanSchedulePeriodData.Status.SCHEDULED;
    }

    @Override
    public LoanAccountData retrieveOne(final Long loanId) {

        try {
            final String hierarchy = getHierarchyString();
            final String hierarchySearchString = hierarchy + "%";

            final CredibleXLoanMapper loanRowMapper = new CredibleXLoanMapper(sqlGenerator, delinquencyReadPlatformService);
            final LoanSchedulePeriodMapper loanSchedulePeriodRowMapper = new LoanSchedulePeriodMapper();
            final LoanTermVariationsMapper loanTermsVariationsRowMapper = new LoanTermVariationsMapper();

            final String loanTermsVariationSql = loanTermsVariationsRowMapper.loanTermVariationSchema();
            final String loanSchedulePeriodSql = loanSchedulePeriodRowMapper.loanSchedulePeriodSchema();
            final StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("select ");
            sqlBuilder.append(loanRowMapper.loanSchema());
            sqlBuilder.append(" join m_office o on (o.id = c.office_id or o.id = g.office_id) ");
            sqlBuilder.append(" left join m_office transferToOffice on transferToOffice.id = c.transfer_to_office_id ");
            sqlBuilder.append(" where l.id=? and ( o.hierarchy like ? or transferToOffice.hierarchy like ?)");

            final List<LoanTermVariationsData> loanTermVariationsData = this.jdbcTemplate.query(loanTermsVariationSql,
                    loanTermsVariationsRowMapper, loanId);
            final List<LoanSchedulePeriodData> loanSchedulePeriodData = this.jdbcTemplate.query(loanSchedulePeriodSql,
                    loanSchedulePeriodRowMapper, loanId);
            final LoanAccountData loanAccountData = this.jdbcTemplate.queryForObject(sqlBuilder.toString(), loanRowMapper, loanId,
                    hierarchySearchString, hierarchySearchString);

            List<LoanInterestVariationsData> loanInterestVariationsData = LoanInterestVariationsData
                    .buildInterestPeriods(loanTermVariationsData, loanSchedulePeriodData, loanAccountData.getAnnualInterestRate());

            if (loanAccountData instanceof ExtendedLoanAccountData extended) {
                extended.addCustomParameter(LoanAccountAdditionalProperties.LOAN_INTEREST_VARIATIONS, loanInterestVariationsData);

                if (extended.getAdditionalProperties().containsKey("locProductType")
                        && extended.getAdditionalProperties().get("locProductType").equals("PAYABLE")) {
                    extended.addCustomParameter("supplierDetails", retrieveCounterpartyDetails(loanId));
                } else {
                    extended.addCustomParameter("buyerDetails", retrieveCounterpartyDetails(loanId));
                }
            }
            return loanAccountData;
        } catch (final EmptyResultDataAccessException e) {
            throw new LoanNotFoundException(loanId, e);
        }
    }

    /**
     * Retrieve counterparty details (buyers and suppliers) for a loan
     */
    private List<LineOfCreditApprovedBuyerSupplierData> retrieveCounterpartyDetails(Long loanId) {
        final String sql = """
                    SELECT lcd.id as id,
                           lab.name as name
                    FROM m_loan_approver_buyers_suppliers lcd
                    JOIN m_loan l ON l.id = lcd.loan_id
                    JOIN m_line_of_credit_approved_buyers lab ON lab.id = lcd.buyer_supplier_id
                    WHERE lcd.loan_id = ?
                """;

        return this.jdbcTemplate.query(sql,
                (rs, rowNum) -> LineOfCreditApprovedBuyerSupplierData.builder().id(rs.getLong("id")).name(rs.getString("name")).build(),
                loanId);
    }

    private static final class LoanTermVariationsMapper implements RowMapper<LoanTermVariationsData> {

        LoanTermVariationsMapper() {}

        @Override
        public LoanTermVariationsData mapRow(ResultSet rs, int rowNum) throws SQLException {
            Date applicableSqlDate = rs.getDate("applicable_date");
            LocalDate applicableDate = (applicableSqlDate != null) ? ((java.sql.Date) applicableSqlDate).toLocalDate() : null;

            Date valueSqlDate = rs.getDate("date_value");
            LocalDate dateValue = (valueSqlDate != null) ? ((java.sql.Date) valueSqlDate).toLocalDate() : null;

            // Safe extraction for numeric
            BigDecimal decimalValue = rs.getBigDecimal("decimal_value"); // getBigDecimal already returns null if column
                                                                         // is null

            // Safe extraction for boolean: use getObject to handle SQL NULL properly
            Boolean isSpecificToInstallment = rs.getObject("is_specific_to_installment") != null
                    ? rs.getBoolean("is_specific_to_installment")
                    : null;
            return new LoanTermVariationsData(rs.getLong("id"), rs.getInt("term_type"), applicableDate, decimalValue, dateValue,
                    isSpecificToInstallment != null && isSpecificToInstallment // convert to primitive if your
                                                                               // constructor needs boolean
            );
        }

        public String loanTermVariationSchema() {
            return """
                    WITH ranked_variations AS (
                        SELECT
                            id,
                            term_type,
                            applicable_date,
                            decimal_value,
                            date_value,
                            is_specific_to_installment,
                            ROW_NUMBER() OVER (
                                PARTITION BY applicable_date
                                ORDER BY created_on_utc DESC
                            ) AS rn
                        FROM m_loan_term_variations
                        WHERE loan_id = ?
                              AND term_type = 10
                              AND is_active = true
                    )
                    SELECT id, term_type, applicable_date, decimal_value, date_value, is_specific_to_installment
                    FROM ranked_variations
                    WHERE rn = 1
                    ORDER BY applicable_date
                    """;
        }
    }

    private static final class LoanSchedulePeriodMapper implements RowMapper<LoanSchedulePeriodData> {

        LoanSchedulePeriodMapper() {}

        @Override
        public LoanSchedulePeriodData mapRow(ResultSet rs, int rowNum) throws SQLException {
            return LoanSchedulePeriodData.repaymentOnlyPeriod(rs.getInt("installment"), toLocalDateSafe(rs.getDate("fromdate")),
                    toLocalDateSafe(rs.getDate("duedate")), BigDecimal.ZERO, // principalDue
                    BigDecimal.ZERO, // outstandingLoanBalance
                    BigDecimal.ZERO, // interestDue
                    BigDecimal.ZERO, // feeDue
                    BigDecimal.ZERO // penaltyDue
            );
        }

        private LocalDate toLocalDateSafe(Date date) {
            return date != null ? date.toLocalDate() : null;
        }

        public String loanSchedulePeriodSchema() {
            return """
                    select
                        installment,
                        fromdate,
                        duedate
                    from m_loan_repayment_schedule mlrs
                    where loan_id = ?
                    order by installment asc
                    """;
        }

    }

    private static final class CredibleXLoanMapper implements RowMapper<LoanAccountData> {

        private final DatabaseSpecificSQLGenerator sqlGenerator;
        private final DelinquencyReadPlatformService delinquencyReadPlatformService;

        CredibleXLoanMapper(DatabaseSpecificSQLGenerator sqlGenerator, DelinquencyReadPlatformService delinquencyReadPlatformService) {
            this.sqlGenerator = sqlGenerator;
            this.delinquencyReadPlatformService = delinquencyReadPlatformService;
        }

        public String loanSchema() {
            return "l.id as id, l.account_no as accountNo, l.external_id as externalId, l.fund_id as fundId, f.name as fundName,"
                    + " l.loan_type_enum as loanType, l.loanpurpose_cv_id as loanPurposeId, cv.code_value as loanPurposeName,"
                    + " l.is_forced_closure as isForcedClosure, l.is_restructured as isRestructured,"
                    + " lp.id as loanProductId, lp.name as loanProductName, lp.description as loanProductDescription,"
                    + " lp.is_linked_to_floating_interest_rates as isLoanProductLinkedToFloatingRate, "
                    + " lp.allow_variabe_installments as isvariableInstallmentsAllowed, "
                    + " lp.allow_multiple_disbursals as multiDisburseLoan, lp.disallow_expected_disbursements as disallowExpectedDisbursements, "
                    + " lp.can_define_fixed_emi_amount as canDefineInstallmentAmount,"
                    + " c.id as clientId, c.account_no as clientAccountNo, c.display_name as clientName, c.office_id as clientOfficeId, c.external_id as clientExternalId,"
                    + " g.id as groupId, g.account_no as groupAccountNo, g.display_name as groupName,"
                    + " g.office_id as groupOfficeId, g.staff_id As groupStaffId , g.parent_id as groupParentId, (select mg.display_name from m_group mg where mg.id = g.parent_id) as centerName, "
                    + " g.hierarchy As groupHierarchy , g.level_id as groupLevel, g.external_id As groupExternalId, "
                    + " g.status_enum as statusEnum, g.activation_date as activationDate, "
                    + " l.submittedon_date as submittedOnDate, sbu.username as submittedByUsername, sbu.firstname as submittedByFirstname, sbu.lastname as submittedByLastname,"
                    + " l.rejectedon_date as rejectedOnDate, rbu.username as rejectedByUsername, rbu.firstname as rejectedByFirstname, rbu.lastname as rejectedByLastname,"
                    + " l.withdrawnon_date as withdrawnOnDate, wbu.username as withdrawnByUsername, wbu.firstname as withdrawnByFirstname, wbu.lastname as withdrawnByLastname,"
                    + " l.approvedon_date as approvedOnDate, abu.username as approvedByUsername, abu.firstname as approvedByFirstname, abu.lastname as approvedByLastname,"
                    + " l.expected_disbursedon_date as expectedDisbursementDate, l.disbursedon_date as actualDisbursementDate, dbu.username as disbursedByUsername, dbu.firstname as disbursedByFirstname, dbu.lastname as disbursedByLastname,"
                    + " l.closedon_date as closedOnDate, cbu.username as closedByUsername, cbu.firstname as closedByFirstname, cbu.lastname as closedByLastname, l.writtenoffon_date as writtenOffOnDate, "
                    + " l.expected_firstrepaymenton_date as expectedFirstRepaymentOnDate, l.interest_calculated_from_date as interestChargedFromDate, l.maturedon_date as actualMaturityDate, l.expected_maturedon_date as expectedMaturityDate, "
                    + " l.principal_amount_proposed as proposedPrincipal, l.principal_amount as principal, l.approved_principal as approvedPrincipal, l.net_disbursal_amount as netDisbursalAmount, l.arrearstolerance_amount as inArrearsTolerance, l.number_of_repayments as numberOfRepayments, l.repay_every as repaymentEvery,"
                    + " l.grace_on_principal_periods as graceOnPrincipalPayment, l.recurring_moratorium_principal_periods as recurringMoratoriumOnPrincipalPeriods, l.grace_on_interest_periods as graceOnInterestPayment, l.grace_interest_free_periods as graceOnInterestCharged,l.grace_on_arrears_ageing as graceOnArrearsAgeing,"
                    + " l.nominal_interest_rate_per_period as interestRatePerPeriod, l.annual_nominal_interest_rate as annualInterestRate, "
                    + " l.repayment_period_frequency_enum as repaymentFrequencyType, l.interest_period_frequency_enum as interestRateFrequencyType, "
                    + " l.fixed_length as fixedLength, "
                    + " l.term_frequency as termFrequency, l.term_period_frequency_enum as termPeriodFrequencyType, "
                    + " l.amortization_method_enum as amortizationType, l.interest_method_enum as interestType, l.is_equal_amortization as isEqualAmortization, l.interest_calculated_in_period_enum as interestCalculationPeriodType,"
                    + " l.fixed_principal_percentage_per_installment fixedPrincipalPercentagePerInstallment, "
                    + " l.allow_partial_period_interest_calcualtion as allowPartialPeriodInterestCalcualtion,"
                    + " l.loan_status_id as lifeCycleStatusId, l.loan_transaction_strategy_code as transactionStrategyCode, "
                    + " l.loan_transaction_strategy_name as transactionStrategyName, l.enable_installment_level_delinquency as enableInstallmentLevelDelinquency, "
                    + " l.currency_code as currencyCode, l.currency_digits as currencyDigits, l.currency_multiplesof as inMultiplesOf, rc."
                    + sqlGenerator.escape("name")
                    + " as currencyName, rc.display_symbol as currencyDisplaySymbol, rc.internationalized_name_code as currencyNameCode, "
                    + " l.loan_officer_id as loanOfficerId, s.display_name as loanOfficerName, "
                    + " l.principal_disbursed_derived as principalDisbursed, l.principal_repaid_derived as principalPaid,"
                    + " l.principal_adjustments_derived as principalAdjustments, l.principal_writtenoff_derived as principalWrittenOff,"
                    + " l.fee_adjustments_derived as feeAdjustments, l.penalty_adjustments_derived as penaltyAdjustments,"
                    + " l.principal_outstanding_derived as principalOutstanding, l.interest_charged_derived as interestCharged,"
                    + " l.interest_repaid_derived as interestPaid, l.interest_waived_derived as interestWaived,"
                    + " l.interest_writtenoff_derived as interestWrittenOff, l.interest_outstanding_derived as interestOutstanding,"
                    + " l.fee_charges_charged_derived as feeChargesCharged,"
                    + " l.total_charges_due_at_disbursement_derived as feeChargesDueAtDisbursementCharged,"
                    + " l.fee_charges_repaid_derived as feeChargesPaid, l.fee_charges_waived_derived as feeChargesWaived,"
                    + " l.fee_charges_writtenoff_derived as feeChargesWrittenOff,"
                    + " l.fee_charges_outstanding_derived as feeChargesOutstanding,"
                    + " l.penalty_charges_charged_derived as penaltyChargesCharged,"
                    + " l.penalty_charges_repaid_derived as penaltyChargesPaid,"
                    + " l.penalty_charges_waived_derived as penaltyChargesWaived,"
                    + " l.penalty_charges_writtenoff_derived as penaltyChargesWrittenOff,"
                    + " l.penalty_charges_outstanding_derived as penaltyChargesOutstanding, "
                    + " l.total_expected_repayment_derived as totalExpectedRepayment, l.total_repayment_derived as totalRepayment,"
                    + " l.total_expected_costofloan_derived as totalExpectedCostOfLoan, l.total_costofloan_derived as totalCostOfLoan,"
                    + " l.total_waived_derived as totalWaived, l.total_writtenoff_derived as totalWrittenOff,"
                    + " l.writeoff_reason_cv_id as writeoffReasonId, codev.code_value as writeoffReason,"
                    + " l.total_outstanding_derived as totalOutstanding, l.total_overpaid_derived as totalOverpaid,"
                    + " l.fixed_emi_amount as fixedEmiAmount, l.max_outstanding_loan_balance as outstandingLoanBalance,"
                    + " l.loan_sub_status_id as loanSubStatusId, la.principal_overdue_derived as principalOverdue, l.is_fraud as isFraud, "
                    + " la.interest_overdue_derived as interestOverdue, la.fee_charges_overdue_derived as feeChargesOverdue,"
                    + " la.penalty_charges_overdue_derived as penaltyChargesOverdue, la.total_overdue_derived as totalOverdue,"
                    + " la.overdue_since_date_derived as overdueSinceDate, "
                    + " l.sync_disbursement_with_meeting as syncDisbursementWithMeeting,"
                    + " l.loan_counter as loanCounter, l.loan_product_counter as loanProductCounter,"
                    + " l.is_npa as isNPA, l.days_in_month_enum as daysInMonth, l.days_in_year_enum as daysInYear, "
                    + " l.interest_recalculation_enabled as isInterestRecalculationEnabled, "
                    + " lir.id as lirId, lir.loan_id as loanId, lir.compound_type_enum as compoundType, lir.reschedule_strategy_enum as rescheduleStrategy, "
                    + " lir.rest_frequency_type_enum as restFrequencyEnum, lir.rest_frequency_interval as restFrequencyInterval, "
                    + " lir.rest_frequency_nth_day_enum as restFrequencyNthDayEnum, "
                    + " lir.rest_frequency_weekday_enum as restFrequencyWeekDayEnum, "
                    + " lir.rest_frequency_on_day as restFrequencyOnDay, "
                    + " lir.compounding_frequency_type_enum as compoundingFrequencyEnum, lir.compounding_frequency_interval as compoundingInterval, "
                    + " lir.compounding_frequency_nth_day_enum as compoundingFrequencyNthDayEnum, "
                    + " lir.compounding_frequency_weekday_enum as compoundingFrequencyWeekDayEnum, "
                    + " lir.compounding_frequency_on_day as compoundingFrequencyOnDay, "
                    + " lir.is_compounding_to_be_posted_as_transaction as isCompoundingToBePostedAsTransaction, "
                    + " lir.allow_compounding_on_eod as allowCompoundingOnEod, "
                    + " lir.disallow_interest_calc_on_past_due as disallowInterestCalculationOnPastDue, "
                    + " l.is_floating_interest_rate as isFloatingInterestRate, "
                    + " l.interest_rate_differential as interestRateDifferential, "
                    + " l.days_in_year_custom_strategy as daysInYearCustomStrategy, "
                    + " l.enable_income_capitalization as enableIncomeCapitalization, "
                    + " l.capitalized_income_calculation_type as capitalizedIncomeCalculationType, "
                    + " l.capitalized_income_strategy as capitalizedIncomeStrategy, "
                    + " l.create_standing_instruction_at_disbursement as createStandingInstructionAtDisbursement, "
                    + " lpvi.minimum_gap as minimuminstallmentgap, lpvi.maximum_gap as maximuminstallmentgap, "
                    + " lp.can_use_for_topup as canUseForTopup, l.is_topup as isTopup, topup.closure_loan_id as closureLoanId, "
                    + " l.total_recovered_derived as totalRecovered, topuploan.account_no as closureLoanAccountNo, "
                    + " topup.topup_amount as topupAmount, l.last_closed_business_date as lastClosedBusinessDate,l.overpaidon_date as overpaidOnDate, "
                    + " l.is_charged_off as isChargedOff, l.charge_off_reason_cv_id as chargeOffReasonId, codec.code_value as chargeOffReason, l.charged_off_on_date as chargedOffOnDate, l.enable_down_payment as enableDownPayment, l.disbursed_amount_percentage_for_down_payment as disbursedAmountPercentageForDownPayment, l.enable_auto_repayment_for_down_payment as enableAutoRepaymentForDownPayment,"
                    + " cobu.username as chargedOffByUsername, cobu.firstname as chargedOffByFirstname, cobu.lastname as chargedOffByLastname, l.loan_schedule_type as loanScheduleType, l.loan_schedule_processing_type as loanScheduleProcessingType, "
                    + " l.charge_off_behaviour as chargeOffBehaviour, l.interest_recognition_on_disbursement_date as interestRecognitionOnDisbursementDate, "
                    + " llocp.line_of_credit_id as lineOfCreditId, " + " llocp.invoice_no as invoiceNo, llocp.invoice_date as invoiceDate, "
                    + " llocp.invoice_due_date as invoiceDueDate, llocp.invoice_currency as invoiceCurrency, "
                    + " llocp.invoice_amount as invoiceAmount, llocp.disapproved_amount as disapprovedAmount, "
                    + " llocp.approved_receivable_amount as approvedReceivableAmount, llocp.advance_percentage as advancePercentage, "
                    + " llocp.amount_after_advance as amountAfterAdvance, llocp.buyer_details as buyerDetails, "
                    + " llocp.exchange_rate as exchangeRate, llocp.markup as markup, "
                    + " llocp.amount_in_facility_currency as amountInFacilityCurrency, "
                    + " llocp.approved_payable_amount as approvedPayableAmount, "
                    + " loc.external_id as locExternalId, loc.activation_status as locActivationStatus,loc.product_type as locProductType, "////
                    + " l.is_factor_rate_enabled AS factorRateEnabled, l.factor_rate AS factorRate, "
                    + " llocp.approved_payable_amount as approvedPayableAmount, llocp.supplier_details as supplierDetails " ////
                    + " from m_loan l" //
                    + " join m_product_loan lp on lp.id = l.product_id" //
                    + " left join m_loan_recalculation_details lir on lir.loan_id = l.id join m_currency rc on rc."
                    + sqlGenerator.escape("code") + " = l.currency_code" //
                    + " left join m_client c on c.id = l.client_id" //
                    + " left join m_group g on g.id = l.group_id" //
                    + " left join m_loan_arrears_aging la on la.loan_id = l.id" //
                    + " left join m_fund f on f.id = l.fund_id" //
                    + " left join m_staff s on s.id = l.loan_officer_id" //
                    + " left join m_appuser sbu on sbu.id = l.created_by left join m_appuser rbu on rbu.id = l.rejectedon_userid"
                    + " left join m_appuser wbu on wbu.id = l.withdrawnon_userid"
                    + " left join m_appuser abu on abu.id = l.approvedon_userid"
                    + " left join m_appuser dbu on dbu.id = l.disbursedon_userid left join m_appuser cbu on cbu.id = l.closedon_userid"
                    + " left join m_appuser cobu on cobu.id = l.charged_off_by_userid "
                    + " left join m_code_value cv on cv.id = l.loanpurpose_cv_id"
                    + " left join m_code_value codev on codev.id = l.writeoff_reason_cv_id"
                    + " left join m_code_value codec on codec.id = l.charge_off_reason_cv_id"
                    + " left join m_product_loan_variable_installment_config lpvi on lpvi.loan_product_id = l.product_id"
                    + " left join m_loan_topup as topup on l.id = topup.loan_id"
                    + " left join m_loan as topuploan on topuploan.id = topup.closure_loan_id "
                    + " left join m_loan_line_of_credit_params llocp on llocp.loan_id = l.id "
                    + " left join m_line_of_credit loc on loc.id = llocp.line_of_credit_id ";

        }

        @Override
        public ExtendedLoanAccountData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final String currencyCode = rs.getString("currencyCode");
            final String currencyName = rs.getString("currencyName");
            final String currencyNameCode = rs.getString("currencyNameCode");
            final String currencyDisplaySymbol = rs.getString("currencyDisplaySymbol");
            final Integer currencyDigits = JdbcSupport.getInteger(rs, "currencyDigits");
            final Integer inMultiplesOf = JdbcSupport.getInteger(rs, "inMultiplesOf");
            final CurrencyData currencyData = new CurrencyData(currencyCode, currencyName, currencyDigits, inMultiplesOf,
                    currencyDisplaySymbol, currencyNameCode);

            final Long id = rs.getLong("id");
            final String accountNo = rs.getString("accountNo");
            final String externalIdStr = rs.getString("externalId");
            final ExternalId externalId = ExternalIdFactory.produce(externalIdStr);

            final Long clientId = JdbcSupport.getLong(rs, "clientId");
            final String clientAccountNo = rs.getString("clientAccountNo");
            final Long clientOfficeId = JdbcSupport.getLong(rs, "clientOfficeId");
            final ExternalId clientExternalId = ExternalIdFactory.produce(rs.getString("clientExternalId"));
            final String clientName = rs.getString("clientName");

            final Long groupId = JdbcSupport.getLong(rs, "groupId");
            final String groupName = rs.getString("groupName");
            final String groupAccountNo = rs.getString("groupAccountNo");
            final String groupExternalId = rs.getString("groupExternalId");
            final Long groupOfficeId = JdbcSupport.getLong(rs, "groupOfficeId");
            final Long groupStaffId = JdbcSupport.getLong(rs, "groupStaffId");
            final Long groupParentId = JdbcSupport.getLong(rs, "groupParentId");
            final String centerName = rs.getString("centerName");
            final String groupHierarchy = rs.getString("groupHierarchy");
            final String groupLevel = rs.getString("groupLevel");

            final Integer loanTypeId = JdbcSupport.getInteger(rs, "loanType");
            final EnumOptionData loanType = AccountEnumerations.loanType(loanTypeId);

            final Long fundId = JdbcSupport.getLong(rs, "fundId");
            final String fundName = rs.getString("fundName");

            final Long loanOfficerId = JdbcSupport.getLong(rs, "loanOfficerId");
            final String loanOfficerName = rs.getString("loanOfficerName");

            final Long loanPurposeId = JdbcSupport.getLong(rs, "loanPurposeId");
            final String loanPurposeName = rs.getString("loanPurposeName");

            final Long loanProductId = JdbcSupport.getLong(rs, "loanProductId");
            final String loanProductName = rs.getString("loanProductName");
            final String loanProductDescription = rs.getString("loanProductDescription");
            final boolean isLoanProductLinkedToFloatingRate = rs.getBoolean("isLoanProductLinkedToFloatingRate");
            final Boolean multiDisburseLoan = rs.getBoolean("multiDisburseLoan");
            final Boolean canDefineInstallmentAmount = rs.getBoolean("canDefineInstallmentAmount");
            final BigDecimal outstandingLoanBalance = rs.getBigDecimal("outstandingLoanBalance");

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

            final LocalDate writtenOffOnDate = JdbcSupport.getLocalDate(rs, "writtenOffOnDate");
            final Long writeoffReasonId = JdbcSupport.getLong(rs, "writeoffReasonId");
            final String writeoffReason = rs.getString("writeoffReason");
            final LocalDate actualMaturityDate = JdbcSupport.getLocalDate(rs, "actualMaturityDate");
            final LocalDate expectedMaturityDate = JdbcSupport.getLocalDate(rs, "expectedMaturityDate");

            final Boolean isvariableInstallmentsAllowed = rs.getBoolean("isvariableInstallmentsAllowed");
            final Integer minimumGap = rs.getInt("minimuminstallmentgap");
            final Integer maximumGap = rs.getInt("maximuminstallmentgap");

            final LocalDate chargedOffOnDate = JdbcSupport.getLocalDate(rs, "chargedOffOnDate");
            final String chargedOffByUsername = rs.getString("chargedOffByUsername");
            final String chargedOffByFirstname = rs.getString("chargedOffByFirstname");
            final String chargedOffByLastname = rs.getString("chargedOffByLastname");
            final Long chargeOffReasonId = JdbcSupport.getLong(rs, "chargeOffReasonId");
            final String chargeOffReason = rs.getString("chargeOffReason");
            final boolean isChargedOff = rs.getBoolean("isChargedOff");

            final LoanApplicationTimelineData timeline = new LoanApplicationTimelineData(submittedOnDate, submittedByUsername,
                    submittedByFirstname, submittedByLastname, rejectedOnDate, rejectedByUsername, rejectedByFirstname, rejectedByLastname,
                    withdrawnOnDate, withdrawnByUsername, withdrawnByFirstname, withdrawnByLastname, approvedOnDate, approvedByUsername,
                    approvedByFirstname, approvedByLastname, expectedDisbursementDate, actualDisbursementDate, disbursedByUsername,
                    disbursedByFirstname, disbursedByLastname, closedOnDate, closedByUsername, closedByFirstname, closedByLastname,
                    actualMaturityDate, expectedMaturityDate, writtenOffOnDate, closedByUsername, closedByFirstname, closedByLastname,
                    chargedOffOnDate, chargedOffByUsername, chargedOffByFirstname, chargedOffByLastname);

            final BigDecimal principal = rs.getBigDecimal("principal");
            final BigDecimal approvedPrincipal = rs.getBigDecimal("approvedPrincipal");
            final BigDecimal proposedPrincipal = rs.getBigDecimal("proposedPrincipal");
            final BigDecimal netDisbursalAmount = rs.getBigDecimal("netDisbursalAmount");
            final BigDecimal totalOverpaid = rs.getBigDecimal("totalOverpaid");
            final BigDecimal inArrearsTolerance = rs.getBigDecimal("inArrearsTolerance");

            final Integer numberOfRepayments = JdbcSupport.getInteger(rs, "numberOfRepayments");
            final Integer repaymentEvery = JdbcSupport.getInteger(rs, "repaymentEvery");
            final BigDecimal interestRatePerPeriod = rs.getBigDecimal("interestRatePerPeriod");
            final BigDecimal annualInterestRate = rs.getBigDecimal("annualInterestRate");
            final BigDecimal interestRateDifferential = rs.getBigDecimal("interestRateDifferential");
            final boolean isFloatingInterestRate = rs.getBoolean("isFloatingInterestRate");

            final Integer graceOnPrincipalPayment = JdbcSupport.getIntegerDefaultToNullIfZero(rs, "graceOnPrincipalPayment");
            final Integer recurringMoratoriumOnPrincipalPeriods = JdbcSupport.getIntegerDefaultToNullIfZero(rs,
                    "recurringMoratoriumOnPrincipalPeriods");
            final Integer graceOnInterestPayment = JdbcSupport.getIntegerDefaultToNullIfZero(rs, "graceOnInterestPayment");
            final Integer graceOnInterestCharged = JdbcSupport.getIntegerDefaultToNullIfZero(rs, "graceOnInterestCharged");
            final Integer graceOnArrearsAgeing = JdbcSupport.getIntegerDefaultToNullIfZero(rs, "graceOnArrearsAgeing");

            final Integer termFrequency = JdbcSupport.getInteger(rs, "termFrequency");
            final Integer termPeriodFrequencyTypeInt = JdbcSupport.getInteger(rs, "termPeriodFrequencyType");
            final EnumOptionData termPeriodFrequencyType = LoanEnumerations.termFrequencyType(termPeriodFrequencyTypeInt);

            final int repaymentFrequencyTypeInt = JdbcSupport.getInteger(rs, "repaymentFrequencyType");
            final EnumOptionData repaymentFrequencyType = LoanEnumerations.repaymentFrequencyType(repaymentFrequencyTypeInt);

            final int interestRateFrequencyTypeInt = JdbcSupport.getInteger(rs, "interestRateFrequencyType");
            final EnumOptionData interestRateFrequencyType = LoanEnumerations.interestRateFrequencyType(interestRateFrequencyTypeInt);

            final String transactionStrategyCode = rs.getString("transactionStrategyCode");
            final String transactionStrategyName = rs.getString("transactionStrategyName");

            final int amortizationTypeInt = JdbcSupport.getInteger(rs, "amortizationType");
            final int interestTypeInt = JdbcSupport.getInteger(rs, "interestType");
            final int interestCalculationPeriodTypeInt = JdbcSupport.getInteger(rs, "interestCalculationPeriodType");
            final boolean isEqualAmortization = rs.getBoolean("isEqualAmortization");
            final EnumOptionData amortizationType = LoanEnumerations.amortizationType(amortizationTypeInt);
            final BigDecimal fixedPrincipalPercentagePerInstallment = rs.getBigDecimal("fixedPrincipalPercentagePerInstallment");
            final EnumOptionData interestType = LoanEnumerations.interestType(interestTypeInt);
            final EnumOptionData interestCalculationPeriodType = LoanEnumerations
                    .interestCalculationPeriodType(interestCalculationPeriodTypeInt);
            final Boolean allowPartialPeriodInterestCalcualtion = rs.getBoolean("allowPartialPeriodInterestCalcualtion");

            final Integer lifeCycleStatusId = JdbcSupport.getInteger(rs, "lifeCycleStatusId");
            final LoanStatusEnumData status = LoanEnumerations.status(lifeCycleStatusId);

            final Integer loanSubStatusId = JdbcSupport.getInteger(rs, "loanSubStatusId");
            EnumOptionData loanSubStatus = null;
            if (loanSubStatusId != null) {
                loanSubStatus = LoanSubStatus.loanSubStatus(loanSubStatusId);
            }

            // settings
            final LocalDate expectedFirstRepaymentOnDate = JdbcSupport.getLocalDate(rs, "expectedFirstRepaymentOnDate");
            final LocalDate interestChargedFromDate = JdbcSupport.getLocalDate(rs, "interestChargedFromDate");

            final Boolean syncDisbursementWithMeeting = rs.getBoolean("syncDisbursementWithMeeting");

            final BigDecimal feeChargesDueAtDisbursementCharged = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs,
                    "feeChargesDueAtDisbursementCharged");
            LoanSummaryData loanSummary = null;
            Boolean inArrears = false;
            if (status.getId().intValue() >= 300) {

                // loan summary
                final BigDecimal principalDisbursed = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalDisbursed");
                final BigDecimal principalAdjustments = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalAdjustments");
                final BigDecimal principalPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalPaid");
                final BigDecimal principalWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalWrittenOff");
                final BigDecimal principalOutstanding = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalOutstanding");
                final BigDecimal principalOverdue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "principalOverdue");

                final BigDecimal interestCharged = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestCharged");
                final BigDecimal interestPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestPaid");
                final BigDecimal interestWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestWaived");
                final BigDecimal interestWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestWrittenOff");
                final BigDecimal interestOutstanding = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestOutstanding");
                final BigDecimal interestOverdue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "interestOverdue");

                final BigDecimal feeChargesCharged = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesCharged");
                final BigDecimal feeAdjustments = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeAdjustments");
                final BigDecimal feeChargesPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesPaid");
                final BigDecimal feeChargesWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesWaived");
                final BigDecimal feeChargesWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesWrittenOff");
                final BigDecimal feeChargesOutstanding = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesOutstanding");
                final BigDecimal feeChargesOverdue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "feeChargesOverdue");

                final BigDecimal penaltyChargesCharged = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesCharged");
                final BigDecimal penaltyAdjustments = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyAdjustments");
                final BigDecimal penaltyChargesPaid = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesPaid");
                final BigDecimal penaltyChargesWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesWaived");
                final BigDecimal penaltyChargesWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesWrittenOff");
                final BigDecimal penaltyChargesOutstanding = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesOutstanding");
                final BigDecimal penaltyChargesOverdue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "penaltyChargesOverdue");

                final BigDecimal totalExpectedRepayment = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalExpectedRepayment");
                final BigDecimal totalRepayment = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalRepayment");
                final BigDecimal totalExpectedCostOfLoan = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalExpectedCostOfLoan");
                final BigDecimal totalCostOfLoan = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalCostOfLoan");
                final BigDecimal totalWaived = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalWaived");
                final BigDecimal totalWrittenOff = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalWrittenOff");
                final BigDecimal totalOutstanding = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalOutstanding");
                final BigDecimal totalOverdue = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalOverdue");
                final BigDecimal totalRecovered = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "totalRecovered");

                final LocalDate overdueSinceDate = JdbcSupport.getLocalDate(rs, "overdueSinceDate");
                inArrears = (overdueSinceDate != null);

                loanSummary = LoanSummaryData.builder().currency(currencyData).principalDisbursed(principalDisbursed)
                        .principalAdjustments(principalAdjustments).principalPaid(principalPaid).principalWrittenOff(principalWrittenOff)
                        .principalOutstanding(principalOutstanding).principalOverdue(principalOverdue).interestCharged(interestCharged)
                        .interestPaid(interestPaid).interestWaived(interestWaived).interestWrittenOff(interestWrittenOff)
                        .interestOutstanding(interestOutstanding).interestOverdue(interestOverdue).feeChargesCharged(feeChargesCharged)
                        .feeAdjustments(feeAdjustments).feeChargesDueAtDisbursementCharged(feeChargesDueAtDisbursementCharged)
                        .feeChargesPaid(feeChargesPaid).feeChargesWaived(feeChargesWaived).feeChargesWrittenOff(feeChargesWrittenOff)
                        .feeChargesOutstanding(feeChargesOutstanding).feeChargesOverdue(feeChargesOverdue)
                        .penaltyChargesCharged(penaltyChargesCharged).penaltyAdjustments(penaltyAdjustments)
                        .penaltyChargesPaid(penaltyChargesPaid).penaltyChargesWaived(penaltyChargesWaived)
                        .penaltyChargesWrittenOff(penaltyChargesWrittenOff).penaltyChargesOutstanding(penaltyChargesOutstanding)
                        .penaltyChargesOverdue(penaltyChargesOverdue).totalExpectedRepayment(totalExpectedRepayment)
                        .totalRepayment(totalRepayment).totalExpectedCostOfLoan(totalExpectedCostOfLoan).totalCostOfLoan(totalCostOfLoan)
                        .totalWaived(totalWaived).totalWrittenOff(totalWrittenOff).totalOutstanding(totalOutstanding)
                        .totalOverdue(totalOverdue).overdueSinceDate(overdueSinceDate).writeoffReasonId(writeoffReasonId)
                        .writeoffReason(writeoffReason).totalRecovered(totalRecovered).chargeOffReasonId(chargeOffReasonId)
                        .chargeOffReason(chargeOffReason).build();

            }

            GroupGeneralData groupData = null;
            if (groupId != null) {
                final Integer groupStatusEnum = JdbcSupport.getInteger(rs, "statusEnum");
                final EnumOptionData groupStatus = ClientEnumerations.status(groupStatusEnum);
                final LocalDate activationDate = JdbcSupport.getLocalDate(rs, "activationDate");
                groupData = GroupGeneralData.instance(groupId, groupAccountNo, groupName, groupExternalId, groupStatus, activationDate,
                        groupOfficeId, null, groupParentId, centerName, groupStaffId, null, groupHierarchy, groupLevel, null);
            }

            final Integer loanCounter = JdbcSupport.getInteger(rs, "loanCounter");
            final Integer loanProductCounter = JdbcSupport.getInteger(rs, "loanProductCounter");
            final BigDecimal fixedEmiAmount = JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "fixedEmiAmount");
            final Boolean isNPA = rs.getBoolean("isNPA");

            final int daysInMonth = JdbcSupport.getInteger(rs, "daysInMonth");
            final EnumOptionData daysInMonthType = CommonEnumerations.daysInMonthType(daysInMonth);
            final int daysInYear = JdbcSupport.getInteger(rs, "daysInYear");
            final EnumOptionData daysInYearType = CommonEnumerations.daysInYearType(daysInYear);
            final boolean isInterestRecalculationEnabled = rs.getBoolean("isInterestRecalculationEnabled");
            final Boolean createStandingInstructionAtDisbursement = rs.getBoolean("createStandingInstructionAtDisbursement");

            LoanInterestRecalculationData interestRecalculationData = null;
            if (isInterestRecalculationEnabled) {

                final Long lprId = JdbcSupport.getLong(rs, "lirId");
                final Long productId = JdbcSupport.getLong(rs, "loanId");
                final int compoundTypeEnumValue = JdbcSupport.getInteger(rs, "compoundType");
                final EnumOptionData interestRecalculationCompoundingType = LoanEnumerations
                        .interestRecalculationCompoundingType(compoundTypeEnumValue);
                final int rescheduleStrategyEnumValue = JdbcSupport.getInteger(rs, "rescheduleStrategy");
                final EnumOptionData rescheduleStrategyType = LoanEnumerations.rescheduleStrategyType(rescheduleStrategyEnumValue);
                final CalendarData calendarData = null;
                final int restFrequencyEnumValue = JdbcSupport.getInteger(rs, "restFrequencyEnum");
                final EnumOptionData restFrequencyType = LoanEnumerations.interestRecalculationFrequencyType(restFrequencyEnumValue);
                final int restFrequencyInterval = JdbcSupport.getInteger(rs, "restFrequencyInterval");
                final Integer restFrequencyNthDayEnumValue = JdbcSupport.getInteger(rs, "restFrequencyNthDayEnum");
                EnumOptionData restFrequencyNthDayEnum = null;
                if (restFrequencyNthDayEnumValue != null) {
                    restFrequencyNthDayEnum = LoanEnumerations.interestRecalculationCompoundingNthDayType(restFrequencyNthDayEnumValue);
                }
                final Integer restFrequencyWeekDayEnumValue = JdbcSupport.getInteger(rs, "restFrequencyWeekDayEnum");
                EnumOptionData restFrequencyWeekDayEnum = null;
                if (restFrequencyWeekDayEnumValue != null) {
                    restFrequencyWeekDayEnum = LoanEnumerations
                            .interestRecalculationCompoundingDayOfWeekType(restFrequencyWeekDayEnumValue);
                }
                final Integer restFrequencyOnDay = JdbcSupport.getInteger(rs, "restFrequencyOnDay");
                final CalendarData compoundingCalendarData = null;
                final Integer compoundingFrequencyEnumValue = JdbcSupport.getInteger(rs, "compoundingFrequencyEnum");
                EnumOptionData compoundingFrequencyType = null;
                if (compoundingFrequencyEnumValue != null) {
                    compoundingFrequencyType = LoanEnumerations.interestRecalculationFrequencyType(compoundingFrequencyEnumValue);
                }
                final Integer compoundingInterval = JdbcSupport.getInteger(rs, "compoundingInterval");
                final Integer compoundingFrequencyNthDayEnumValue = JdbcSupport.getInteger(rs, "compoundingFrequencyNthDayEnum");
                EnumOptionData compoundingFrequencyNthDayEnum = null;
                if (compoundingFrequencyNthDayEnumValue != null) {
                    compoundingFrequencyNthDayEnum = LoanEnumerations
                            .interestRecalculationCompoundingNthDayType(compoundingFrequencyNthDayEnumValue);
                }
                final Integer compoundingFrequencyWeekDayEnumValue = JdbcSupport.getInteger(rs, "compoundingFrequencyWeekDayEnum");
                EnumOptionData compoundingFrequencyWeekDayEnum = null;
                if (compoundingFrequencyWeekDayEnumValue != null) {
                    compoundingFrequencyWeekDayEnum = LoanEnumerations
                            .interestRecalculationCompoundingDayOfWeekType(compoundingFrequencyWeekDayEnumValue);
                }
                final Integer compoundingFrequencyOnDay = JdbcSupport.getInteger(rs, "compoundingFrequencyOnDay");

                final Boolean isCompoundingToBePostedAsTransaction = rs.getBoolean("isCompoundingToBePostedAsTransaction");
                final Boolean allowCompoundingOnEod = rs.getBoolean("allowCompoundingOnEod");
                final Boolean disallowInterestCalculationOnPastDue = rs.getBoolean("disallowInterestCalculationOnPastDue");
                interestRecalculationData = new LoanInterestRecalculationData(lprId, productId, interestRecalculationCompoundingType,
                        rescheduleStrategyType, calendarData, restFrequencyType, restFrequencyInterval, restFrequencyNthDayEnum,
                        restFrequencyWeekDayEnum, restFrequencyOnDay, compoundingCalendarData, compoundingFrequencyType,
                        compoundingInterval, compoundingFrequencyNthDayEnum, compoundingFrequencyWeekDayEnum, compoundingFrequencyOnDay,
                        isCompoundingToBePostedAsTransaction, allowCompoundingOnEod, disallowInterestCalculationOnPastDue);
            }

            final boolean canUseForTopup = rs.getBoolean("canUseForTopup");
            final boolean isTopup = rs.getBoolean("isTopup");
            final Long closureLoanId = rs.getLong("closureLoanId");
            final String closureLoanAccountNo = rs.getString("closureLoanAccountNo");
            final BigDecimal topupAmount = rs.getBigDecimal("topupAmount");
            final boolean disallowExpectedDisbursements = rs.getBoolean("disallowExpectedDisbursements");
            // Current Delinquency Range Data
            DelinquencyRangeData delinquencyRange = this.delinquencyReadPlatformService.retrieveCurrentDelinquencyTag(id);

            final boolean isFraud = rs.getBoolean("isFraud");
            final LocalDate lastClosedBusinessDate = JdbcSupport.getLocalDate(rs, "lastClosedBusinessDate");
            final LocalDate overpaidOnDate = JdbcSupport.getLocalDate(rs, "overpaidOnDate");

            final boolean enableDownPayment = rs.getBoolean("enableDownPayment");
            final BigDecimal disbursedAmountPercentageForDownPayment = rs.getBigDecimal("disbursedAmountPercentageForDownPayment");
            final boolean enableAutoRepaymentForDownPayment = rs.getBoolean("enableAutoRepaymentForDownPayment");
            final boolean enableInstallmentLevelDelinquency = rs.getBoolean("enableInstallmentLevelDelinquency");
            final String loanScheduleTypeStr = rs.getString("loanScheduleType");
            final LoanScheduleType loanScheduleType = LoanScheduleType.valueOf(loanScheduleTypeStr);
            final String loanScheduleProcessingTypeStr = rs.getString("loanScheduleProcessingType");
            final LoanScheduleProcessingType loanScheduleProcessingType = LoanScheduleProcessingType.valueOf(loanScheduleProcessingTypeStr);
            final Integer fixedLength = JdbcSupport.getInteger(rs, "fixedLength");
            final LoanChargeOffBehaviour chargeOffBehaviour = LoanChargeOffBehaviour.valueOf(rs.getString("chargeOffBehaviour"));
            final boolean interestRecognitionOnDisbursementDate = rs.getBoolean("interestRecognitionOnDisbursementDate");
            final StringEnumOptionData daysInYearCustomStrategy = ApiFacingEnum.getStringEnumOptionData(DaysInYearCustomStrategyType.class,
                    rs.getString("daysInYearCustomStrategy"));
            final boolean enableIncomeCapitalization = rs.getBoolean("enableIncomeCapitalization");
            final StringEnumOptionData capitalizedIncomeCalculationType = ApiFacingEnum
                    .getStringEnumOptionData(LoanCapitalizedIncomeCalculationType.class, rs.getString("capitalizedIncomeCalculationType"));
            final StringEnumOptionData capitalizedIncomeStrategy = ApiFacingEnum
                    .getStringEnumOptionData(LoanCapitalizedIncomeStrategy.class, rs.getString("capitalizedIncomeStrategy"));
            // Adding new fields as per CredibleX requirements
            final Boolean isForcedClosure = rs.getBoolean("isForcedClosure");
            final Boolean isRestructured = rs.getBoolean("isRestructured");

            final boolean factorRateEnabled = rs.getBoolean("factorRateEnabled");
            final BigDecimal factorRate = rs.getBigDecimal("factorRate");

            ExtendedLoanAccountData extendedLoanAccountData = ExtendedLoanAccountData.basicLoanDetails(id, accountNo, status, externalId,
                    clientId, clientAccountNo, clientName, clientOfficeId, clientExternalId, groupData, loanType, loanProductId,
                    loanProductName, loanProductDescription, isLoanProductLinkedToFloatingRate, fundId, fundName, loanPurposeId,
                    loanPurposeName, loanOfficerId, loanOfficerName, currencyData, proposedPrincipal, principal, approvedPrincipal,
                    netDisbursalAmount, totalOverpaid, inArrearsTolerance, termFrequency, termPeriodFrequencyType, numberOfRepayments,
                    repaymentEvery, repaymentFrequencyType, null, null, transactionStrategyCode, transactionStrategyName, amortizationType,
                    interestRatePerPeriod, interestRateFrequencyType, annualInterestRate, interestType, isFloatingInterestRate,
                    interestRateDifferential, interestCalculationPeriodType, allowPartialPeriodInterestCalcualtion,
                    expectedFirstRepaymentOnDate, graceOnPrincipalPayment, recurringMoratoriumOnPrincipalPeriods, graceOnInterestPayment,
                    graceOnInterestCharged, interestChargedFromDate, timeline, loanSummary, feeChargesDueAtDisbursementCharged,
                    syncDisbursementWithMeeting, loanCounter, loanProductCounter, multiDisburseLoan, canDefineInstallmentAmount,
                    fixedEmiAmount, outstandingLoanBalance, inArrears, graceOnArrearsAgeing, isNPA, daysInMonthType, daysInYearType,
                    isInterestRecalculationEnabled, interestRecalculationData, createStandingInstructionAtDisbursement,
                    isvariableInstallmentsAllowed, minimumGap, maximumGap, loanSubStatus, canUseForTopup, isTopup, closureLoanId,
                    closureLoanAccountNo, topupAmount, isEqualAmortization, fixedPrincipalPercentagePerInstallment, delinquencyRange,
                    disallowExpectedDisbursements, isFraud, lastClosedBusinessDate, overpaidOnDate, isChargedOff, enableDownPayment,
                    disbursedAmountPercentageForDownPayment, enableAutoRepaymentForDownPayment, enableInstallmentLevelDelinquency,
                    loanScheduleType.asEnumOptionData(), loanScheduleProcessingType.asEnumOptionData(), fixedLength,
                    chargeOffBehaviour.getValueAsStringEnumOptionData(), interestRecognitionOnDisbursementDate, daysInYearCustomStrategy,
                    enableIncomeCapitalization, capitalizedIncomeCalculationType, capitalizedIncomeStrategy);

            // Adding custom parameters for CredibleX requirements
            extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.IS_FORCED_CLOSURE, isForcedClosure);
            extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.IS_RESTRUCTURED, isRestructured);

            extractLocDetails(extendedLoanAccountData, rs);

            extendedLoanAccountData.setFactorRate(factorRate);
            extendedLoanAccountData.setFactorRateEnabled(factorRateEnabled);

            return extendedLoanAccountData;

        }

        private void extractLocDetails(ExtendedLoanAccountData extendedLoanAccountData, ResultSet rs) throws SQLException {

            // Extract LOC params fields from the result set
            final Long locLineOfCreditId = JdbcSupport.getLong(rs, LoanAccountAdditionalProperties.LINE_OF_CREDIT_ID);
            final String locInvoiceNo = rs.getString(LoanAccountAdditionalProperties.INVOICE_NO);
            final LocalDate locInvoiceDate = JdbcSupport.getLocalDate(rs, LoanAccountAdditionalProperties.INVOICE_DATE);
            final LocalDate locInvoiceDueDate = JdbcSupport.getLocalDate(rs, LoanAccountAdditionalProperties.INVOICE_DUE_DATE);
            final String locInvoiceCurrency = rs.getString(LoanAccountAdditionalProperties.INVOICE_CURRENCY);
            final BigDecimal locInvoiceAmount = rs.getBigDecimal(LoanAccountAdditionalProperties.INVOICE_AMOUNT);
            final BigDecimal locDisapprovedAmount = rs.getBigDecimal(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT);
            final BigDecimal locApprovedReceivableAmount = rs.getBigDecimal(LoanAccountAdditionalProperties.APPROVED_RECEIVABLE_AMOUNT);
            final BigDecimal locAdvancePercentage = rs.getBigDecimal(LoanAccountAdditionalProperties.ADVANCE_PERCENTAGE);
            final BigDecimal locAmountAfterAdvance = rs.getBigDecimal(LoanAccountAdditionalProperties.AMOUNT_AFTER_ADVANCE);
            final String locBuyerDetails = rs.getString(LoanAccountAdditionalProperties.BUYER_DETAILS);
            final BigDecimal locExchangeRate = rs.getBigDecimal(LoanAccountAdditionalProperties.EXCHANGE_RATE);
            final BigDecimal locMarkup = rs.getBigDecimal(LoanAccountAdditionalProperties.MARKUP);
            final BigDecimal locAmountInFacilityCurrency = rs.getBigDecimal(LoanAccountAdditionalProperties.AMOUNT_IN_FACILITY_CURRENCY);
            final BigDecimal locApprovedPayableAmount = rs.getBigDecimal(LoanAccountAdditionalProperties.APPROVED_PAYABLE_AMOUNT);
            final String locExternalId = rs.getString("locExternalId");
            final String locProductType = rs.getString("locProductType");
            final String locActivationStatus = rs.getString("locActivationStatus");

            // Add them to custom parameters if they are not null
            if (locProductType != null) {
                extendedLoanAccountData.addCustomParameter("locProductType", locProductType);
            }
            if (locActivationStatus != null) {
                extendedLoanAccountData.addCustomParameter("locActivationStatus", locActivationStatus);
            }
            if (locExternalId != null) {
                extendedLoanAccountData.addCustomParameter("locExternalId", locExternalId);
            }

            if (locLineOfCreditId != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.LINE_OF_CREDIT_ID, locLineOfCreditId);
            }
            if (locInvoiceNo != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.INVOICE_NO, locInvoiceNo);
            }
            if (locInvoiceDate != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.INVOICE_DATE, locInvoiceDate);
            }
            if (locInvoiceDueDate != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.INVOICE_DUE_DATE, locInvoiceDueDate);
            }
            if (locInvoiceCurrency != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.INVOICE_CURRENCY, locInvoiceCurrency);
            }
            if (locInvoiceAmount != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.INVOICE_AMOUNT, locInvoiceAmount);
            }
            if (locDisapprovedAmount != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.DISAPPROVED_AMOUNT, locDisapprovedAmount);
            }
            if (locApprovedReceivableAmount != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.APPROVED_RECEIVABLE_AMOUNT,
                        locApprovedReceivableAmount);
            }
            if (locAdvancePercentage != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.ADVANCE_PERCENTAGE, locAdvancePercentage);
            }
            if (locAmountAfterAdvance != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.AMOUNT_AFTER_ADVANCE, locAmountAfterAdvance);
            }
            if (locBuyerDetails != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.BUYER_DETAILS, locBuyerDetails);
            }
            if (locExchangeRate != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.EXCHANGE_RATE, locExchangeRate);
            }
            if (locMarkup != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.MARKUP, locMarkup);
            }
            if (locAmountInFacilityCurrency != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.AMOUNT_IN_FACILITY_CURRENCY,
                        locAmountInFacilityCurrency);
            }
            if (locApprovedPayableAmount != null) {
                extendedLoanAccountData.addCustomParameter(LoanAccountAdditionalProperties.APPROVED_PAYABLE_AMOUNT,
                        locApprovedPayableAmount);
            }

        }
    }

    @Override
    public Collection<DisbursementData> retrieveLoanDisbursementDetails(final Long loanId) {
        final CredXLoanDisbursementDetailMapper rm = new CredXLoanDisbursementDetailMapper(sqlGenerator);
        final String sql = "select " + rm.schema()
                + " where dd.loan_id=? and dd.is_reversed=false group by dd.id, lc.amount_waived_derived order by dd.expected_disburse_date,dd.disbursedon_date,dd.id";
        return this.jdbcTemplate.query(sql, rm, loanId); // NOSONAR
    }

    private static final class CredXLoanDisbursementDetailMapper implements RowMapper<DisbursementData> {

        private final DatabaseSpecificSQLGenerator sqlGenerator;

        CredXLoanDisbursementDetailMapper(DatabaseSpecificSQLGenerator sqlGenerator) {
            this.sqlGenerator = sqlGenerator;
        }

        public String schema() {
            return "dd.id as id,dd.expected_disburse_date as expectedDisbursementdate, dd.disbursedon_date as actualDisbursementdate,dd.principal as principal,dd.net_disbursal_amount as netDisbursalAmount,sum(lc.amount) chargeAmount, lc.amount_waived_derived waivedAmount, sum(lc.tax_amount) as taxAmount, "
                    + sqlGenerator.groupConcat("lc.id") + " loanChargeId "
                    + "from m_loan l inner join m_loan_disbursement_detail dd on dd.loan_id = l.id left join m_loan_tranche_disbursement_charge tdc on tdc.disbursement_detail_id=dd.id "
                    + "left join m_loan_charge lc on  lc.id=tdc.loan_charge_id and lc.is_active=true";
        }

        @Override
        public DisbursementData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final LocalDate expectedDisbursementdate = JdbcSupport.getLocalDate(rs, "expectedDisbursementdate");
            final LocalDate actualDisbursementdate = JdbcSupport.getLocalDate(rs, "actualDisbursementdate");
            final BigDecimal principal = rs.getBigDecimal("principal");
            final String loanChargeId = rs.getString("loanChargeId");
            final BigDecimal netDisbursalAmount = rs.getBigDecimal("netDisbursalAmount");
            BigDecimal chargeAmount = rs.getBigDecimal("chargeAmount");
            final BigDecimal waivedAmount = rs.getBigDecimal("waivedAmount");
            final BigDecimal taxAmount = rs.getBigDecimal("taxAmount");
            if (chargeAmount != null && waivedAmount != null) {
                chargeAmount = chargeAmount.subtract(waivedAmount);
            }

            if (chargeAmount != null && taxAmount != null) {
                // Assumption is that tax is not waivable, so we add it to the charge amount
                chargeAmount = chargeAmount.add(taxAmount);
            }
            return new DisbursementData(id, expectedDisbursementdate, actualDisbursementdate, principal, netDisbursalAmount, loanChargeId,
                    chargeAmount, waivedAmount);
        }

    }

    @Override
    public LoanAccountData retrieveLoanProductDetailsTemplate(final Long productId, final Long clientId, final Long groupId) {

        this.context.authenticatedUser();

        final ExtendedLoanProductData loanProduct = (ExtendedLoanProductData) this.loanProductReadPlatformService
                .retrieveLoanProduct(productId);
        final Collection<EnumOptionData> loanTermFrequencyTypeOptions = this.loanDropdownReadPlatformService
                .retrieveLoanTermFrequencyTypeOptions();
        final Collection<EnumOptionData> repaymentFrequencyTypeOptions = this.loanDropdownReadPlatformService
                .retrieveRepaymentFrequencyTypeOptions();
        final Collection<EnumOptionData> repaymentFrequencyNthDayTypeOptions = this.loanDropdownReadPlatformService
                .retrieveRepaymentFrequencyOptionsForNthDayOfMonth();
        final Collection<EnumOptionData> repaymentFrequencyDaysOfWeekTypeOptions = this.loanDropdownReadPlatformService
                .retrieveRepaymentFrequencyOptionsForDaysOfWeek();
        final Collection<EnumOptionData> interestRateFrequencyTypeOptions = this.loanDropdownReadPlatformService
                .retrieveInterestRateFrequencyTypeOptions();
        final Collection<EnumOptionData> amortizationTypeOptions = this.loanDropdownReadPlatformService
                .retrieveLoanAmortizationTypeOptions();
        Collection<EnumOptionData> interestTypeOptions = null;
        if (loanProduct.isLinkedToFloatingInterestRates()) {
            interestTypeOptions = List.of(interestType(InterestMethod.DECLINING_BALANCE));
        } else {
            interestTypeOptions = this.loanDropdownReadPlatformService.retrieveLoanInterestTypeOptions();
        }
        final Collection<EnumOptionData> interestCalculationPeriodTypeOptions = this.loanDropdownReadPlatformService
                .retrieveLoanInterestRateCalculatedInPeriodOptions();
        final Collection<FundData> fundOptions = this.fundReadPlatformService.retrieveAllFunds();
        final Collection<TransactionProcessingStrategyData> repaymentStrategyOptions = this.loanDropdownReadPlatformService
                .retrieveTransactionProcessingStrategies();
        final Collection<CodeValueData> loanPurposeOptions = this.codeValueReadPlatformService.retrieveCodeValuesByCode("LoanPurpose");
        final Collection<CodeValueData> loanCollateralOptions = this.codeValueReadPlatformService
                .retrieveCodeValuesByCode("LoanCollateral");
        Collection<ChargeData> chargeOptions = null;
        if (loanProduct.getMultiDisburseLoan()) {
            chargeOptions = this.chargeReadPlatformService.retrieveLoanProductApplicableCharges(productId,
                    new ChargeTimeType[] { ChargeTimeType.OVERDUE_INSTALLMENT });
        } else {
            chargeOptions = this.chargeReadPlatformService.retrieveLoanProductApplicableCharges(productId,
                    new ChargeTimeType[] { ChargeTimeType.OVERDUE_INSTALLMENT, ChargeTimeType.TRANCHE_DISBURSEMENT });
        }

        Integer loanCycleCounter = null;
        if (loanProduct.isUseBorrowerCycle()) {
            if (clientId == null) {
                loanCycleCounter = retriveLoanCounter(groupId, AccountType.GROUP.getValue(), loanProduct.getId());
            } else {
                loanCycleCounter = retriveLoanCounter(clientId, loanProduct.getId());
            }
        }

        Collection<LoanAccountSummaryData> activeLoanOptions = null;
        if (loanProduct.isCanUseForTopup() && clientId != null) {
            activeLoanOptions = this.accountDetailsReadPlatformService.retrieveClientActiveLoanAccountSummary(clientId);
        } else if (loanProduct.isCanUseForTopup() && groupId != null) {
            activeLoanOptions = this.accountDetailsReadPlatformService.retrieveGroupActiveLoanAccountSummary(groupId);
        }

        List<LineOfCreditSummary> lineOfCreditSummaries = null;
        Boolean isLocEnabled = false;
        if (!loanProduct.getAdditionalProperties().isEmpty() && loanProduct.getAdditionalProperties().containsKey("isLocEnabled")
                && (Boolean) loanProduct.getAdditionalProperties().get("isLocEnabled")) {
            isLocEnabled = (Boolean) loanProduct.getAdditionalProperties().get("isLocEnabled");
            lineOfCreditSummaries = this.lineOfCreditReadPlatformService.retrieveSummary(loanProduct.getCurrency().getCode(), clientId);
        }

        ExtendedLoanAccountData loanAccountData = new ExtendedLoanAccountData();

        loanAccountData.withProductData(loanProduct, loanCycleCounter) //
                .setTermFrequencyTypeOptions(loanTermFrequencyTypeOptions) //
                .setRepaymentFrequencyTypeOptions(repaymentFrequencyTypeOptions) //
                .setRepaymentFrequencyNthDayTypeOptions(repaymentFrequencyNthDayTypeOptions) //
                .setRepaymentFrequencyDaysOfWeekTypeOptions(repaymentFrequencyDaysOfWeekTypeOptions) //
                .setTransactionProcessingStrategyOptions(repaymentStrategyOptions) //
                .setInterestRateFrequencyTypeOptions(interestRateFrequencyTypeOptions) //
                .setAmortizationTypeOptions(amortizationTypeOptions) //
                .setInterestTypeOptions(interestTypeOptions) //
                .setInterestCalculationPeriodTypeOptions(interestCalculationPeriodTypeOptions) //
                .setFundOptions(fundOptions) //
                .setChargeOptions(chargeOptions) //
                .setLoanPurposeOptions(loanPurposeOptions) //
                .setLoanCollateralOptions(loanCollateralOptions) //
                .setClientActiveLoanOptions(activeLoanOptions) //
                .setLoanScheduleTypeOptions(LoanScheduleType.getValuesAsEnumOptionDataList()) //
                .setLoanScheduleProcessingTypeOptions(LoanScheduleProcessingType.getValuesAsEnumOptionDataList());//

        if (lineOfCreditSummaries != null && !lineOfCreditSummaries.isEmpty()) {
            loanAccountData.getAdditionalProperties().put("lineOfCreditOptions", lineOfCreditSummaries);
            loanAccountData.getAdditionalProperties().put("isLocEnabled", isLocEnabled);
        }

        return loanAccountData;

    }

    @Override
    public Page<LoanAccountData> retrieveAll(final SearchParameters searchParameters) {

        final AppUser currentUser = this.context.authenticatedUser();
        final String hierarchy = currentUser.getOffice().getHierarchy();
        final String hierarchySearchString = hierarchy + "%";
        final LoanReadPlatformServiceImpl.LoanMapper loanMapper = new LoanReadPlatformServiceImpl.LoanMapper(sqlGenerator,
                delinquencyReadPlatformService);

        final StringBuilder sqlBuilder = new StringBuilder(200);
        sqlBuilder.append("select " + sqlGenerator.calcFoundRows() + " ");
        sqlBuilder.append(loanMapper.loanSchema());

        // TODO - for time being this will data scope list of loans returned to
        // only loans that have a client associated.
        // to support scenario where loan has group_id only OR client_id will
        // probably require a UNION query
        // but that at present is an edge case
        sqlBuilder.append(" join m_office o on (o.id = c.office_id or o.id = g.office_id) ");
        sqlBuilder.append(" left join m_office transferToOffice on transferToOffice.id = c.transfer_to_office_id ");

        if (searchParameters.getLocId() != null) {
            sqlBuilder.append(" left join m_loan_line_of_credit_params loc on loc.loan_id = l.id ");

        }
        sqlBuilder.append(" where ( o.hierarchy like ? or transferToOffice.hierarchy like ?)");

        int arrayPos = 2;
        List<Object> extraCriterias = new ArrayList<>();
        extraCriterias.add(hierarchySearchString);
        extraCriterias.add(hierarchySearchString);

        if (searchParameters != null) {

            if (StringUtils.isNotBlank(searchParameters.getStatus())) {
                sqlBuilder.append(" and l.loan_status_id = ?");
                extraCriterias.add(Integer.parseInt(searchParameters.getStatus()));
                arrayPos = arrayPos + 1;
            }

            if (StringUtils.isNotBlank(searchParameters.getExternalId())) {
                sqlBuilder.append(" and l.external_id = ?");
                extraCriterias.add(searchParameters.getExternalId());
                arrayPos = arrayPos + 1;
            }
            if (searchParameters.getOfficeId() != null) {
                sqlBuilder.append("and c.office_id =?");
                extraCriterias.add(searchParameters.getOfficeId());
                arrayPos = arrayPos + 1;
            }

            if (StringUtils.isNotBlank(searchParameters.getAccountNo())) {
                sqlBuilder.append(" and l.account_no = ?");
                extraCriterias.add(searchParameters.getAccountNo());
                arrayPos = arrayPos + 1;
            }

            if (searchParameters.getClientId() != null) {
                sqlBuilder.append(" and l.client_id = ?");
                extraCriterias.add(searchParameters.getClientId());
                arrayPos = arrayPos + 1;
            }

            if (searchParameters.getLocId() != null) {
                sqlBuilder.append(" and loc.line_of_credit_id = ?");
                extraCriterias.add(searchParameters.getLocId());
                arrayPos = arrayPos + 1;
            }

            if (searchParameters.hasOrderBy()) {
                sqlBuilder.append(" order by ").append(searchParameters.getOrderBy());
                this.columnValidator.validateSqlInjection(sqlBuilder.toString(), searchParameters.getOrderBy());

                if (searchParameters.hasSortOrder()) {
                    sqlBuilder.append(' ').append(searchParameters.getSortOrder());
                    this.columnValidator.validateSqlInjection(sqlBuilder.toString(), searchParameters.getSortOrder());
                }
            }

            if (searchParameters.hasLimit()) {
                sqlBuilder.append(" ");
                if (searchParameters.hasOffset()) {
                    sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
                } else {
                    sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit()));
                }
            }
        }
        final Object[] objectArray = extraCriterias.toArray();
        final Object[] finalObjectArray = Arrays.copyOf(objectArray, arrayPos);
        return this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), finalObjectArray, loanMapper);
    }

    @Override
    public LoanApprovalData retrieveApprovalTemplate(final Long loanId) {
        final Loan loan = loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);
        final ApplicationCurrency appCurrency = applicationCurrencyRepository.findOneWithNotFoundDetection(loan.getCurrency());

        Optional<LoanLineOfCreditParams> lineOfCreditParams = loanLineOfCreditParamsRepository.findByLoanId(loanId);

        if (lineOfCreditParams.isPresent() && lineOfCreditParams.get().getLineOfCredit().getProductType() == LocProductType.RECEIVABLE) {
            // If LOC is linked, then fetch the LOC details and override the net disbursal amount
            return new LoanApprovalData(loan.getApprovedPrincipal(), DateUtils.getBusinessLocalDate(), loan.getNetDisbursalAmount(),
                    appCurrency.toData());
        }

        return new LoanApprovalData(loan.getProposedPrincipal(), DateUtils.getBusinessLocalDate(), loan.getNetDisbursalAmount(),
                appCurrency.toData());
    }

}
