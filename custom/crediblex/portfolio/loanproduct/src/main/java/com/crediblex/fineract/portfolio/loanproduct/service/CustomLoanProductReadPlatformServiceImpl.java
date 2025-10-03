package com.crediblex.fineract.portfolio.loanproduct.service;

import com.crediblex.fineract.portfolio.loanproduct.data.ExtendedLoanProductData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
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
            final String sql = "SELECT lp.is_loc_enable as isLocEnabled, lp.is_factor_rate_product AS factorRateProductEnabled, lp.factor_rate AS factorRate, "
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
            extendedData.getAdditionalProperties().put("isLocEnabled", rs.getBoolean("isLocEnabled"));
            extendedData.setFactorRateProductEnabled(rs.getBoolean("factorRateProductEnabled"));
            extendedData.setFactorRate(rs.getBigDecimal("factorRate"));
            return extendedData;
        }
    }

}
