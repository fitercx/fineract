package com.crediblex.fineract.portfolio.loanproduct.service;

import com.crediblex.fineract.portfolio.loanproduct.data.ExtendedLoanProductData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityType;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.delinquency.data.DelinquencyBucketData;
import org.apache.fineract.portfolio.delinquency.service.DelinquencyReadPlatformService;
import org.apache.fineract.portfolio.loanproduct.data.AdvancedPaymentData;
import org.apache.fineract.portfolio.loanproduct.data.CreditAllocationData;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductBorrowerCycleVariationData;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductData;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.loanproduct.exception.LoanProductNotFoundException;
import org.apache.fineract.portfolio.loanproduct.service.LoanProductReadPlatformServiceImpl;
import org.apache.fineract.portfolio.rate.data.RateData;
import org.apache.fineract.portfolio.rate.service.RateReadService;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanProductReadPlatformServiceImpl extends LoanProductReadPlatformServiceImpl {

    public CustomLoanProductReadPlatformServiceImpl(PlatformSecurityContext context, JdbcTemplate jdbcTemplate,
            org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService chargeReadPlatformService,
            RateReadService rateReadService, DatabaseSpecificSQLGenerator sqlGenerator, FineractEntityAccessUtil fineractEntityAccessUtil,
            DelinquencyReadPlatformService delinquencyReadPlatformService, LoanProductRepository loanProductRepository) {
        super(context, jdbcTemplate, chargeReadPlatformService, rateReadService, sqlGenerator, fineractEntityAccessUtil,
                delinquencyReadPlatformService, loanProductRepository);
    }

    @Override
    public LoanProductData retrieveLoanProduct(final Long loanProductId) {

        try {
            final Collection<ChargeData> charges = this.chargeReadPlatformService.retrieveLoanProductCharges(loanProductId);
            final Collection<RateData> rates = this.rateReadService.retrieveProductLoanRates(loanProductId);
            final Collection<LoanProductBorrowerCycleVariationData> borrowerCycleVariationDatas = retrieveLoanProductBorrowerCycleVariations(
                    loanProductId);
            final Collection<AdvancedPaymentData> advancedPaymentData = retrieveAdvancedPaymentData(loanProductId);
            final Collection<CreditAllocationData> creditAllocationData = retrieveCreditAllocationData(loanProductId);
            final Collection<DelinquencyBucketData> delinquencyBucketOptions = this.delinquencyReadPlatformService
                    .retrieveAllDelinquencyBuckets();
            final CustomLoanProductMapper rm = new CustomLoanProductMapper(charges, borrowerCycleVariationDatas, rates,
                    delinquencyBucketOptions, advancedPaymentData, creditAllocationData);
            final String sql = "SELECT lp.enable_loc_payable as enableLocPayable,lp.enable_loc_receivable as enableLocReceivable, lp.is_factor_rate_product AS factorRateProductEnabled, lp.factor_rate AS factorRate, lp.penalty_grace_period AS penaltyGracePeriod, lp.enable_loc_receivable as enableLocReceivable, "
                    + rm.getSchema() + " where lp.id = ?";

            return this.jdbcTemplate.queryForObject(sql, rm, loanProductId); // NOSONAR

        } catch (final EmptyResultDataAccessException e) {
            throw new LoanProductNotFoundException(loanProductId, e);
        }

    }

    private static final class CustomLoanProductMapper implements RowMapper<ExtendedLoanProductData> {

        final LoanProductMapper rm;

        CustomLoanProductMapper(final Collection<ChargeData> charges,
                final Collection<LoanProductBorrowerCycleVariationData> borrowerCycleVariationDatas, final Collection<RateData> rates,
                final Collection<DelinquencyBucketData> delinquencyBucketOptions, Collection<AdvancedPaymentData> advancedPaymentData,
                Collection<CreditAllocationData> creditAllocationData) {

            this.rm = new LoanProductMapper(charges, borrowerCycleVariationDatas, rates, delinquencyBucketOptions, advancedPaymentData,
                    creditAllocationData);

        }

        public String getSchema() {
            return rm.loanProductSchema();
        }

        @Override
        public ExtendedLoanProductData mapRow(ResultSet rs, int rowNum) throws SQLException {
            LoanProductData data = rm.mapRow(rs, rowNum);
            ExtendedLoanProductData extendedData = ExtendedLoanProductData.fromLoanProductData(data);
            extendedData.setFactorRateProductEnabled(rs.getBoolean("factorRateProductEnabled"));
            extendedData.setFactorRate(rs.getBigDecimal("factorRate"));
            extendedData.setPenaltyGracePeriod(rs.getInt("penaltyGracePeriod"));
            extendedData.setEnableLineOfCreditReceivable(rs.getBoolean("enableLocReceivable"));
            extendedData.setEnableLineOfCreditPayable(rs.getBoolean("enableLocPayable"));
            return extendedData;
        }
    }

    @Override
    public Collection<LoanProductData> retrieveAllLoanProductsForLookup(final boolean activeOnly) {
        this.context.authenticatedUser();

        final CustomLoanProductLookupMapper rm = new CustomLoanProductLookupMapper(sqlGenerator);

        String sql = "select ";
        if (activeOnly) {
            sql += rm.activeOnlySchema();
        } else {
            sql += rm.schema();
        }

        // Check if branch specific products are enabled. If yes, fetch only
        // products mapped to current user's office
        String inClause = fineractEntityAccessUtil
                .getSQLWhereClauseForProductIDsForUserOffice_ifGlobalConfigEnabled(FineractEntityType.LOAN_PRODUCT);
        if (inClause != null && !inClause.trim().isEmpty()) {
            if (activeOnly) {
                sql += " and id in ( " + inClause + " )";
            } else {
                sql += " where id in ( " + inClause + " ) ";
            }
        }

        return this.jdbcTemplate.query(sql, rm); // NOSONAR
    }

    private static final class CustomLoanProductLookupMapper implements RowMapper<LoanProductData> {

        private final DatabaseSpecificSQLGenerator sqlGenerator;

        CustomLoanProductLookupMapper(DatabaseSpecificSQLGenerator sqlGenerator) {
            this.sqlGenerator = sqlGenerator;
        }

        public String schema() {
            return "lp.id as id, lp.name as name, lp.allow_multiple_disbursals as multiDisburseLoan, enable_loc_receivable as enableLocReceivable, enable_loc_payable as enableLocPayable from m_product_loan lp";
        }

        public String activeOnlySchema() {
            return schema() + " where (close_date is null or close_date >= " + sqlGenerator.currentBusinessDate() + ")";
        }

        public String productMixSchema() {
            return "lp.id as id, lp.name as name, lp.allow_multiple_disbursals as multiDisburseLoan FROM m_product_loan lp left join m_product_mix pm on pm.product_id=lp.id where lp.id not IN("
                    + "select lp.id from m_product_loan lp inner join m_product_mix pm on pm.product_id=lp.id)";
        }

        public String restrictedProductsSchema() {
            return "pm.restricted_product_id as id, rp.name as name, rp.allow_multiple_disbursals as multiDisburseLoan from m_product_mix pm join m_product_loan rp on rp.id = pm.restricted_product_id ";
        }

        public String derivedRestrictedProductsSchema() {
            return "pm.product_id as id, lp.name as name, lp.allow_multiple_disbursals as multiDisburseLoan from m_product_mix pm join m_product_loan lp on lp.id=pm.product_id";
        }

        @Override
        public LoanProductData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final Long id = rs.getLong("id");
            final String name = rs.getString("name");
            final Boolean multiDisburseLoan = rs.getBoolean("multiDisburseLoan");
            final Boolean enableLocReceivable = rs.getBoolean("enableLocReceivable");
            final Boolean enableLocPayable = rs.getBoolean("enableLocPayable");

            return ExtendedLoanProductData.lookup(id, name, multiDisburseLoan, enableLocPayable, enableLocReceivable);
        }
    }

}
