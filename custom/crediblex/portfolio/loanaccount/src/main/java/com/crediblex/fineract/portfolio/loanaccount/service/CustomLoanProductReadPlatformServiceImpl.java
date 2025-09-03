package com.crediblex.fineract.portfolio.loanaccount.service;

import com.crediblex.fineract.portfolio.loc.data.CustomLoanAccountData;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Service;

@Service
public class CustomLoanProductReadPlatformServiceImpl {
    
    private final JdbcTemplate jdbcTemplate;

    public CustomLoanProductReadPlatformServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieve loan product data with line of credit information.
     * This method provides a way to get loan product data that includes line of credit details.
     */
    public CustomLoanAccountData retrieveLoanProductWithLineOfCredit(final Long loanProductId) {
        final LocLoanProductMapper rm = new LocLoanProductMapper();
        final String sql = "select " + rm.loanProductSchema() + " where lp.id = ?";
        
        try {
            return this.jdbcTemplate.queryForObject(sql, rm, loanProductId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static final class LocLoanProductMapper implements RowMapper<CustomLoanAccountData> {

        public String loanProductSchema() {
            return "lp.id as id, lp.name as name, lp.short_name as shortName, lp.description as description, "
                    + "lp.fund_id as fundId, f.name as fundName, lp.loan_transaction_strategy_id as transactionStrategyId, "
                    + "lp.loan_transaction_strategy_name as transactionStrategyName, lp.start_date as startDate, lp.close_date as closeDate, "
                    + "lp.include_in_borrower_cycle as includeInBorrowerCycle, lp.use_borrower_cycle as useBorrowerCycle, "
                    + "lp.currency_code as currencyCode, curr.name as currencyName, curr.internationalized_name_code as currencyNameCode, "
                    + "curr.display_symbol as currencyDisplaySymbol, curr.decimal_places as currencyDigits, curr.currency_multiplesof as inMultiplesOf, "
                    + "lp.principal_amount as principal, lp.min_principal_amount as minPrincipal, lp.max_principal_amount as maxPrincipal, "
                    + "lp.tolerance_amount as tolerance, lp.number_of_repayments as numberOfRepayments, lp.min_number_of_repayments as minNumberOfRepayments, "
                    + "lp.max_number_of_repayments as maxNumberOfRepayments, lp.repayment_every as repaidEvery, lp.repayment_frequency_type as repaymentPeriodFrequency, "
                    + "lp.interest_rate_per_period as interestRatePerPeriod, lp.min_interest_rate_per_period as minInterestRatePerPeriod, "
                    + "lp.max_interest_rate_per_period as maxInterestRatePerPeriod, lp.annual_interest_rate as annualInterestRate, "
                    + "lp.interest_rate_frequency_type as interestRatePerPeriodFreq, lp.amortization_method as amortizationMethod, "
                    + "lp.interest_type as interestMethod, lp.interest_calculation_period_type as interestCalculationInPeriodMethod, "
                    + "lp.allow_partial_period_interest_calculation as allowPartialPeriodInterestCalcualtion, "
                    + "lp.accounting_type as accountingType, lp.allow_multiple_disbursals as multiDisburseLoan, "
                    + "lp.max_tranche_count as maxTrancheCount, lp.outstanding_loan_balance as outstandingLoanBalance, "
                    + "lp.disallow_expected_disbursements as disallowExpectedDisbursements, "
                    + "lp.allow_approved_disbursed_amounts_over_applied as allowApprovedDisbursedAmountsOverApplied, "
                    + "lp.over_applied_calculation_type as overAppliedCalculationType, lp.over_applied_number as overAppliedNumber, "
                    + "lp.grace_on_principal_payment as graceOnPrincipalPayment, lp.grace_on_interest_payment as graceOnInterestPayment, "
                    + "lp.grace_on_interest_charged as graceOnInterestCharged, lp.grace_on_arrears_ageing as graceOnArrearsAgeing, "
                    + "lp.overdue_days_for_npa as overdueDaysForNPA, lp.minimum_days_between_disbursal_and_first_repayment as minimumDaysBetweenDisbursalAndFirstRepayment, "
                    + "lp.external_id as externalId, lp.include_in_borrower_cycle as includeInBorrowerCycle, lp.use_borrower_cycle as useBorrowerCycle, "
                    + "lp.installment_amount_in_multiples_of as installmentAmountInMultiplesOf, lp.can_define_installment_amount as canDefineInstallmentAmount, "
                    + "lp.interest_recalculation_enabled as isInterestRecalculationEnabled, "
                    + "lpr.id as lprId, lpr.product_id as productId, lpr.compound_type_enum as compoundType, "
                    + "lpr.reschedule_strategy_enum as rescheduleStrategy, lpr.rest_frequency_enum as restFrequencyEnum, "
                    + "lpr.rest_frequency_interval as restFrequencyInterval, lpr.rest_frequency_nth_day_enum as restFrequencyNthDayEnum, "
                    + "lpr.rest_frequency_week_day_enum as restFrequencyWeekDayEnum, lpr.rest_frequency_on_day as restFrequencyOnDay, "
                    + "lpr.compounding_frequency_enum as compoundingFrequencyTypeEnum, lpr.compounding_frequency_interval as compoundingInterval, "
                    + "lpr.compounding_frequency_nth_day_enum as compoundingFrequencyNthDayEnum, "
                    + "lpr.compounding_frequency_week_day_enum as compoundingFrequencyWeekDayEnum, lpr.compounding_frequency_on_day as compoundingFrequencyOnDay, "
                    + "lpr.is_arrears_based_on_original_schedule as isArrearsBasedOnOriginalSchedule, "
                    + "lpr.is_compounding_to_be_posted_as_transaction as isCompoundingToBePostedAsTransaction, "
                    + "lpr.pre_close_interest_calculation_strategy_enum as preCloseInterestCalculationStrategy, "
                    + "lpr.allow_compounding_on_eod as allowCompoundingOnEod, "
                    + "lpr.is_interest_calculation_disallowed_on_past_due as disallowInterestCalculationOnPastDue, "
                    + "lpg.guarantee_fund_id as guaranteeFundId, lpg.guarantee_fund_name as guaranteeFundName, "
                    + "lpg.guarantee_fund_percentage as guaranteeFundPercentage, lpg.guarantee_fund_amount as guaranteeFundAmount, "
                    + "lca.allow_attribute_overrides as allowAttributeOverrides, "
                    + "lfr.is_floating_interest_rate_calculation_allowed as isFloatingInterestRateCalculationAllowed, "
                    + "lp.allow_variabe_installments as isVariableIntallmentsAllowed, " + "lvi.minimum_gap as minimumGap, "
                    + "lvi.maximum_gap as maximumGap, dbuc.id as delinquencyBucketId, dbuc.name as delinquencyBucketName, "
                    + "lp.can_use_for_topup as canUseForTopup, lp.is_equal_amortization as isEqualAmortization, lp.loan_schedule_type as loanScheduleType, lp.loan_schedule_processing_type as loanScheduleProcessingType, lp.supported_interest_refund_types as supportedInterestRefundTypes, "
                    + "lp.charge_off_behaviour as chargeOffBehaviour, " //
                    + "lp.enable_income_capitalization as enableIncomeCapitalization, " //
                    + "lp.capitalized_income_calculation_type as capitalizedIncomeCalculationType, " //
                    + "lp.capitalized_income_strategy as capitalizedIncomeStrategy, " //
                    + "lp.is_loc_enable as is_loc_enabled, " //
                    + "l.line_of_credit_id as line_of_credit_id, " //
                    + "loc.name as lineOfCreditName " //
                    + " from m_product_loan lp " + " left join m_fund f on f.id = lp.fund_id "
                    + " left join m_product_loan_recalculation_details lpr on lpr.product_id=lp.id "
                    + " left join m_product_loan_guarantee_details lpg on lpg.loan_product_id=lp.id "
                    + " left join m_product_loan_configurable_attributes lca on lca.loan_product_id = lp.id "
                    + " left join m_product_loan_floating_rates as lfr on lfr.loan_product_id = lp.id "
                    + " left join m_floating_rates as fr on lfr.floating_rates_id = fr.id "
                    + " left join m_product_loan_variable_installment_config as lvi on lvi.loan_product_id = lp.id "
                    + " left join m_delinquency_bucket as dbuc on dbuc.id = lp.delinquency_bucket_id "
                    + " left join m_line_of_credit loc on loc.id = l.line_of_credit_id "
                    + " join m_currency curr on curr.code = lp.currency_code";
        }

        @Override
        public CustomLoanAccountData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            
            // Get the line_of_credit_id value from the result set
            Long lineOfCreditId = rs.getLong("line_of_credit_id");
            if (rs.wasNull()) {
                lineOfCreditId = null;
            }
            
            final String lineOfCreditName = rs.getString("lineOfCreditName");
            
            // Create a minimal LoanAccountData with just the ID
            org.apache.fineract.portfolio.loanaccount.data.LoanAccountData baseData = new org.apache.fineract.portfolio.loanaccount.data.LoanAccountData();
            baseData.setId(id);
            
            return new CustomLoanAccountData(baseData, lineOfCreditId, lineOfCreditName, null);
        }
    }
}
